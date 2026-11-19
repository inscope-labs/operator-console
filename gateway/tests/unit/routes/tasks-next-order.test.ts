/**
 * GET /tasks/next — contract §3.6 (revision 1.3): every item carries the
 * work-order identifier the task was issued under, and a task the gateway
 * holds no work-order identifier for is not listed.
 *
 * The gateway reports the order it holds. It never derives, defaults or
 * invents one.
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { clearRoutes, dispatch } from '../../../src/routes/router';
import { registerPlanRoutes } from '../../../src/routes/plans';
import { registerTaskRoutes } from '../../../src/routes/tasks';
import { resetPlanMemory } from '../../../src/plan/client';
import { resetMemoryHalt } from '../../../src/halt/state';
import { resetOperationMemory } from '../../../src/idempotency/store-client';
import type { Env } from '../../../src/runtime';

const HEAD_SHA = 'a'.repeat(40);

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

const _idemSeq = { n: 0 };
function idemKey(): string {
  _idemSeq.n += 1;
  return _idemSeq.n.toString(16).padStart(32, '0');
}

beforeEach(() => {
  clearRoutes();
  resetPlanMemory();
  resetMemoryHalt();
  resetOperationMemory();
  registerPlanRoutes();
  registerTaskRoutes();
});

async function getTasksNext(env: Env): Promise<Array<Record<string, unknown>>> {
  const res = await dispatch(
    new Request('https://gw.test/tasks/next', {
      headers: { authorization: 'Bearer tok' },
    }),
    env,
    ctx
  );
  expect(res.status).toBe(200);
  return (await res.json()) as Array<Record<string, unknown>>;
}

async function putPlan(
  env: Env,
  body: Record<string, unknown>,
  planId = 'atlas'
): Promise<Response> {
  return dispatch(
    new Request(`https://gw.test/plans/${planId}`, {
      method: 'PUT',
      headers: {
        authorization: 'Bearer tok',
        'content-type': 'application/json',
      },
      body: JSON.stringify({ ...body, idempotencyKey: idemKey() }),
    }),
    env,
    ctx
  );
}

describe('GET /tasks/next — work-order identifier', () => {
  it('lists a ready task with the order it was issued under', async () => {
    const env = baseEnv();
    await putPlan(env, {
      title: 'Atlas',
      status: 'in_progress',
      tasks: [
        {
          id: '10008',
          title: 'Refactor parser',
          branch: 'task-10008-refactor-parser',
          status: 'ready',
          headSha: HEAD_SHA,
          order: 'order-7731',
        },
      ],
    });

    const body = await getTasksNext(env);
    expect(body).toHaveLength(1);
    expect(body[0].id).toBe('10008');
    expect(body[0].planId).toBe('atlas');
    expect(body[0].order).toBe('order-7731');
    // The item carries exactly the keys of tasks-next.schema.json.
    expect(Object.keys(body[0]).sort()).toEqual([
      'branch',
      'headSha',
      'id',
      'order',
      'planId',
      'status',
      'title',
    ]);
  });

  it('omits a task the gateway holds no work-order identifier for', async () => {
    const env = baseEnv();
    await putPlan(env, {
      title: 'Atlas',
      status: 'in_progress',
      tasks: [
        {
          id: 'no-order',
          title: 'No order',
          branch: 'task-no-order',
          status: 'ready',
          headSha: HEAD_SHA,
        },
        {
          id: 'empty-order',
          title: 'Empty order',
          branch: 'task-empty-order',
          status: 'ready',
          headSha: HEAD_SHA,
          order: '',
        },
        {
          id: 'unassigned-no-order',
          title: 'Unassigned, no order',
          branch: 'task-unassigned',
          status: 'unassigned',
          headSha: HEAD_SHA,
        },
        {
          id: 'with-order',
          title: 'With order',
          branch: 'task-with-order',
          status: 'ready',
          headSha: HEAD_SHA,
          order: 'order-7731',
        },
      ],
    });

    const body = await getTasksNext(env);
    expect(body.map((t) => t.id)).toEqual(['with-order']);
    expect(body[0].order).toBe('order-7731');
  });

  it('stores the order per task through PUT /plans/:id (the harness path)', async () => {
    const env = baseEnv();
    const res = await putPlan(env, {
      title: 'Atlas',
      status: 'in_progress',
      tasks: [
        {
          id: '10008',
          title: 'Refactor parser',
          branch: 'task-10008-refactor-parser',
          status: 'ready',
          headSha: HEAD_SHA,
          order: 'order-7731',
        },
        {
          id: '10009',
          title: 'Second task',
          branch: 'task-10009-other',
          status: 'ready',
          headSha: HEAD_SHA,
          order: 'order-7732',
        },
      ],
    });
    expect(res.status).toBe(201);

    const body = await getTasksNext(env);
    expect(body.map((t) => t.order)).toEqual(['order-7731', 'order-7732']);
  });

  it('never derives an order for a task that has none', async () => {
    const env = baseEnv();
    await putPlan(env, {
      title: 'Atlas',
      status: 'in_progress',
      tasks: [
        {
          id: '10008',
          title: 'Refactor parser',
          branch: 'task-10008-refactor-parser',
          status: 'ready',
          headSha: HEAD_SHA,
        },
      ],
    });

    const body = await getTasksNext(env);
    // Not listed, and nothing invented in its place.
    expect(body).toHaveLength(0);
  });
});
