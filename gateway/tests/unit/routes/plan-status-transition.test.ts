/**
 * POST /plans/:id/status — contract §3.17 (revision 1.3).
 *
 * The console sends SET_IN_PROGRESS here before it publishes. The operation
 * changes one task's status in the gateway's plan record and does not commit
 * to the consumer repository.
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { clearRoutes, dispatch, listRoutes } from '../../../src/routes/router';
import { registerPlanRoutes } from '../../../src/routes/plans';
import { registerOperationRoutes } from '../../../src/routes/operations';
import { clearAuditRecords } from '../../../src/audit/emit';
import { resetMemoryHalt } from '../../../src/halt/state';
import {
  resetPlanMemory,
  getPlanRecord,
  putPlanRecord,
} from '../../../src/plan/client';
import { resetOperationMemory } from '../../../src/idempotency/store-client';
import { encodeCapability } from '../../../src/capability/codec';
import {
  CAPABILITY_VERSION,
  type OperatorCapability,
} from '../../../src/capability/types';
import type { Env } from '../../../src/runtime';

const NOW = Math.floor(Date.now() / 1000);
const HEAD = 'a'.repeat(40);
const OTHER_HEAD = 'b'.repeat(40);
const BRANCH = 'task-7-work';
const PLAN = 'atlas';
const TASK = '7';
const SECRET = new TextEncoder().encode('lab-hmac-secret-key-32b-minimum!!');

const _idemSeq = { n: 0 };
function idemKey(): string {
  _idemSeq.n += 1;
  return _idemSeq.n.toString(16).padStart(32, '0');
}

function b64url(bytes: Uint8Array): string {
  let s = '';
  for (let i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

async function signedCap(
  over: Partial<OperatorCapability> = {}
): Promise<string> {
  const cap: OperatorCapability = {
    version: CAPABILITY_VERSION,
    kid: 'lab1',
    exp: NOW + 120,
    order: 'o1',
    plan: PLAN,
    task: TASK,
    op: 'set_in_progress',
    branch: BRANCH,
    head: HEAD,
    nonce: crypto.randomUUID().replace(/-/g, '').slice(0, 16),
    ...over,
  };
  const message = encodeCapability(cap);
  const key = await crypto.subtle.importKey(
    'raw',
    SECRET,
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign']
  );
  const sig = await crypto.subtle.sign(
    'HMAC',
    key,
    new TextEncoder().encode(message)
  );
  return message + '\n' + b64url(new Uint8Array(sig));
}

interface MockConsumer {
  calls: { putFile: number; getRef: number };
  getRef: (branch: string) => Promise<{ ref: string; sha: string }>;
  compare: () => Promise<unknown>;
  putFile: () => Promise<unknown>;
  getContents: () => Promise<unknown>;
}

function mockConsumer(
  over: {
    sha?: string;
    missing?: boolean;
    errorMessage?: string;
    onGetRef?: () => void;
  } = {}
): MockConsumer {
  const calls = { putFile: 0, getRef: 0 };
  return {
    calls,
    getRef: async (branch: string) => {
      calls.getRef += 1;
      over.onGetRef?.();
      if (over.errorMessage) throw new Error(over.errorMessage);
      if (over.missing) throw new Error('404 Not Found');
      return { ref: `refs/heads/${branch}`, sha: over.sha ?? HEAD };
    },
    compare: async () => ({
      status: 'ahead',
      aheadBy: 1,
      behindBy: 0,
      files: ['src/app.ts'],
      hasMergeCommits: false,
      isAncestryOfBase: true,
    }),
    putFile: async () => {
      calls.putFile += 1;
      return { contentSha: 'd'.repeat(40), path: 'x' };
    },
    getContents: async () => ({
      sha: '',
      content: '',
      type: 'file' as const,
      path: '',
    }),
  };
}

/** A nonce store that really remembers: the second use of a nonce is a replay. */
function nonceStore(): DurableObjectNamespace {
  const seen = new Set<string>();
  return {
    idFromName: () => ({ toString: () => 'n' }),
    get: () => ({
      fetch: async (input: RequestInfo | URL, init?: RequestInit) => {
        const req =
          typeof input === 'string' || input instanceof URL
            ? new Request(input, init)
            : input;
        const body = (await req.json()) as { nonce: string };
        if (seen.has(body.nonce)) {
          return new Response(
            JSON.stringify({ message: 'nonce already used' }),
            { status: 409 }
          );
        }
        seen.add(body.nonce);
        return new Response(JSON.stringify({ ok: true }), { status: 200 });
      },
    }),
  } as unknown as DurableObjectNamespace;
}

function baseEnv(
  over: Record<string, unknown> = {},
  consumer: MockConsumer = mockConsumer()
): Env {
  return {
    ENVIRONMENT: 'test',
    OPERATOR_TOKEN: 'tok',
    SIGNING_KEYS: JSON.stringify([
      {
        kid: 'lab1',
        algorithm: 'HMAC',
        secret: 'lab-hmac-secret-key-32b-minimum!!',
        encoding: 'utf8',
      },
    ]),
    REPOS: '{}',
    GITHUB_APP_ID: '1',
    GITHUB_APP_PRIVATE_KEY: 'k',
    GITHUB_APP_INSTALLATION_ID: '1',
    NONCE_STORE: nonceStore(),
    __adapter: {
      consumer: () => consumer,
      product: () => consumer,
    },
    __haltActive: false,
    ...over,
  } as unknown as Env;
}

const ctx = {
  waitUntil: () => undefined,
  passThroughOnException: () => undefined,
} as unknown as ExecutionContext;

async function seedPlan(
  env: Env,
  over: Partial<{
    id: string;
    tasks: Array<{
      id: string;
      title: string;
      branch?: string;
      status: string;
      headSha?: string;
    }>;
  }> = {}
): Promise<void> {
  await putPlanRecord(
    env,
    PLAN,
    {
      id: PLAN,
      title: 'Atlas',
      status: 'ready',
      taskCount: 1,
      completedTaskCount: 0,
      updatedAt: NOW,
      tasks: [
        {
          id: TASK,
          title: 'Task 7',
          branch: BRANCH,
          status: 'ready',
          headSha: HEAD,
        },
      ],
      ...over,
    } as never
  );
}

async function postStatus(
  body: Record<string, unknown>,
  env: Env,
  planId: string = PLAN
): Promise<Response> {
  return dispatch(
    new Request(`https://gw.test/plans/${planId}/status`, {
      method: 'POST',
      headers: {
        authorization: 'Bearer tok',
        'content-type': 'application/json',
      },
      body: JSON.stringify(body),
    }),
    env,
    ctx
  );
}

async function getOperation(env: Env, id: string): Promise<Response> {
  return dispatch(
    new Request(`https://gw.test/operations/${id}`, {
      method: 'GET',
      headers: { authorization: 'Bearer tok' },
    }),
    env,
    ctx
  );
}

/** The request body the console sends, with a fresh capability. */
async function requestBody(
  over: Record<string, unknown> = {},
  capOver: Partial<OperatorCapability> = {}
): Promise<Record<string, unknown>> {
  return {
    plan: PLAN,
    task: TASK,
    branch: BRANCH,
    expectedHead: HEAD,
    targetStatus: 'in_progress',
    idempotencyKey: idemKey(),
    capability: await signedCap(capOver),
    ...over,
  };
}

beforeEach(async () => {
  clearRoutes();
  clearAuditRecords();
  resetMemoryHalt();
  resetPlanMemory();
  resetOperationMemory();
  registerPlanRoutes();
  registerOperationRoutes();
});

describe('POST /plans/:id/status', () => {
  it('registers the guarded status transition route', () => {
    const statusRoutes = listRoutes().filter((r) =>
      r.pattern.includes('status')
    );
    expect(statusRoutes.length).toBe(2);
    expect(statusRoutes.every((r) => r.requiresOperatorToken)).toBe(true);

    // The transition is capability-gated; the read beside it is not.
    const transition = statusRoutes.find((r) => r.method === 'POST');
    expect(transition).toBeDefined();
    expect(transition!.requiresCapability).toBe(true);
    const read = statusRoutes.find((r) => r.method === 'GET');
    expect(read).toBeDefined();
    expect(read!.requiresCapability).toBe(false);
  });

  it('accepts the transition and answers exactly the contract response keys', async () => {
    const env = baseEnv();
    await seedPlan(env);
    const consumer = mockConsumer();
    const env2 = baseEnv({}, consumer);
    await seedPlan(env2);

    const res = await postStatus(await requestBody(), env2);
    expect(res.status).toBe(200);
    const body = (await res.json()) as Record<string, unknown>;
    expect(Object.keys(body).sort()).toEqual([
      'accepted',
      'auditRef',
      'headSha',
      'newStatus',
      'operation',
      'plan',
      'task',
    ]);
    expect(body.accepted).toBe(true);
    expect(body.operation).toBe('SET_IN_PROGRESS');
    expect(body.plan).toBe(PLAN);
    expect(body.task).toBe(TASK);
    expect(body.newStatus).toBe('in_progress');
    expect(body.headSha).toBe(HEAD);
    expect(String(body.auditRef)).toMatch(/^audit-/);

    // The task's status changed in the gateway's plan record …
    const rec = await getPlanRecord(env2, PLAN);
    expect(rec.body?.tasks?.[0].status).toBe('in_progress');

    // … and nothing was committed to the consumer repository.
    expect(consumer.calls.putFile).toBe(0);
  });

  it('refuses every reserved targetStatus and leaves the record unchanged', async () => {
    for (const target of [
      'draft',
      'ready',
      'completed',
      'halted',
      'archived',
    ]) {
      const env = baseEnv();
      await seedPlan(env);
      const res = await postStatus(
        await requestBody({ targetStatus: target }),
        env
      );
      expect(res.status).toBe(400);
      const body = (await res.json()) as { error: string };
      expect(body.error).toBe('BAD_ENCODING');
      const rec = await getPlanRecord(env, PLAN);
      expect(rec.body?.tasks?.[0].status).toBe('ready');
    }
  });

  it('refuses an unknown request key', async () => {
    const env = baseEnv();
    await seedPlan(env);
    const res = await postStatus(
      await requestBody({ completed: 'not-a-field' }),
      env
    );
    expect(res.status).toBe(400);
    const body = (await res.json()) as { error: string };
    expect(body.error).toBe('BAD_ENCODING');
    const rec = await getPlanRecord(env, PLAN);
    expect(rec.body?.tasks?.[0].status).toBe('ready');
  });

  it('refuses the reserved ops set and set_done, and publish as UNKNOWN_OP', async () => {
    for (const op of ['set', 'set_done'] as const) {
      const env = baseEnv();
      await seedPlan(env);
      const res = await postStatus(
        await requestBody({}, { op }),
        env
      );
      expect(res.status).toBe(400);
      const body = (await res.json()) as { error: string };
      expect(body.error).toBe('BAD_ENCODING');
      const rec = await getPlanRecord(env, PLAN);
      expect(rec.body?.tasks?.[0].status).toBe('ready');
    }

    const env = baseEnv();
    await seedPlan(env);
    const res = await postStatus(
      await requestBody({}, { op: 'publish' }),
      env
    );
    expect(res.status).toBe(400);
    const body = (await res.json()) as { error: string };
    expect(body.error).toBe('UNKNOWN_OP');
    const rec = await getPlanRecord(env, PLAN);
    expect(rec.body?.tasks?.[0].status).toBe('ready');
  });

  it('refuses a capability branch that differs from the request branch', async () => {
    const env = baseEnv();
    await seedPlan(env);
    const res = await postStatus(
      await requestBody({}, { branch: 'task-7-other' }),
      env
    );
    expect(res.status).toBe(403);
    const body = (await res.json()) as { error: string };
    expect(body.error).toBe('BRANCH_MISMATCH');
    const rec = await getPlanRecord(env, PLAN);
    expect(rec.body?.tasks?.[0].status).toBe('ready');
  });

  it('refuses a task whose recorded branch differs from the capability branch', async () => {
    const env = baseEnv();
    await seedPlan(env, {
      tasks: [
        {
          id: TASK,
          title: 'Task 7',
          branch: 'task-7-other',
          status: 'ready',
          headSha: HEAD,
        },
      ],
    });
    const res = await postStatus(await requestBody(), env);
    expect(res.status).toBe(403);
    const body = (await res.json()) as { error: string };
    expect(body.error).toBe('BRANCH_MISMATCH');
    const rec = await getPlanRecord(env, PLAN);
    expect(rec.body?.tasks?.[0].status).toBe('ready');
  });

  it('refuses a branch that does not exist (BRANCH_MISSING)', async () => {
    const consumer = mockConsumer({ missing: true });
    const env = baseEnv({}, consumer);
    await seedPlan(env);
    const res = await postStatus(await requestBody(), env);
    expect(res.status).toBe(404);
    const body = (await res.json()) as { error: string };
    expect(body.error).toBe('BRANCH_MISSING');
    const rec = await getPlanRecord(env, PLAN);
    expect(rec.body?.tasks?.[0].status).toBe('ready');
  });

  it('reports an upstream failure while reading the head as NETWORK', async () => {
    const consumer = mockConsumer({ errorMessage: 'upstream 500' });
    const env = baseEnv({}, consumer);
    await seedPlan(env);
    const res = await postStatus(await requestBody(), env);
    expect(res.status).toBe(502);
    const body = (await res.json()) as { error: string };
    expect(body.error).toBe('NETWORK');
    const rec = await getPlanRecord(env, PLAN);
    expect(rec.body?.tasks?.[0].status).toBe('ready');
  });

  it('refuses a moved head — each of the three heads differing in turn', async () => {
    // (a) the capability head differs from the live head
    {
      const env = baseEnv();
      await seedPlan(env);
      const res = await postStatus(
        await requestBody({}, { head: OTHER_HEAD }),
        env
      );
      expect(res.status).toBe(409);
      const body = (await res.json()) as { error: string };
      expect(body.error).toBe('HEAD_MOVED');
      const rec = await getPlanRecord(env, PLAN);
      expect(rec.body?.tasks?.[0].status).toBe('ready');
    }
    // (b) expectedHead differs from the live head
    {
      const env = baseEnv();
      await seedPlan(env);
      const res = await postStatus(
        await requestBody({ expectedHead: OTHER_HEAD }),
        env
      );
      expect(res.status).toBe(409);
      const body = (await res.json()) as { error: string };
      expect(body.error).toBe('HEAD_MOVED');
      const rec = await getPlanRecord(env, PLAN);
      expect(rec.body?.tasks?.[0].status).toBe('ready');
    }
    // (c) the capability head and expectedHead differ from each other
    {
      const env = baseEnv();
      await seedPlan(env);
      const res = await postStatus(
        await requestBody(
          { expectedHead: 'c'.repeat(40) },
          { head: OTHER_HEAD }
        ),
        env
      );
      expect(res.status).toBe(409);
      const body = (await res.json()) as { error: string };
      expect(body.error).toBe('HEAD_MOVED');
      const rec = await getPlanRecord(env, PLAN);
      expect(rec.body?.tasks?.[0].status).toBe('ready');
    }
  });

  it('refuses while a halt is active (HALT_ACTIVE)', async () => {
    const env = baseEnv({ __haltActive: true });
    await seedPlan(env);
    const res = await postStatus(await requestBody(), env);
    expect(res.status).toBe(409);
    const body = (await res.json()) as { error: string };
    expect(body.error).toBe('HALT_ACTIVE');
    const rec = await getPlanRecord(env, PLAN);
    expect(rec.body?.tasks?.[0].status).toBe('ready');
  });

  it('refuses an unknown plan, naming it', async () => {
    const env = baseEnv();
    const res = await postStatus(
      await requestBody({ plan: 'ghost' }, { plan: 'ghost' }),
      env,
      'ghost'
    );
    expect(res.status).toBe(400);
    const body = (await res.json()) as { error: string; message: string };
    expect(body.error).toBe('BAD_ENCODING');
    expect(body.message).toContain('ghost');
  });

  it('refuses an unknown task, naming it', async () => {
    const env = baseEnv();
    await seedPlan(env);
    const res = await postStatus(
      await requestBody({ task: 'nope' }, { task: 'nope' }),
      env
    );
    expect(res.status).toBe(400);
    const body = (await res.json()) as { error: string; message: string };
    expect(body.error).toBe('BAD_ENCODING');
    expect(body.message).toContain('nope');
  });

  it('refuses a stale etag with 409 RECONCILE_REQUIRED and changes nothing', async () => {
    // The record changes between the route's read and its write.
    let env: Env;
    const consumer = mockConsumer({
      onGetRef: () => {
        void putPlanRecord(
          env,
          PLAN,
          { title: 'Atlas (moved underneath)' } as never
        );
      },
    });
    env = baseEnv({}, consumer);
    await seedPlan(env);

    const res = await postStatus(await requestBody(), env);
    expect(res.status).toBe(409);
    const body = (await res.json()) as { error: string };
    expect(body.error).toBe('RECONCILE_REQUIRED');
    const rec = await getPlanRecord(env, PLAN);
    expect(rec.body?.tasks?.[0].status).toBe('ready');
  });

  it('replays the same result for the same idempotencyKey', async () => {
    const env = baseEnv();
    await seedPlan(env);
    const key = idemKey();
    const body = await requestBody({ idempotencyKey: key });

    const first = await postStatus(body, env);
    expect(first.status).toBe(200);
    const firstBody = await first.json();

    const second = await postStatus(body, env);
    expect(second.status).toBe(200);
    expect(second.headers.get('x-idempotency-replay')).toBe('1');
    const secondBody = await second.json();
    expect(secondBody).toEqual(firstBody);
  });

  it('records the outcome so GET /operations/:id shows the final state', async () => {
    const env = baseEnv();
    await seedPlan(env);
    const key = idemKey();
    const res = await postStatus(
      await requestBody({ idempotencyKey: key }),
      env
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as Record<string, unknown>;

    const lookup = await getOperation(env, key);
    expect(lookup.status).toBe(200);
    const rec = (await lookup.json()) as Record<string, unknown>;
    expect(rec.id).toBe(key);
    expect(rec.operation).toBe('SET_IN_PROGRESS');
    expect(rec.status).toBe('completed');
    expect(rec.auditRef).toBe(body.auditRef);
    expect(rec.headSha).toBe(HEAD);
    expect(rec.branch).toBe(BRANCH);
  });

  it('records a refusal so GET /operations/:id shows it', async () => {
    const env = baseEnv();
    await seedPlan(env);
    const key = idemKey();
    const res = await postStatus(
      await requestBody({ idempotencyKey: key, targetStatus: 'completed' }),
      env
    );
    expect(res.status).toBe(400);

    const lookup = await getOperation(env, key);
    expect(lookup.status).toBe(200);
    const rec = (await lookup.json()) as Record<string, unknown>;
    expect(rec.status).toBe('refused');
    expect(rec.code).toBe('BAD_ENCODING');
  });

  it('refuses a replayed nonce under a new idempotencyKey (NONCE_REPLAYED)', async () => {
    const env = baseEnv();
    await seedPlan(env);
    const capability = await signedCap();

    const first = await postStatus(
      await requestBody({ capability }),
      env
    );
    expect(first.status).toBe(200);

    const second = await postStatus(
      await requestBody({ capability }),
      env
    );
    expect(second.status).toBe(409);
    const body = (await second.json()) as { error: string };
    expect(body.error).toBe('NONCE_REPLAYED');
  });
});
