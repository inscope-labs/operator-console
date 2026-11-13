const _idemSeq = { n: 0 };
function idemKey(): string {
  _idemSeq.n += 1;
  return _idemSeq.n.toString(16).padStart(32, '0');
}

import { describe, it, expect, beforeEach } from 'vitest';
import { clearRoutes, dispatch, listRoutes } from '../../../src/routes/router';
import { registerTaskRoutes } from '../../../src/routes/tasks';
import { clearPublishGuards, registerPublishGuard } from '../../../src/guards/index';
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
const SECRET = new TextEncoder().encode('lab-hmac-secret-key-32b-minimum!!');

function bytesToBase64UrlLocal(bytes: Uint8Array): string {
  let s = '';
  for (let i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function toB64(bytes: Uint8Array): string {
  let s = '';
  for (let i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
  return btoa(s);
}

async function sha256Hex(bytes: Uint8Array): Promise<string> {
  const dig = await crypto.subtle.digest('SHA-256', bytes);
  return Array.from(new Uint8Array(dig))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

async function signedCapability(
  over: Partial<OperatorCapability> = {}
): Promise<string> {
  const cap: OperatorCapability = {
    version: CAPABILITY_VERSION,
    kid: 'lab1',
    exp: NOW + 120,
    order: 'o1',
    plan: 'atlas',
    task: '7',
    op: 'upload',
    branch: BRANCH,
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
  return message + '\n' + bytesToBase64UrlLocal(new Uint8Array(sig));
}

function mockConsumer(opts: { submissionExists?: boolean } = {}) {
  const store = new Map<string, Uint8Array>();
  if (opts.submissionExists) {
    store.set('submissions/sub-1.zip', new Uint8Array([1]));
  }
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
    pathExists: async (path: string) => store.has(path),
    putFile: async (o: {
      path: string;
      contentBytes: Uint8Array;
      message: string;
      branch: string;
    }) => {
      if (store.has(o.path)) throw new Error('already exists');
      store.set(o.path, o.contentBytes);
      return { contentSha: 'b'.repeat(40), path: o.path };
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
  const names = [
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
  ];
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
  names.forEach((n, i) => registerPublishGuard(n, guards[i]));
  registerTaskRoutes();
});

describe('POST /tasks/:id/upload', () => {
  it('is registered as protected privileged route', () => {
    const route = listRoutes().find((r) => r.pattern.includes('upload'));
    expect(route?.requiresOperatorToken).toBe(true);
    expect(route?.requiresCapability).toBe(true);
  });

  it('refuses missing capability', async () => {
    const res = await dispatch(
      new Request('https://gw.test/tasks/7/upload', {
        method: 'POST',
        headers: {
          authorization: 'Bearer tok',
          'content-type': 'application/json',
        },
        body: JSON.stringify({
          idempotencyKey: idemKey(), submissionId: 'sub-1' }),
      }),
      baseEnv(),
      ctx
    );
    expect(res.status).toBe(400);
    const body = (await res.json()) as { error: string };
    expect(body.error).toBe('BAD_ENCODING');
  });

  it('refuses mismatched zipSha256 before capability gate side-effects on body', async () => {
    const cap = await signedCapability();
    const res = await dispatch(
      new Request('https://gw.test/tasks/7/upload', {
        method: 'POST',
        headers: {
          authorization: 'Bearer tok',
          'content-type': 'application/json',
        },
        body: JSON.stringify({
          idempotencyKey: idemKey(),
          capability: cap,
          submissionId: 'sub-1',
          zipBase64: toB64(new TextEncoder().encode('x')),
          zipSha256: 'b'.repeat(64),
          branch: BRANCH,
          head: HEAD,
        }),
      }),
      baseEnv(),
      ctx
    );
    expect(res.status).toBe(400);
    const body = (await res.json()) as { error: string; message: string };
    expect(body.error).toBe('BAD_ENCODING');
    expect(body.message).toMatch(/does not match/);
  });

  it('happy path stores submission and returns auditRef', async () => {
    const bytes = new TextEncoder().encode('PK\x03\x04submission');
    const hex = await sha256Hex(bytes);
    const cap = await signedCapability();
    const res = await dispatch(
      new Request('https://gw.test/tasks/7/upload', {
        method: 'POST',
        headers: {
          authorization: 'Bearer tok',
          'content-type': 'application/json',
        },
        body: JSON.stringify({
          idempotencyKey: idemKey(),
          capability: cap,
          submissionId: 'sub-7731',
          zipBase64: toB64(bytes),
          zipSha256: hex,
          branch: BRANCH,
          head: HEAD,
          plan: 'atlas',
          task: '7',
        }),
      }),
      baseEnv(),
      ctx
    );
    const body = (await res.json()) as Record<string, unknown>;
    expect(res.status).toBe(200);
    expect(body.accepted).toBe(true);
    expect(body.operation).toBe('UPLOAD');
    expect(body.auditRef).toMatch(/^audit-/);
    expect(listAuditRecords().length).toBeGreaterThan(0);
  });

  it('refuses when submission already exists (G15)', async () => {
    const bytes = new TextEncoder().encode('PK\x03\x04submission');
    const hex = await sha256Hex(bytes);
    const cap = await signedCapability();
    const consumer = mockConsumer({ submissionExists: true });
    // pre-seed path matching sub-1
    const res = await dispatch(
      new Request('https://gw.test/tasks/7/upload', {
        method: 'POST',
        headers: {
          authorization: 'Bearer tok',
          'content-type': 'application/json',
        },
        body: JSON.stringify({
          idempotencyKey: idemKey(),
          capability: cap,
          submissionId: 'sub-1',
          zipBase64: toB64(bytes),
          zipSha256: hex,
          branch: BRANCH,
          head: HEAD,
        }),
      }),
      baseEnv({
        __adapter: {
          consumer: () => consumer,
          product: () => consumer,
        },
      }),
      ctx
    );
    expect(res.status).toBe(409);
    const body = (await res.json()) as { error: string };
    expect(body.error).toBe('SUBMISSION_IMMUTABLE');
  });
});
