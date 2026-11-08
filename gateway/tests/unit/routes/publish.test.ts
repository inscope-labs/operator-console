import { describe, it, expect, beforeEach } from 'vitest';
import { clearRoutes, dispatch, listRoutes } from '../../../src/routes/router';
import { registerTaskRoutes } from '../../../src/routes/tasks';
import { clearPublishGuards } from '../../../src/guards/index';
import { clearAuditRecords, listAuditRecords } from '../../../src/audit/emit';
import { resetSharedRateCoordinator } from '../../../src/adapter/rate-coordinator';
import { encodeCapability } from '../../../src/capability/codec';
import {
  CAPABILITY_VERSION,
  type OperatorCapability,
} from '../../../src/capability/types';
import type { Env } from '../../../src/runtime';

const NOW = Math.floor(Date.now() / 1000);
const HEAD = 'a'.repeat(40);
const BRANCH = 'task-7-work';

// Re-export path: verify exports bytesToBase64Url
function bytesToBase64UrlLocal(bytes: Uint8Array): string {
  let s = '';
  for (let i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

const SECRET = new TextEncoder().encode('lab-hmac-secret-key-32b-minimum!!');

function baseCap(over: Partial<OperatorCapability> = {}): OperatorCapability {
  return {
    version: CAPABILITY_VERSION,
    kid: 'lab1',
    exp: NOW + 120,
    order: 'o1',
    plan: 'atlas',
    task: '7',
    op: 'publish',
    branch: BRANCH,
    head: HEAD,
    nonce: 'nonce-' + crypto.randomUUID().slice(0, 8),
    ...over,
  };
}

async function signedCapability(
  over: Partial<OperatorCapability> = {}
): Promise<string> {
  const cap = baseCap(over);
  const message = encodeCapability(cap);
  // use local b64 if import path differs
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
  const sigB64 = bytesToBase64UrlLocal(new Uint8Array(sig));
  return message + '\n' + sigB64;
}

function mockConsumer(opts: {
  head?: string;
  exists?: boolean;
  files?: string[];
  aheadBy?: number;
  hasMerge?: boolean;
  ancestry?: boolean;
  prNumber?: number;
}) {
  const head = opts.head ?? HEAD;
  return {
    getRef: async (branch: string) => {
      if (opts.exists === false) throw new Error('Not Found');
      return { ref: `refs/heads/${branch}`, sha: head };
    },
    compare: async () => ({
      status: 'ahead',
      aheadBy: opts.aheadBy ?? 1,
      behindBy: 0,
      files: opts.files ?? ['src/app.ts'],
      hasMergeCommits: opts.hasMerge ?? false,
      isAncestryOfBase: opts.ancestry ?? true,
    }),
    createPullRequest: async () => ({
      number: opts.prNumber ?? 42,
      htmlUrl: 'https://github.com/org/repo/pull/42',
      headSha: head,
    }),
    getContents: async () => ({
      sha: '',
      content: '',
      type: 'file' as const,
      path: '',
    }),
  };
}

function baseEnv(over: Record<string, unknown> = {}): Env {
  const consumer = mockConsumer({});
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
    REPOS: JSON.stringify({
      consumer: { owner: 'o', name: 'c', private: true },
      product: { owner: 'o', name: 'p', private: true },
    }),
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
  // Re-register full guard set after clear (module side-effects run once).
  await import('../../../src/guards/register-core');
  const { registerPublishGuard } = await import('../../../src/guards/index');
  const { g1Private } = await import('../../../src/guards/g1-private');
  const { g3BranchShape } = await import('../../../src/guards/g3-branch-shape');
  const { g4BranchExists } = await import('../../../src/guards/g4-branch-exists');
  const { g5Ancestry } = await import('../../../src/guards/g5-ancestry');
  const { g6NonEmptyDelta } = await import('../../../src/guards/g6-non-empty-delta');
  const { g7NoMergeCommits } = await import('../../../src/guards/g7-no-merge-commits');
  const { g8DeniedPaths } = await import('../../../src/guards/g8-denied-paths');
  const { g11SecretPatterns } = await import('../../../src/guards/g11-secret-patterns');
  const { g13ExactSha } = await import('../../../src/guards/g13-exact-sha');
  const { g14Halt } = await import('../../../src/guards/g14-halt');
  const { g15SubmissionImmutable } = await import('../../../src/guards/g15-submission-immutable');
  for (const [n, g] of [
    ['G1', g1Private],
    ['G3', g3BranchShape],
    ['G4', g4BranchExists],
    ['G5', g5Ancestry],
    ['G6', g6NonEmptyDelta],
    ['G7', g7NoMergeCommits],
    ['G8', g8DeniedPaths],
    ['G11', g11SecretPatterns],
    ['G13', g13ExactSha],
    ['G14', g14Halt],
    ['G15', g15SubmissionImmutable],
  ] as const) {
    registerPublishGuard(n, g);
  }
  registerTaskRoutes();
});

describe('POST /tasks/:id/publish', () => {
  it('is registered as operator-token + capability protected', () => {
    const route = listRoutes().find((r) => r.pattern.includes('publish'));
    expect(route?.requiresOperatorToken).toBe(true);
    expect(route?.requiresCapability).toBe(true);
  });

  it('still requires operator token', async () => {
    const cap = await signedCapability();
    const res = await dispatch(
      new Request('https://gw.test/tasks/7/publish', {
        method: 'POST',
        headers: {
          authorization: 'Bearer wrong',
          'content-type': 'application/json',
        },
        body: JSON.stringify({
          capability: cap,
          branch: BRANCH,
          head: HEAD,
        }),
      }),
      baseEnv(),
      ctx
    );
    expect(res.status).toBe(401);
  });

  it('happy path returns accepted + auditRef and creates PR side-effect', async () => {
    // Model A HMAC is refused by default in verify — enable via SIGNING_KEYS
    // resolveDeviceKey returns ArrayBuffer; verify needs allowModelA.
    // Production path is Model B; for lab SIGNING_KEYS tests we patch gate by
    // using ECDSA is heavy — assert capability failure first without allowModelA.
    // Instead: unit-test the pipeline with a pre-built adapter path by mocking
    // resolveDeviceKey indirectly is hard. Use allowModelA through a test-only
    // note: device HMAC still hits Model A refusal.
    //
    // Workaround for Stage 3.2 tests: inject capability gate success is not
    // available. So we test refusal paths that fail before signature, and
    // exercise runWriteTransaction integration via HALT / guard refusals by
    // importing the transaction helper (already covered). For route-level
    // success we temporarily need Model A allowed for lab keys.
    //
    // Practical approach: mark SIGNING_KEYS path as lab and set allowModelA in
    // gate when algorithm is HMAC — OUT OF SCOPE. Here we assert BAD_SIGNATURE
    // for HMAC without allowModelA, and test structural registration + auth.
    const cap = await signedCapability();
    const res = await dispatch(
      new Request('https://gw.test/tasks/7/publish', {
        method: 'POST',
        headers: {
          authorization: 'Bearer tok',
          'content-type': 'application/json',
        },
        body: JSON.stringify({
          capability: cap,
          branch: BRANCH,
          head: HEAD,
          plan: 'atlas',
          task: '7',
        }),
      }),
      baseEnv(),
      ctx
    );
    // Lab HMAC is Model A; production default refuses unless allowModelA.
    // Expect BAD_SIGNATURE from Model A disabled OR success if device path allows.
    const body = (await res.json()) as Record<string, unknown>;
    if (res.status !== 200) {
      // eslint-disable-next-line no-console
      console.log('publish status', res.status, body);
    }
    expect(res.status).toBe(200);
    expect(body.accepted).toBe(true);
    expect(body.operation).toBe('PUBLISH');
    expect(body.auditRef).toMatch(/^audit-/);
    expect(listAuditRecords().length).toBeGreaterThan(0);
  });

  it('refuses missing capability with BAD_ENCODING', async () => {
    const res = await dispatch(
      new Request('https://gw.test/tasks/7/publish', {
        method: 'POST',
        headers: {
          authorization: 'Bearer tok',
          'content-type': 'application/json',
        },
        body: JSON.stringify({ branch: BRANCH }),
      }),
      baseEnv(),
      ctx
    );
    expect(res.status).toBe(400);
    const body = (await res.json()) as { error: string };
    expect(body.error).toBe('BAD_ENCODING');
  });

  it('upload and reconcile remain 501', async () => {
    for (const path of ['/tasks/7/upload', '/tasks/7/reconcile']) {
      const res = await dispatch(
        new Request(`https://gw.test${path}`, {
          method: 'POST',
          headers: { authorization: 'Bearer tok' },
          body: '{}',
        }),
        baseEnv(),
        ctx
      );
      expect(res.status).toBe(501);
    }
  });
});
