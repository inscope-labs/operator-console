import { describe, it, expect, beforeEach } from 'vitest';
import {
  dispatch,
  clearRoutes,
  listRoutes,
} from '../../../src/routes/router';
import { registerTaskRoutes } from '../../../src/routes/tasks';
import { registerAdminRoutes } from '../../../src/routes/admin';
import { registerDecisionRoutes } from '../../../src/routes/decisions';
import { registerDoctorRoutes } from '../../../src/routes/doctor';
import { registerSyncRoutes } from '../../../src/routes/sync';
import { encodeCapability } from '../../../src/capability/codec';
import { CAPABILITY_VERSION, type OperatorCapability } from '../../../src/capability/types';
import { bytesToBase64Url } from '../../../src/capability/verify';
import type { Env } from '../../../src/index';

const ctx = {} as ExecutionContext;

const TEST_SECRET = 'lab-secret-for-stubs-test-32bytes!';
const TEST_HEAD = 'a'.repeat(40);

async function signHmac(content: string, secretStr: string): Promise<string> {
  const enc = new TextEncoder();
  const key = await crypto.subtle.importKey(
    'raw',
    enc.encode(secretStr),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign']
  );
  const sig = await crypto.subtle.sign('HMAC', key, enc.encode(content));
  return bytesToBase64Url(new Uint8Array(sig));
}

function baseEnv(over: Record<string, unknown> = {}): Env {
  const nonceStub = {
    fetch: () => Promise.resolve(new Response(JSON.stringify({ ok: true }), { status: 200 })),
  };

  const mockConsumer = {
    getRef: async () => ({ sha: TEST_HEAD }),
    getBranch: async () => ({ name: 'task-task-1', commit: { sha: TEST_HEAD } }),
    compareCommits: async () => ({ ahead_by: 1, behind_by: 0, status: 'ahead', commits: [{ parents: [{ sha: 'root' }] }] }),
  };

  const mockAdapter = {
    consumer: () => mockConsumer,
  };

  return {
    ENVIRONMENT: 'test',
    OPERATOR_TOKEN: 'test-token',
    SIGNING_KEYS: JSON.stringify([
      { kid: 'k_lab', algorithm: 'HMAC', secret: TEST_SECRET, encoding: 'utf8' },
    ]),
    REPOS: '{}',
    GITHUB_APP_ID: 'app-id',
    GITHUB_APP_PRIVATE_KEY: 'key',
    GITHUB_APP_INSTALLATION_ID: 'inst-id',
    NONCE_STORE: {
      idFromName: () => ({ toString: () => 'id' }),
      get: () => nonceStub,
    },
    __adapter: mockAdapter as any,
    ...over,
  } as unknown as Env;
}

beforeEach(() => {
  clearRoutes();
  registerTaskRoutes();
  registerAdminRoutes();
  registerDecisionRoutes();
  registerDoctorRoutes();
  registerSyncRoutes();
});

describe('T1-103 fail-closed stubs (Stage 1.1)', () => {
  it('registers all stub routes', () => {
    const routes = listRoutes();
    const paths = routes.map((r) => `${r.method} ${r.pattern}`);
    expect(paths.some((p) => p.includes('tasks'))).toBe(true);
    expect(paths.some((p) => p.includes('admin'))).toBe(true);
    expect(paths.some((p) => p.includes('decisions'))).toBe(true);
    expect(paths.some((p) => p.includes('doctor'))).toBe(true);
    expect(paths.some((p) => p.includes('sync'))).toBe(true);
  });

  describe('POST /tasks/:id/publish (Stage 3.2 live)', () => {
    it('requires operator token (returns 401 when missing)', async () => {
      const res = await dispatch(
        new Request('https://gw.test/tasks/task-1/publish', {
          method: 'POST',
          body: JSON.stringify({ capability: 'dummy' }),
        }),
        baseEnv(),
        ctx
      );
      expect(res.status).toBe(401);
      const body = (await res.json()) as { error: string };
      expect(body.error).toBe('OPERATOR_AUTH');
    });

    it('refuses missing capability with BAD_ENCODING (no longer 501 stub)', async () => {
      const res = await dispatch(
        new Request('https://gw.test/tasks/task-1/publish', {
          method: 'POST',
          headers: {
            authorization: 'Bearer test-token',
            'content-type': 'application/json',
          },
          body: JSON.stringify({}),
        }),
        baseEnv(),
        ctx
      );
      expect(res.status).toBe(400);
      const body = (await res.json()) as { error: string };
      expect(body.error).toBe('BAD_ENCODING');
    });
  });

  describe('Stub routes return 501 NOT_IMPLEMENTED', () => {
    it('GET /admin/enrolments returns 501', async () => {
      const res = await dispatch(
        new Request('https://gw.test/admin/enrolments', {
          method: 'GET',
          headers: { authorization: 'Bearer test-token' },
        }),
        baseEnv(),
        ctx
      );
      expect(res.status).toBe(501);
      const body = (await res.json()) as { error: string; message: string };
      expect(body.error).toBe('NOT_IMPLEMENTED');
      expect(body.message).toMatch(/admin routes not yet implemented/);
    });

    it('POST /admin/revoke returns 501', async () => {
      const res = await dispatch(
        new Request('https://gw.test/admin/revoke', {
          method: 'POST',
          headers: { authorization: 'Bearer test-token' },
          body: JSON.stringify({ kid: 'k1' }),
        }),
        baseEnv(),
        ctx
      );
      expect(res.status).toBe(501);
      const body = (await res.json()) as { error: string };
      expect(body.error).toBe('NOT_IMPLEMENTED');
    });

    it('POST /decisions/:id/decide returns 501', async () => {
      const res = await dispatch(
        new Request('https://gw.test/decisions/dec-1/decide', {
          method: 'POST',
          headers: { authorization: 'Bearer test-token' },
          body: JSON.stringify({ decision: 'approved' }),
        }),
        baseEnv(),
        ctx
      );
      expect(res.status).toBe(501);
      const body = (await res.json()) as { error: string; message: string };
      expect(body.error).toBe('NOT_IMPLEMENTED');
      expect(body.message).toMatch(/decide endpoint not yet implemented/);
    });

    it('GET /doctor returns 501', async () => {
      const res = await dispatch(
        new Request('https://gw.test/doctor', {
          method: 'GET',
          headers: { authorization: 'Bearer test-token' },
        }),
        baseEnv(),
        ctx
      );
      expect(res.status).toBe(501);
      const body = (await res.json()) as { error: string; message: string };
      expect(body.error).toBe('NOT_IMPLEMENTED');
      expect(body.message).toMatch(/doctor endpoint not yet implemented/);
    });

    it('POST /doctor returns 501', async () => {
      const res = await dispatch(
        new Request('https://gw.test/doctor', {
          method: 'POST',
          headers: { authorization: 'Bearer test-token' },
        }),
        baseEnv(),
        ctx
      );
      expect(res.status).toBe(501);
      const body = (await res.json()) as { error: string };
      expect(body.error).toBe('NOT_IMPLEMENTED');
    });

    it('POST /sync returns 501', async () => {
      const res = await dispatch(
        new Request('https://gw.test/sync', {
          method: 'POST',
          headers: { authorization: 'Bearer test-token' },
        }),
        baseEnv(),
        ctx
      );
      expect(res.status).toBe(501);
      const body = (await res.json()) as { error: string; message: string };
      expect(body.error).toBe('NOT_IMPLEMENTED');
      expect(body.message).toMatch(/sync endpoint not yet implemented/);
    });

    it('GET /verify-main returns 501', async () => {
      const res = await dispatch(
        new Request('https://gw.test/verify-main', {
          method: 'GET',
          headers: { authorization: 'Bearer test-token' },
        }),
        baseEnv(),
        ctx
      );
      expect(res.status).toBe(501);
      const body = (await res.json()) as { error: string };
      expect(body.error).toBe('NOT_IMPLEMENTED');
    });

    it('asserts no stub route returns 2xx', async () => {
      const stubRequests = [
        new Request('https://gw.test/admin/enrolments', {
          headers: { authorization: 'Bearer test-token' },
        }),
        new Request('https://gw.test/admin/revoke', {
          method: 'POST',
          headers: { authorization: 'Bearer test-token' },
        }),
        new Request('https://gw.test/decisions/dec-1/decide', {
          method: 'POST',
          headers: { authorization: 'Bearer test-token' },
        }),
        new Request('https://gw.test/doctor', {
          headers: { authorization: 'Bearer test-token' },
        }),
        new Request('https://gw.test/sync', {
          method: 'POST',
          headers: { authorization: 'Bearer test-token' },
        }),
        new Request('https://gw.test/verify-main', {
          headers: { authorization: 'Bearer test-token' },
        }),
      ];

      for (const req of stubRequests) {
        const res = await dispatch(req, baseEnv(), ctx);
        expect(res.status).toBe(501);
        expect(res.status).toBeGreaterThanOrEqual(400);
      }
    });
  });
});
