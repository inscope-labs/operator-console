import { describe, it, expect, beforeEach } from 'vitest';
import { dispatch, clearRoutes } from '../../../src/routes/router';
import { registerDoctorRoutes } from '../../../src/routes/doctor';
import { registerSyncRoutes } from '../../../src/routes/sync';
import type { Env } from '../../../src/index';

const ctx = {} as ExecutionContext;
const TEST_HEAD = 'b3c4d5e6f708192a3b4c5d6e7f8a9b0c1d2e3f4a';
const PRODUCT_HEAD = 'a1b2c3d4e5f60718293a4b5c6d7e8f9012345678';

function baseEnv(over: Record<string, unknown> = {}): Env {
  const nonceStub = {
    fetch: () =>
      Promise.resolve(new Response(JSON.stringify({ ok: true }), { status: 200 })),
  };

  const mockConsumer = {
    getRef: async (branch: string) => {
      if (branch === 'missing') throw Object.assign(new Error('GitHub 404: Not Found'), { status: 404 });
      return { sha: TEST_HEAD, ref: `refs/heads/${branch}` };
    },
    compare: async (_base: string, _head: string) => ({
      status: 'identical',
      aheadBy: 0,
      behindBy: 0,
      files: [],
      hasMergeCommits: false,
      isAncestryOfBase: true,
    }),
    getRepoMetadata: async () => ({ private: true, full_name: 'org/consumer' }),
  };

  const mockProduct = {
    getRef: async () => ({ sha: PRODUCT_HEAD, ref: 'refs/heads/main' }),
  };

  const mockAdapter = {
    consumer: () => mockConsumer,
    product: () => mockProduct,
    tokenExpiresAt: () => Math.floor(Date.now() / 1000) + 3600,
  };

  return {
    ENVIRONMENT: 'test',
    OPERATOR_TOKEN: 'test-token',
    SIGNING_KEYS: JSON.stringify([
      { kid: 'k_lab', algorithm: 'HMAC', secret: 'lab-secret-for-stubs-test-32bytes!', encoding: 'utf8' },
    ]),
    REPOS: '{}',
    GITHUB_APP_ID: 'app-id',
    GITHUB_APP_PRIVATE_KEY: '-----BEGIN PRIVATE KEY-----\nMIIE...\n-----END PRIVATE KEY-----',
    GITHUB_APP_INSTALLATION_ID: 'inst-id',
    NONCE_STORE: {
      idFromName: () => ({ toString: () => 'id' }),
      get: () => nonceStub,
    },
    __adapter: mockAdapter as any,
    __haltActive: false,
    ...over,
  } as unknown as Env;
}

beforeEach(() => {
  clearRoutes();
  registerDoctorRoutes();
  registerSyncRoutes();
});

describe('Stage 4.2 POST /sync', () => {
  it('requires operator token', async () => {
    const res = await dispatch(
      new Request('https://gw.test/sync', { method: 'POST', body: '{}' }),
      baseEnv(),
      ctx
    );
    expect(res.status).toBe(401);
  });

  it('returns contract shape when in sync', async () => {
    const res = await dispatch(
      new Request('https://gw.test/sync', {
        method: 'POST',
        headers: {
          authorization: 'Bearer test-token',
          'content-type': 'application/json',
        },
        body: JSON.stringify({ branch: 'main' }),
      }),
      baseEnv(),
      ctx
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      status: string;
      headSha: string;
      inSync: boolean;
    };
    expect(body.headSha).toBe(TEST_HEAD);
    expect(typeof body.inSync).toBe('boolean');
    expect(typeof body.status).toBe('string');
    expect(body.inSync).toBe(true);
  });

  it('defaults branch to main when body omitted', async () => {
    const res = await dispatch(
      new Request('https://gw.test/sync', {
        method: 'POST',
        headers: { authorization: 'Bearer test-token' },
      }),
      baseEnv(),
      ctx
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as { headSha: string };
    expect(body.headSha).toBe(TEST_HEAD);
  });

  it('GET /verify-main returns same shape', async () => {
    const res = await dispatch(
      new Request('https://gw.test/verify-main', {
        headers: { authorization: 'Bearer test-token' },
      }),
      baseEnv(),
      ctx
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as { headSha: string; inSync: boolean };
    expect(body.headSha).toBe(TEST_HEAD);
  });
});

describe('Stage 4.2 GET /doctor', () => {
  it('requires operator token', async () => {
    const res = await dispatch(
      new Request('https://gw.test/doctor'),
      baseEnv(),
      ctx
    );
    expect(res.status).toBe(401);
  });

  it('returns healthy + checks when all pass', async () => {
    const res = await dispatch(
      new Request('https://gw.test/doctor', {
        headers: { authorization: 'Bearer test-token' },
      }),
      baseEnv(),
      ctx
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      healthy: boolean;
      checks: Array<{ name: string; passed: boolean; details?: string }>;
    };
    expect(body.healthy).toBe(true);
    expect(Array.isArray(body.checks)).toBe(true);
    const names = body.checks.map((c) => c.name);
    expect(names).toContain('github_token_validity');
    expect(names).toContain('halt_state');
    expect(names).toContain('repo_privacy');
    expect(body.checks.every((c) => c.passed)).toBe(true);
  });

  it('never leaks private key or token material in details', async () => {
    const res = await dispatch(
      new Request('https://gw.test/doctor', {
        headers: { authorization: 'Bearer test-token' },
      }),
      baseEnv(),
      ctx
    );
    const text = await res.text();
    expect(text).not.toMatch(/BEGIN PRIVATE KEY/);
    expect(text).not.toMatch(/MIIE/);
    expect(text).not.toMatch(/gh[pousr]_/);
    expect(text).not.toMatch(/lab-secret-for-stubs/);
  });

  it('reports halt_state when halted', async () => {
    const res = await dispatch(
      new Request('https://gw.test/doctor', {
        headers: { authorization: 'Bearer test-token' },
      }),
      baseEnv({ __haltActive: true }),
      ctx
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      healthy: boolean;
      checks: Array<{ name: string; passed: boolean; details?: string }>;
    };
    const halt = body.checks.find((c) => c.name === 'halt_state');
    expect(halt).toBeDefined();
    expect(halt!.passed).toBe(false);
    expect(halt!.details).toMatch(/halted/i);
    expect(body.healthy).toBe(false);
  });

  it('POST /doctor is accepted (same payload)', async () => {
    const res = await dispatch(
      new Request('https://gw.test/doctor', {
        method: 'POST',
        headers: { authorization: 'Bearer test-token' },
      }),
      baseEnv(),
      ctx
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as { healthy: boolean };
    expect(typeof body.healthy).toBe('boolean');
  });
});
