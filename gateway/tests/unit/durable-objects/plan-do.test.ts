import { describe, it, expect, beforeEach } from 'vitest';
import { PlanDO } from '../../../src/durable-objects/plan-do';

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

  it('returns next ready tasks', async () => {
    await planDo.fetch(
      new Request('https://plan/plans/atlas', {
        method: 'PUT',
        body: JSON.stringify({
          title: 'Atlas',
          status: 'in_progress',
          tasks: [
            { id: '1', title: 'A', status: 'ready', branch: 'task-1-a' },
            { id: '2', title: 'B', status: 'done', branch: 'task-2-b' },
          ],
        }),
      })
    );
    const res = await planDo.fetch(
      new Request('https://plan/tasks/next')
    );
    const body = (await res.json()) as Array<{ id: string }>;
    expect(body.map((t) => t.id)).toEqual(['1']);
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
