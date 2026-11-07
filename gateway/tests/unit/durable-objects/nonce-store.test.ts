import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { NonceStore, type NonceRecord } from '../../../src/durable-objects/nonce-store';

/**
 * In-memory Durable Object storage mock with optional transaction + alarm.
 */
function mockState() {
  const map = new Map<string, unknown>();
  let alarmAt: number | null = null;
  // Serialize transactions to mirror Durable Object single-threading under concurrency.
  let txnTail: Promise<void> = Promise.resolve();

  const storage = {
    async get<T>(key: string): Promise<T | undefined> {
      return map.get(key) as T | undefined;
    },
    async put(key: string, value: unknown): Promise<void> {
      map.set(key, value);
    },
    async delete(key: string): Promise<boolean> {
      return map.delete(key);
    },
    async list<T>(opts?: { prefix?: string }): Promise<Map<string, T>> {
      const out = new Map<string, T>();
      for (const [k, v] of map) {
        if (!opts?.prefix || k.startsWith(opts.prefix)) out.set(k, v as T);
      }
      return out;
    },
    async transaction<T>(
      closure: (txn: {
        get: <U>(key: string) => Promise<U | undefined>;
        put: (key: string, value: unknown) => Promise<void>;
        delete: (key: string) => Promise<boolean>;
      }) => Promise<T>
    ): Promise<T> {
      const run = txnTail.then(() =>
        closure({
          get: storage.get,
          put: storage.put,
          delete: storage.delete,
        })
      );
      txnTail = run.then(
        () => undefined,
        () => undefined
      );
      return run;
    },
    async setAlarm(at: number): Promise<void> {
      alarmAt = at;
    },
    async getAlarm(): Promise<number | null> {
      return alarmAt;
    },
    async deleteAlarm(): Promise<void> {
      alarmAt = null;
    },
  };

  return {
    state: { storage } as unknown as DurableObjectState,
    map,
    getAlarm: () => alarmAt,
  };
}

function store() {
  const ctx = mockState();
  return { store: new NonceStore(ctx.state), map: ctx.map, getAlarm: ctx.getAlarm };
}

function checkBody(nonce: string, exp: number, extra: Record<string, string> = {}) {
  return JSON.stringify({ nonce, exp, kid: 'k1', op: 'publish', ...extra });
}

describe('NonceStore check-and-store', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-11-07T12:00:00Z'));
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('stores a fresh nonce and returns 200', async () => {
    const { store: ns, map } = store();
    const exp = Math.floor(Date.now() / 1000) + 120;
    const res = await ns.fetch(
      new Request('https://do/check-and-store', {
        method: 'POST',
        body: checkBody('n-1', exp),
      })
    );
    expect(res.status).toBe(200);
    const rec = map.get('nonce:n-1') as NonceRecord;
    expect(rec.nonce).toBe('n-1');
    expect(rec.exp).toBe(exp);
    expect(rec.kid).toBe('k1');
  });

  it('returns 409 NONCE_REPLAYED on second use of the same nonce', async () => {
    const { store: ns } = store();
    const exp = Math.floor(Date.now() / 1000) + 120;
    const body = checkBody('n-replay', exp);

    const first = await ns.fetch(
      new Request('https://do/check-and-store', { method: 'POST', body })
    );
    expect(first.status).toBe(200);

    const second = await ns.fetch(
      new Request('https://do/check-and-store', { method: 'POST', body })
    );
    expect(second.status).toBe(409);
    const json = (await second.json()) as { error: string };
    expect(json.error).toBe('NONCE_REPLAYED');
  });

  it('concurrent identical nonces: exactly one success and one NONCE_REPLAYED', async () => {
    const { store: ns } = store();
    const exp = Math.floor(Date.now() / 1000) + 120;
    const body = checkBody('n-race', exp);

    // Durable Objects serialize requests per instance; simulate concurrent
    // callers as two overlapping awaits that still hit the transactional path.
    const [a, b] = await Promise.all([
      ns.fetch(new Request('https://do/check-and-store', { method: 'POST', body })),
      ns.fetch(new Request('https://do/check-and-store', { method: 'POST', body })),
    ]);

    const statuses = [a.status, b.status].sort();
    expect(statuses).toEqual([200, 409]);
    const errors = await Promise.all(
      [a, b].map(async (r) =>
        r.status === 409 ? ((await r.json()) as { error: string }).error : null
      )
    );
    expect(errors.filter((e) => e === 'NONCE_REPLAYED')).toHaveLength(1);
  });

  it('rejects already-expired nonce without storing', async () => {
    const { store: ns, map } = store();
    const exp = Math.floor(Date.now() / 1000) - 10;
    const res = await ns.fetch(
      new Request('https://do/check-and-store', {
        method: 'POST',
        body: checkBody('n-old', exp),
      })
    );
    expect(res.status).toBe(401);
    const json = (await res.json()) as { error: string };
    expect(json.error).toBe('CAPABILITY_EXPIRED');
    expect(map.has('nonce:n-old')).toBe(false);
  });

  it('schedules an alarm when a nonce is stored', async () => {
    const { store: ns, getAlarm } = store();
    const exp = Math.floor(Date.now() / 1000) + 300;
    await ns.fetch(
      new Request('https://do/check-and-store', {
        method: 'POST',
        body: checkBody('n-alarm', exp),
      })
    );
    const alarm = getAlarm();
    expect(alarm).not.toBeNull();
    expect(alarm!).toBeGreaterThanOrEqual(Date.now());
    expect(alarm!).toBeLessThanOrEqual(exp * 1000 + 1000);
  });
});

describe('NonceStore cleanup alarm', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-11-07T12:00:00Z'));
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('purge-expired removes expired nonces and leaves live ones', async () => {
    const { store: ns, map } = store();
    const now = Math.floor(Date.now() / 1000);

    // Insert two records directly to control expiry without check-and-store clock.
    map.set('nonce:dead', {
      nonce: 'dead',
      exp: now - 5,
      kid: 'k1',
      op: 'publish',
      storedAt: now - 100,
    } satisfies NonceRecord);
    map.set('nonce:live', {
      nonce: 'live',
      exp: now + 600,
      kid: 'k1',
      op: 'publish',
      storedAt: now,
    } satisfies NonceRecord);

    const res = await ns.fetch(
      new Request('https://do/purge-expired', { method: 'POST' })
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as { purged: number };
    expect(body.purged).toBe(1);
    expect(map.has('nonce:dead')).toBe(false);
    expect(map.has('nonce:live')).toBe(true);
  });

  it('alarm handler purges expired records and does not accumulate unbounded', async () => {
    const { store: ns, map, getAlarm } = store();
    const now = Math.floor(Date.now() / 1000);

    for (let i = 0; i < 20; i++) {
      map.set(`nonce:exp-${i}`, {
        nonce: `exp-${i}`,
        exp: now - 1 - i,
        kid: 'k1',
        op: 'publish',
        storedAt: now - 100,
      } satisfies NonceRecord);
    }
    map.set('nonce:keep', {
      nonce: 'keep',
      exp: now + 900,
      kid: 'k1',
      op: 'publish',
      storedAt: now,
    } satisfies NonceRecord);

    await ns.alarm();

    expect(map.size).toBe(1);
    expect(map.has('nonce:keep')).toBe(true);
    // Alarm rescheduled for the remaining live nonce.
    expect(getAlarm()).not.toBeNull();
  });

  it('stats reports live vs expired counts', async () => {
    const { store: ns, map } = store();
    const now = Math.floor(Date.now() / 1000);
    map.set('nonce:a', {
      nonce: 'a',
      exp: now - 1,
      kid: 'k',
      op: 'publish',
      storedAt: now,
    } satisfies NonceRecord);
    map.set('nonce:b', {
      nonce: 'b',
      exp: now + 10,
      kid: 'k',
      op: 'publish',
      storedAt: now,
    } satisfies NonceRecord);

    const res = await ns.fetch(new Request('https://do/stats', { method: 'GET' }));
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      live: number;
      expired: number;
      total: number;
    };
    expect(body.live).toBe(1);
    expect(body.expired).toBe(1);
    expect(body.total).toBe(2);
  });
});
