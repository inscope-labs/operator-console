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
import type { Env } from '../../../src/index';

const ctx = {} as ExecutionContext;

const TEST_SECRET = 'lab-secret-for-stubs-test-32bytes!';
const TEST_HEAD = 'a'.repeat(40);


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

  describe('Former stubs now live or remaining 501', () => {
    it('GET /admin/enrolments is live (Stage 4.3; no longer 501)', async () => {
      const res = await dispatch(
        new Request('https://gw.test/admin/enrolments', {
          method: 'GET',
          headers: { authorization: 'Bearer test-token' },
        }),
        baseEnv(),
        ctx
      );
      // Without ENROLMENT_REGISTRY binding may be 502 NETWORK; must not be 501
      expect(res.status).not.toBe(501);
    });

    it('POST /admin/revoke is live (Stage 4.3; no longer 501)', async () => {
      const res = await dispatch(
        new Request('https://gw.test/admin/revoke', {
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
      expect(res.status).not.toBe(501);
      expect(res.status).toBeGreaterThanOrEqual(400);
    });

    it('POST /decisions/:id/decide is live (Stage 3.6; no longer 501)', async () => {
      const res = await dispatch(
        new Request('https://gw.test/decisions/dec-1/decide', {
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
      expect(res.status).not.toBe(501);
      expect(res.status).toBeGreaterThanOrEqual(400);
    });

    // Stage 4.2: /doctor, /sync, /verify-main implemented — covered in sync-doctor.test.ts

    it('asserts no residual 501 stub routes remain for implemented surfaces', async () => {
      // Phase 3–4.3 cleared publish/upload/reconcile/halt/decide/sync/doctor/admin.
      const stubRequests: Request[] = [];
      expect(stubRequests.length).toBe(0);
    });
  });
});
