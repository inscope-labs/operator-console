import { describe, it, expect, beforeEach } from 'vitest';
import { PlanDO } from '../../../src/durable-objects/plan-do';
import {
  resetPlanMemory,
  putPlanRecord,
  nextTasks,
} from '../../../src/plan/client';
import type { Env } from '../../../src/runtime';

class MemoryStorage {
  private map = new Map<string, unknown>();
  async get<T>(key: string): Promise<T | undefined> {
    return this.map.get(key) as T | undefined;
  }
  async put(key: string, value: unknown): Promise<void> {
    this.map.set(key, value);
  }
  async delete(key: string): Promise<boolean> {
    return this.map.delete(key);
  }
  async list(): Promise<Map<string, unknown>> {
    return new Map(this.map);
  }
}

function makeDO(): PlanDO {
  const storage = new MemoryStorage();
  const state = { storage } as unknown as DurableObjectState;
  return new PlanDO(state);
}

describe('PlanDO', () => {
  let planDo: PlanDO;
  beforeEach(() => {
    planDo = makeDO();
  });

  it('survives sequential put/get (restart simulation via same storage)', async () => {
    const put = await planDo.fetch(
      new Request('https://plan/plans/atlas', {
        method: 'PUT',
        body: JSON.stringify({
          title: 'Atlas',
          status: 'in_progress',
          tasks: [
            {
              id: '10008',
              title: 'Refactor parser',
              branch: 'task-10008-refactor-parser',
              status: 'ready',
              headSha: 'a'.repeat(40),
            },
          ],
        }),
      })
    );
    expect(put.status).toBe(201);
    const rec = (await put.json()) as { etag: string; id: string };
    expect(rec.id).toBe('atlas');
    expect(rec.etag).toBeTruthy();

    const get = await planDo.fetch(
      new Request('https://plan/plans/atlas')
    );
    expect(get.status).toBe(200);
    const body = (await get.json()) as { title: string };
    expect(body.title).toBe('Atlas');
  });

  it('CAS refuses mismatched If-Match', async () => {
    await planDo.fetch(
      new Request('https://plan/plans/atlas', {
        method: 'PUT',
        body: JSON.stringify({ title: 'Atlas', status: 'ready' }),
      })
    );
    const conflict = await planDo.fetch(
      new Request('https://plan/plans/atlas', {
        method: 'PUT',
        headers: { 'if-match': '"deadbeef"' },
        body: JSON.stringify({ title: 'Atlas2', status: 'ready' }),
      })
    );
    expect(conflict.status).toBe(409);
  });

  it('returns next ready tasks with the work-order identifier they carry', async () => {
    await planDo.fetch(
      new Request('https://plan/plans/atlas', {
        method: 'PUT',
        body: JSON.stringify({
          title: 'Atlas',
          status: 'in_progress',
          tasks: [
            {
              id: '1',
              title: 'A',
              status: 'ready',
              branch: 'task-1-a',
              order: 'order-7731',
            },
            {
              id: '2',
              title: 'B',
              status: 'done',
              branch: 'task-2-b',
              order: 'order-7732',
            },
          ],
        }),
      })
    );
    const res = await planDo.fetch(
      new Request('https://plan/tasks/next')
    );
    const body = (await res.json()) as Array<{
      id: string;
      order: string;
      status: string;
    }>;
    expect(body.map((t) => t.id)).toEqual(['1']);
    // Contract section 3.6: the order is reported as held.
    expect(body[0].order).toBe('order-7731');
    expect(body[0].status).toBe('ready');
  });

  it('omits a ready task whose work-order identifier is missing or empty', async () => {
    await planDo.fetch(
      new Request('https://plan/plans/atlas', {
        method: 'PUT',
        body: JSON.stringify({
          title: 'Atlas',
          status: 'in_progress',
          tasks: [
            {
              id: 'no-order',
              title: 'No order',
              status: 'ready',
              branch: 'task-no-order',
            },
            {
              id: 'empty-order',
              title: 'Empty order',
              status: 'ready',
              branch: 'task-empty-order',
              order: '',
            },
            {
              id: 'unassigned-no-order',
              title: 'Unassigned, no order',
              status: 'unassigned',
              branch: 'task-unassigned',
            },
            {
              id: 'with-order',
              title: 'With order',
              status: 'ready',
              branch: 'task-with-order',
              order: 'order-7731',
            },
          ],
        }),
      })
    );
    const res = await planDo.fetch(
      new Request('https://plan/tasks/next')
    );
    const body = (await res.json()) as Array<{ id: string; order: string }>;
    expect(body.map((t) => t.id)).toEqual(['with-order']);
    expect(body[0].order).toBe('order-7731');
  });

  it('agrees with the in-memory plan client on what is listed', async () => {
    const tasks = [
      {
        id: '1',
        title: 'A',
        status: 'ready',
        branch: 'task-1-a',
        order: 'order-7731',
      },
      {
        id: '2',
        title: 'B',
        status: 'unassigned',
        branch: 'task-2-b',
        order: 'order-7732',
      },
      {
        id: '3',
        title: 'C',
        status: 'ready',
        branch: 'task-3-c',
      },
      {
        id: '4',
        title: 'D',
        status: 'ready',
        branch: 'task-4-d',
        order: '',
      },
      {
        id: '5',
        title: 'E',
        status: 'done',
        branch: 'task-5-e',
        order: 'order-7735',
      },
    ];

    // Durable Object path
    await planDo.fetch(
      new Request('https://plan/plans/atlas', {
        method: 'PUT',
        body: JSON.stringify({
          title: 'Atlas',
          status: 'in_progress',
          tasks,
        }),
      })
    );
    const doRes = await planDo.fetch(
      new Request('https://plan/tasks/next')
    );
    const doBody = await doRes.json();

    // In-memory path (the fallback used by tests and single-isolate runs)
    resetPlanMemory();
    const env = {} as Env;
    await putPlanRecord(env, 'atlas', {
      title: 'Atlas',
      status: 'in_progress',
      tasks,
    } as never);
    const memoryBody = await nextTasks(env);

    expect(doBody).toEqual(memoryBody);
    expect((doBody as Array<{ id: string }>).map((t) => t.id)).toEqual([
      '1',
      '2',
    ]);
  });

  it('304 when If-None-Match matches etag', async () => {
    const put = await planDo.fetch(
      new Request('https://plan/plans/atlas', {
        method: 'PUT',
        body: JSON.stringify({ title: 'Atlas', status: 'ready' }),
      })
    );
    const etag = put.headers.get('etag')!;
    const res = await planDo.fetch(
      new Request('https://plan/plans/atlas', {
        headers: { 'if-none-match': etag },
      })
    );
    expect(res.status).toBe(304);
  });
});
