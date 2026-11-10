import { describe, it, expect, beforeEach } from 'vitest';
import { clearRoutes, dispatch } from '../../../src/routes/router';
import { registerPlanRoutes } from '../../../src/routes/plans';
import { registerTaskRoutes } from '../../../src/routes/tasks';
import { resetPlanMemory, putPlanRecord } from '../../../src/plan/client';
import { resetMemoryHalt } from '../../../src/halt/state';
import type { Env } from '../../../src/runtime';

function baseEnv(over: Record<string, unknown> = {}): Env {
  return {
    ENVIRONMENT: 'test',
    OPERATOR_TOKEN: 'tok',
    SIGNING_KEYS: '[]',
    REPOS: '{}',
    GITHUB_APP_ID: '1',
    GITHUB_APP_PRIVATE_KEY: 'k',
    GITHUB_APP_INSTALLATION_ID: '1',
    NONCE_STORE: {
      idFromName: () => ({ toString: () => 'n' }),
      get: () => ({
        fetch: async () => new Response('{}', { status: 200 }),
      }),
    },
    ...over,
  } as unknown as Env;
}

const ctx = {
  waitUntil: () => undefined,
  passThroughOnException: () => undefined,
} as unknown as ExecutionContext;

beforeEach(() => {
  clearRoutes();
  resetPlanMemory();
  resetMemoryHalt();
  registerPlanRoutes();
  registerTaskRoutes();
});

describe('Stage 4.1 plan/task read paths', () => {
  it('GET /plans returns catalogue array', async () => {
    const env = baseEnv();
    await putPlanRecord(env, 'atlas', {
      title: 'Atlas Engine Migration',
      status: 'in_progress',
      taskCount: 2,
      completedTaskCount: 1,
    });
    const res = await dispatch(
      new Request('https://gw.test/plans', {
        headers: { authorization: 'Bearer tok' },
      }),
      env,
      ctx
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as Array<{ id: string; title: string }>;
    expect(Array.isArray(body)).toBe(true);
    expect(body[0].id).toBe('atlas');
    expect(body[0].title).toMatch(/Atlas/);
  });

  it('GET /plans/:id/status returns contract shape', async () => {
    const env = baseEnv();
    await putPlanRecord(env, 'atlas', {
      title: 'Atlas',
      status: 'in_progress',
      openBranches: ['task-10008-refactor-parser'],
      currentMilestone: 'M2-Parser',
    });
    const res = await dispatch(
      new Request('https://gw.test/plans/atlas/status', {
        headers: { authorization: 'Bearer tok' },
      }),
      env,
      ctx
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      id: string;
      status: string;
      halted: boolean;
      openBranches: string[];
    };
    expect(body.id).toBe('atlas');
    expect(body.status).toBe('in_progress');
    expect(body.halted).toBe(false);
    expect(body.openBranches).toContain('task-10008-refactor-parser');
  });

  it('POST /plans/:id/validate and GET render-check', async () => {
    const env = baseEnv();
    await putPlanRecord(env, 'atlas', {
      title: 'Atlas',
      status: 'ready',
      tasks: [{ id: '1', title: 'T', status: 'ready' }],
    });
    const v = await dispatch(
      new Request('https://gw.test/plans/atlas/validate', {
        method: 'POST',
        headers: {
          authorization: 'Bearer tok',
          'content-type': 'application/json',
        },
        body: '{}',
      }),
      env,
      ctx
    );
    expect(v.status).toBe(200);
    const vb = (await v.json()) as { valid: boolean; planId: string };
    expect(vb.valid).toBe(true);
    expect(vb.planId).toBe('atlas');

    const r = await dispatch(
      new Request('https://gw.test/plans/atlas/render-check', {
        headers: { authorization: 'Bearer tok' },
      }),
      env,
      ctx
    );
    expect(r.status).toBe(200);
    const rb = (await r.json()) as { ok: boolean };
    expect(rb.ok).toBe(true);
  });

  it('GET /tasks/next returns ready tasks', async () => {
    const env = baseEnv();
    await putPlanRecord(env, 'atlas', {
      title: 'Atlas',
      status: 'in_progress',
      tasks: [
        {
          id: '10008',
          title: 'Refactor parser',
          branch: 'task-10008-refactor-parser',
          status: 'ready',
          headSha: 'b'.repeat(40),
        },
      ],
    });
    const res = await dispatch(
      new Request('https://gw.test/tasks/next', {
        headers: { authorization: 'Bearer tok' },
      }),
      env,
      ctx
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as Array<{
      id: string;
      planId: string;
      status: string;
    }>;
    expect(body).toHaveLength(1);
    expect(body[0].id).toBe('10008');
    expect(body[0].planId).toBe('atlas');
    expect(body[0].status).toBe('ready');
  });

  it('requires operator token', async () => {
    const res = await dispatch(
      new Request('https://gw.test/plans'),
      baseEnv(),
      ctx
    );
    expect(res.status).toBe(401);
  });
});
