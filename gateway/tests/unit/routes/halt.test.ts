import { describe, it, expect, beforeEach } from 'vitest';
import { clearRoutes, dispatch, listRoutes } from '../../../src/routes/router';
import { registerHaltRoutes } from '../../../src/routes/halt';
import { registerTaskRoutes } from '../../../src/routes/tasks';
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
const SECRET = new TextEncoder().encode('lab-hmac-secret-key-32b-minimum!!');

function b64url(bytes: Uint8Array): string {
  let s = '';
  for (let i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

async function signedCap(
  over: Partial<OperatorCapability>
): Promise<string> {
  const cap: OperatorCapability = {
    version: CAPABILITY_VERSION,
    kid: 'lab1',
    exp: NOW + 120,
    order: 'o1',
    plan: 'atlas',
    task: '0',
    op: 'halt',
    branch: 'engine',
    head: HEAD,
    nonce: 'nonce-' + crypto.randomUUID().slice(0, 8),
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

function mockConsumer() {
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
    createPullRequest: async () => ({
      number: 1,
      htmlUrl: 'https://example/pr/1',
      headSha: HEAD,
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
  registerHaltRoutes();
  registerTaskRoutes();
});

describe('POST /halt and /resume', () => {
  it('registers halt and resume routes', () => {
    const paths = listRoutes().map((r) => r.pattern);
    expect(paths.some((p) => p.includes('halt'))).toBe(true);
    expect(paths.some((p) => p.includes('resume'))).toBe(true);
  });

  it('halt engages marker and emits audit', async () => {
    const cap = await signedCap({ op: 'halt' });
    const env = baseEnv({ __haltActive: false });
    const res = await dispatch(
      new Request('https://gw.test/halt', {
        method: 'POST',
        headers: {
          authorization: 'Bearer tok',
          'content-type': 'application/json',
        },
        body: JSON.stringify({
          capability: cap,
          branch: 'engine',
          head: HEAD,
          reason: 'security review',
        }),
      }),
      env,
      ctx
    );
    const body = (await res.json()) as Record<string, unknown>;
    expect(res.status).toBe(200);
    expect(body.accepted).toBe(true);
    expect(body.operation).toBe('HALT');
    expect(body.auditRef).toMatch(/^audit-/);
    expect(env.__haltActive).toBe(true);
  });

  it('while halted, publish is refused with HALT_ACTIVE', async () => {
    const pubCap = await signedCap({
      op: 'publish',
      branch: 'task-7-work',
      task: '7',
    });
    const env = baseEnv({ __haltActive: true });
    const res = await dispatch(
      new Request('https://gw.test/tasks/7/publish', {
        method: 'POST',
        headers: {
          authorization: 'Bearer tok',
          'content-type': 'application/json',
        },
        body: JSON.stringify({
          capability: pubCap,
          branch: 'task-7-work',
          head: HEAD,
        }),
      }),
      env,
      ctx
    );
    expect(res.status).toBe(409);
    const body = (await res.json()) as { error: string };
    expect(body.error).toBe('HALT_ACTIVE');
  });

  it('resume clears marker and emits audit', async () => {
    const cap = await signedCap({ op: 'resume' });
    const env = baseEnv({ __haltActive: true });
    const res = await dispatch(
      new Request('https://gw.test/resume', {
        method: 'POST',
        headers: {
          authorization: 'Bearer tok',
          'content-type': 'application/json',
        },
        body: JSON.stringify({
          capability: cap,
          branch: 'engine',
          head: HEAD,
          reason: 'audit done',
        }),
      }),
      env,
      ctx
    );
    const body = (await res.json()) as Record<string, unknown>;
    expect(res.status).toBe(200);
    expect(body.operation).toBe('RESUME');
    expect(body.auditRef).toMatch(/^audit-/);
    expect(env.__haltActive).toBe(false);
    expect(listAuditRecords().some((r) => r.op === 'resume')).toBe(true);
  });

  it('rejects non-engine branch on halt', async () => {
    const cap = await signedCap({ op: 'halt', branch: 'task-x' });
    const res = await dispatch(
      new Request('https://gw.test/halt', {
        method: 'POST',
        headers: {
          authorization: 'Bearer tok',
          'content-type': 'application/json',
        },
        body: JSON.stringify({
          capability: cap,
          branch: 'task-x',
          head: HEAD,
        }),
      }),
      baseEnv(),
      ctx
    );
    expect(res.status).toBe(400);
    const body = (await res.json()) as { error: string };
    expect(body.error).toBe('BRANCH_INVALID');
  });
});
