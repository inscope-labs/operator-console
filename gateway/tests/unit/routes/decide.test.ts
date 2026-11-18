const _idemSeq = { n: 0 };
function idemKey(): string {
  _idemSeq.n += 1;
  return _idemSeq.n.toString(16).padStart(32, '0');
}

import { describe, it, expect, beforeEach } from 'vitest';
import { clearRoutes, dispatch, listRoutes } from '../../../src/routes/router';
import { registerDecisionRoutes } from '../../../src/routes/decisions';
import { clearPublishGuards, registerPublishGuard } from '../../../src/guards/index';
import { clearAuditRecords, listAuditRecords } from '../../../src/audit/emit';
import { resetSharedRateCoordinator } from '../../../src/adapter/rate-coordinator';
import { resetMemoryHalt } from '../../../src/halt/state';
import { encodeCapability } from '../../../src/capability/codec';
import {
  CAPABILITY_VERSION,
  type OperatorCapability,
} from '../../../src/capability/types';
import type { Env } from '../../../src/runtime';

const NOW = Math.floor(Date.now() / 1000);
const HEAD = 'a'.repeat(40);
const BRANCH = 'task-7-work';
const SECRET = new TextEncoder().encode('lab-hmac-secret-key-32b-minimum!!');

function b64url(bytes: Uint8Array): string {
  let s = '';
  for (let i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** The nonce of the last capability minted by signedCap — used to assert
 *  that the nonce never reaches the persisted record. */
let lastNonce = '';

async function signedCap(
  over: Partial<OperatorCapability> = {}
): Promise<string> {
  const cap: OperatorCapability = {
    version: CAPABILITY_VERSION,
    kid: 'lab1',
    exp: NOW + 120,
    order: 'o1',
    plan: 'atlas',
    task: '7',
    op: 'decide',
    branch: BRANCH,
    head: HEAD,
    nonce: crypto.randomUUID().replace(/-/g, '').slice(0, 16),
    ...over,
  };
  lastNonce = cap.nonce;
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

function mockConsumer() {
  const files = new Map<string, Uint8Array>();
  return {
    getRef: async (branch: string) => ({
      ref: `refs/heads/${branch}`,
      sha: HEAD,
    }),
    compare: async () => ({
      status: 'ahead',
      aheadBy: 1,
      behindBy: 0,
      files: ['src/app.ts'],
      hasMergeCommits: false,
      isAncestryOfBase: true,
    }),
    putFile: async (o: {
      path: string;
      contentBytes: Uint8Array;
      message: string;
      branch: string;
    }) => {
      files.set(o.path, o.contentBytes);
      return { contentSha: 'd'.repeat(40), path: o.path };
    },
    getContents: async () => ({
      sha: '',
      content: '',
      type: 'file' as const,
      path: '',
    }),
  };
}

function baseEnv(over: Record<string, unknown> = {}): Env {
  const consumer = mockConsumer();
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
    NONCE_STORE: {
      idFromName: () => ({ toString: () => 'n' }),
      get: () => ({
        fetch: async () =>
          new Response(JSON.stringify({ ok: true }), { status: 200 }),
      }),
    },
    ENROLMENT_REGISTRY: {
      idFromName: () => ({ toString: () => 'e' }),
      get: () => ({
        fetch: async () => new Response('{}', { status: 404 }),
      }),
    },
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

beforeEach(async () => {
  clearRoutes();
  clearPublishGuards();
  clearAuditRecords();
  resetSharedRateCoordinator();
  resetMemoryHalt();
  const mods = await Promise.all([
    import('../../../src/guards/g1-private'),
    import('../../../src/guards/g3-branch-shape'),
    import('../../../src/guards/g4-branch-exists'),
    import('../../../src/guards/g5-ancestry'),
    import('../../../src/guards/g6-non-empty-delta'),
    import('../../../src/guards/g7-no-merge-commits'),
    import('../../../src/guards/g8-denied-paths'),
    import('../../../src/guards/g11-secret-patterns'),
    import('../../../src/guards/g13-exact-sha'),
    import('../../../src/guards/g14-halt'),
    import('../../../src/guards/g15-submission-immutable'),
  ]);
  const guards = [
    mods[0].g1Private,
    mods[1].g3BranchShape,
    mods[2].g4BranchExists,
    mods[3].g5Ancestry,
    mods[4].g6NonEmptyDelta,
    mods[5].g7NoMergeCommits,
    mods[6].g8DeniedPaths,
    mods[7].g11SecretPatterns,
    mods[8].g13ExactSha,
    mods[9].g14Halt,
    mods[10].g15SubmissionImmutable,
  ];
  [
    'G1',
    'G3',
    'G4',
    'G5',
    'G6',
    'G7',
    'G8',
    'G11',
    'G13',
    'G14',
    'G15',
  ].forEach((n, i) => registerPublishGuard(n, guards[i]));
  registerDecisionRoutes();
});

describe('POST /plans/:id/decide', () => {
  it('registers plan and legacy decision paths', () => {
    const patterns = listRoutes().map((r) => r.pattern);
    expect(patterns.some((p) => p.includes('plans') && p.includes('decide'))).toBe(
      true
    );
    expect(
      patterns.some((p) => p.includes('decisions') && p.includes('decide'))
    ).toBe(true);
  });

  // ADR 0014 D2 / contract 3.18: the request carries only plan, task, branch, decision, notes,
  // idempotencyKey and capability. Attribution, scope and evidence are NOT request fields.
  async function postDecide(body: Record<string, unknown>, env: Env = baseEnv()): Promise<Response> {
    return dispatch(
      new Request('https://gw.test/plans/atlas/decide', {
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

  it('accepts exactly the contract fields (no attribution, scope or evidence)', async () => {
    const cap = await signedCap();
    const res = await postDecide({
      plan: 'atlas',
      task: '7',
      branch: BRANCH,
      decision: 'APPROVED',
      notes: 'Reviewed test coverage and verification log',
      idempotencyKey: idemKey(),
      capability: cap,
    });
    expect(res.status).toBe(200);
    const body = (await res.json()) as Record<string, unknown>;
    expect(body.accepted).toBe(true);
    expect(body.operation).toBe('DECIDE');
    expect(body.plan).toBe('atlas');
    expect(body.decision).toBe('APPROVED');
    expect(String(body.auditRef)).toMatch(/^audit-/);
  });

  it('notes is optional', async () => {
    const cap = await signedCap();
    const res = await postDecide({
      plan: 'atlas',
      task: '7',
      branch: BRANCH,
      decision: 'DEFERRED',
      idempotencyKey: idemKey(),
      capability: cap,
    });
    expect(res.status).toBe(200);
  });

  it('refuses a missing decision', async () => {
    const cap = await signedCap();
    const res = await postDecide({
      plan: 'atlas',
      task: '7',
      branch: BRANCH,
      idempotencyKey: idemKey(),
      capability: cap,
    });
    expect(res.status).toBe(400);
    const body = (await res.json()) as { error: string; message: string };
    expect(body.error).toBe('BAD_ENCODING');
    expect(body.message).toMatch(/decision required/);
  });

  it('refuses a decision value outside APPROVED|REJECTED|DEFERRED', async () => {
    const cap = await signedCap();
    const res = await postDecide({
      plan: 'atlas',
      task: '7',
      branch: BRANCH,
      decision: 'MAYBE',
      idempotencyKey: idemKey(),
      capability: cap,
    });
    expect(res.status).toBe(400);
    const body = (await res.json()) as { error: string; message: string };
    expect(body.error).toBe('BAD_ENCODING');
    expect(body.message).toMatch(/APPROVED\|REJECTED\|DEFERRED/);
  });

  it('derives attribution from the capability kid, never from the request body', async () => {
    const written: string[] = [];
    const spy = {
      ...mockConsumer(),
      putFile: async (o: { path: string; contentBytes: Uint8Array }) => {
        written.push(new TextDecoder().decode(o.contentBytes));
        return { contentSha: 'd'.repeat(40), path: o.path };
      },
    };
    const env = baseEnv({ __adapter: { consumer: () => spy, product: () => spy } });
    const cap = await signedCap();
    const res = await postDecide(
      {
        plan: 'atlas',
        task: '7',
        branch: BRANCH,
        decision: 'APPROVED',
        notes: 'coverage sufficient',
        attribution: 'mallory@example',
        evidence: 'forged',
        idempotencyKey: idemKey(),
        capability: cap,
      },
      env
    );
    expect(res.status).toBe(200);
    expect(written).toHaveLength(1);
    const record = JSON.parse(written[0]) as Record<string, unknown>;
    expect(record.attribution).toBe('lab1');
    expect(record.rationale).toBe('coverage sufficient');
    expect(record.scope).toBe(`atlas/7/${BRANCH}`);
    // The signed capability, its signature and nonce never reach the persisted record.
    expect(written[0]).not.toContain('ctxop-v1');
    expect(written[0]).not.toContain(lastNonce);
  });

  it('happy path returns DECIDE + auditRef', async () => {
    const cap = await signedCap();
    const res = await dispatch(
      new Request('https://gw.test/plans/atlas/decide', {
        method: 'POST',
        headers: {
          authorization: 'Bearer tok',
          'content-type': 'application/json',
        },
        body: JSON.stringify({
          idempotencyKey: idemKey(),
          capability: cap,
          plan: 'atlas',
          task: '7',
          branch: BRANCH,
          head: HEAD,
          decision: 'APPROVED',
          attribution: 'operator@example',
          rationale: 'coverage sufficient',
          scope: 'task-7',
          evidence: 'vitest green + manual review',
        }),
      }),
      baseEnv(),
      ctx
    );
    const body = (await res.json()) as Record<string, unknown>;
    expect(res.status).toBe(200);
    expect(body.accepted).toBe(true);
    expect(body.operation).toBe('DECIDE');
    expect(body.decision).toBe('APPROVED');
    expect(body.auditRef).toMatch(/^audit-/);
    expect(listAuditRecords().some((r) => r.op === 'decide')).toBe(true);
  });

  it('is not 501', async () => {
    const res = await dispatch(
      new Request('https://gw.test/plans/atlas/decide', {
        method: 'POST',
        headers: {
          authorization: 'Bearer tok',
          'content-type': 'application/json',
        },
        body: '{}',
      }),
      baseEnv(),
      ctx
    );
    expect(res.status).not.toBe(501);
  });
});
