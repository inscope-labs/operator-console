import { describe, it, expect } from 'vitest';
import {
  EnrolmentRegistry,
  toBase64Url,
  type EnrolmentRecord,
} from '../../../src/durable-objects/enrolment-registry';

/**
 * Minimal in-memory DurableObjectState.storage mock with serialized transactions.
 */
function mockState(existing?: Map<string, unknown>) {
  const map = existing ?? new Map<string, unknown>();
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
  };

  return {
    state: { storage } as unknown as DurableObjectState,
    map,
  };
}

function reg(existing?: Map<string, unknown>) {
  const { state, map } = mockState(existing);
  return { registry: new EnrolmentRegistry(state), map };
}

describe('toBase64Url', () => {
  it('normalises standard base64', () => {
    const std = btoa('hello+world/=');
    const out = toBase64Url(std);
    expect(out).not.toMatch(/[+/=]/);
  });
});

describe('EnrolmentRegistry challenge lifecycle', () => {
  it('creates and consumes a challenge once', async () => {
    const { registry, map } = reg();
    const challenge = 'chg-' + crypto.randomUUID();
    const now = 1_700_000_000;
    let res = await registry.fetch(
      new Request('https://do/challenge/create', {
        method: 'POST',
        body: JSON.stringify({ challenge, now }),
      })
    );
    expect(res.status).toBe(200);

    res = await registry.fetch(
      new Request('https://do/challenge/consume', {
        method: 'POST',
        body: JSON.stringify({ challenge, now: now + 10 }),
      })
    );
    expect(res.status).toBe(200);
    // Challenge bytes not retained after consume.
    expect(map.has(`challenge:${challenge}`)).toBe(false);

    // second consume fails
    res = await registry.fetch(
      new Request('https://do/challenge/consume', {
        method: 'POST',
        body: JSON.stringify({ challenge, now: now + 20 }),
      })
    );
    expect(res.status).toBe(400);
    const body = (await res.json()) as { error: string };
    expect(body.error).toBe('ENROLL_CHALLENGE_INVALID');
  });

  it('rejects expired challenge (TTL 300s)', async () => {
    const { registry } = reg();
    const challenge = 'chg-exp';
    const now = 1_700_000_000;
    await registry.fetch(
      new Request('https://do/challenge/create', {
        method: 'POST',
        body: JSON.stringify({ challenge, now }),
      })
    );
    const res = await registry.fetch(
      new Request('https://do/challenge/consume', {
        method: 'POST',
        body: JSON.stringify({ challenge, now: now + 301 }),
      })
    );
    expect(res.status).toBe(400);
    const body = (await res.json()) as { error: string };
    expect(body.error).toBe('ENROLL_CHALLENGE_INVALID');
  });

  it('concurrent consume of the same challenge yields one success and one ENROLL_CHALLENGE_INVALID', async () => {
    const { registry } = reg();
    const challenge = 'chg-race';
    const now = 1_700_000_000;
    await registry.fetch(
      new Request('https://do/challenge/create', {
        method: 'POST',
        body: JSON.stringify({ challenge, now }),
      })
    );

    const body = JSON.stringify({ challenge, now: now + 1 });
    const [a, b] = await Promise.all([
      registry.fetch(
        new Request('https://do/challenge/consume', { method: 'POST', body })
      ),
      registry.fetch(
        new Request('https://do/challenge/consume', { method: 'POST', body })
      ),
    ]);
    const statuses = [a.status, b.status].sort();
    expect(statuses).toEqual([200, 400]);
    const failed = a.status === 400 ? a : b;
    const err = (await failed.json()) as { error: string };
    expect(err.error).toBe('ENROLL_CHALLENGE_INVALID');
  });
});

describe('EnrolmentRegistry enroll + rotate', () => {
  it('enrolls sequential kids', async () => {
    const { registry } = reg();
    const r1 = await registry.fetch(
      new Request('https://do/enroll', {
        method: 'POST',
        body: JSON.stringify({
          alias: 'device-a',
          publicKey: btoa('pk1'),
          authTier: 'hardware_confirm',
          leafCertSha256: 'a'.repeat(64),
          now: 1000,
        }),
      })
    );
    expect(r1.status).toBe(200);
    const b1 = (await r1.json()) as { kid: string };
    expect(b1.kid).toBe('k1');

    const r2 = await registry.fetch(
      new Request('https://do/enroll', {
        method: 'POST',
        body: JSON.stringify({
          alias: 'device-b',
          publicKey: btoa('pk2'),
          authTier: 'in_app_confirm',
          leafCertSha256: 'b'.repeat(64),
          now: 1001,
        }),
      })
    );
    const b2 = (await r2.json()) as { kid: string };
    expect(b2.kid).toBe('k2');
  });

  it('rotate retires old and activates new', async () => {
    const { registry } = reg();
    const en = await registry.fetch(
      new Request('https://do/enroll', {
        method: 'POST',
        body: JSON.stringify({
          alias: 'device-a',
          publicKey: btoa('old'),
          authTier: 'hardware_confirm',
          leafCertSha256: 'a'.repeat(64),
          now: 1000,
        }),
      })
    );
    const { kid } = (await en.json()) as { kid: string };

    const rot = await registry.fetch(
      new Request('https://do/rotate', {
        method: 'POST',
        body: JSON.stringify({
          oldKid: kid,
          publicKey: btoa('new'),
          authTier: 'hardware_confirm',
          leafCertSha256: 'c'.repeat(64),
          now: 2000,
        }),
      })
    );
    expect(rot.status).toBe(200);
    const rb = (await rot.json()) as { kid: string; retiredKid: string };
    expect(rb.retiredKid).toBe(kid);
    expect(rb.kid).toBe('k2');

    const oldLookup = await registry.fetch(new Request(`https://do/lookup/${kid}`));
    const oldRec = (await oldLookup.json()) as { record: { status: string } };
    expect(oldRec.record.status).toBe('retired');

    const newLookup = await registry.fetch(
      new Request(`https://do/lookup/${rb.kid}`)
    );
    const newRec = (await newLookup.json()) as { record: { status: string } };
    expect(newRec.record.status).toBe('active');
  });

  it('rotate failure leaves old kid active (missing fields)', async () => {
    const { registry } = reg();
    const en = await registry.fetch(
      new Request('https://do/enroll', {
        method: 'POST',
        body: JSON.stringify({
          alias: 'device-a',
          publicKey: btoa('old'),
          authTier: 'hardware_confirm',
          leafCertSha256: 'a'.repeat(64),
          now: 1000,
        }),
      })
    );
    const { kid } = (await en.json()) as { kid: string };

    const bad = await registry.fetch(
      new Request('https://do/rotate', {
        method: 'POST',
        body: JSON.stringify({ oldKid: kid }),
      })
    );
    expect(bad.status).toBe(400);

    const lookup = await registry.fetch(new Request(`https://do/lookup/${kid}`));
    const rec = (await lookup.json()) as { record: { status: string } };
    expect(rec.record.status).toBe('active');
  });

  it('listActive returns only active enrolments', async () => {
    const { registry } = reg();
    await registry.fetch(
      new Request('https://do/enroll', {
        method: 'POST',
        body: JSON.stringify({
          alias: 'a',
          publicKey: btoa('1'),
          authTier: 'hardware_confirm',
          leafCertSha256: 'a'.repeat(64),
          now: 1,
        }),
      })
    );
    const en = await registry.fetch(
      new Request('https://do/enroll', {
        method: 'POST',
        body: JSON.stringify({
          alias: 'b',
          publicKey: btoa('2'),
          authTier: 'hardware_confirm',
          leafCertSha256: 'b'.repeat(64),
          now: 2,
        }),
      })
    );
    const { kid } = (await en.json()) as { kid: string };
    await registry.fetch(
      new Request('https://do/rotate', {
        method: 'POST',
        body: JSON.stringify({
          oldKid: kid,
          publicKey: btoa('3'),
          authTier: 'hardware_confirm',
          leafCertSha256: 'c'.repeat(64),
          now: 3,
        }),
      })
    );

    const list = await registry.fetch(new Request('https://do/active'));
    const body = (await list.json()) as { records: EnrolmentRecord[] };
    expect(body.records.every((r) => r.status === 'active')).toBe(true);
    expect(body.records.length).toBe(2); // k1 still active, k2 retired, k3 active
  });
});

describe('EnrolmentRegistry restart survival (DO storage semantics)', () => {
  it('new registry instance with the same storage map still sees enrolments', async () => {
    const { registry: first, map } = reg();
    const en = await first.fetch(
      new Request('https://do/enroll', {
        method: 'POST',
        body: JSON.stringify({
          alias: 'persist-me',
          publicKey: btoa('pk'),
          authTier: 'hardware_confirm',
          leafCertSha256: 'd'.repeat(64),
          now: 5000,
        }),
      })
    );
    const { kid } = (await en.json()) as { kid: string };
    expect(kid).toBe('k1');

    // Simulate Worker restart: new DO class instance, same durable storage.
    const { registry: second } = reg(map);
    const lookup = await second.fetch(new Request(`https://do/lookup/${kid}`));
    expect(lookup.status).toBe(200);
    const body = (await lookup.json()) as {
      record: EnrolmentRecord;
    };
    expect(body.record.kid).toBe(kid);
    expect(body.record.alias).toBe('persist-me');
    expect(body.record.status).toBe('active');

    // Kid sequence also survives.
    const en2 = await second.fetch(
      new Request('https://do/enroll', {
        method: 'POST',
        body: JSON.stringify({
          alias: 'after-restart',
          publicKey: btoa('pk2'),
          authTier: 'in_app_confirm',
          leafCertSha256: 'e'.repeat(64),
          now: 5001,
        }),
      })
    );
    const b2 = (await en2.json()) as { kid: string };
    expect(b2.kid).toBe('k2');
  });
});
