import { describe, it, expect } from 'vitest';
import {
  EnrolmentRegistry,
  toBase64Url,
} from '../../../src/durable-objects/enrolment-registry';

/**
 * Minimal in-memory DurableObjectState.storage mock.
 */
function mockState() {
  const map = new Map<string, unknown>();
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
  };
  return {
    state: { storage } as unknown as DurableObjectState,
    map,
  };
}

function reg() {
  const { state, map } = mockState();
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
    const { registry } = reg();
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
  });

  it('consume marks used even when subsequent attestation would fail (first attempt)', async () => {
    const { registry } = reg();
    const challenge = 'chg-fail';
    const now = 1_700_000_000;
    await registry.fetch(
      new Request('https://do/challenge/create', {
        method: 'POST',
        body: JSON.stringify({ challenge, now }),
      })
    );
    // First consume (simulates reaching attestation verification)
    expect(
      (
        await registry.fetch(
          new Request('https://do/challenge/consume', {
            method: 'POST',
            body: JSON.stringify({ challenge, now }),
          })
        )
      ).status
    ).toBe(200);
    // Replay
    expect(
      (
        await registry.fetch(
          new Request('https://do/challenge/consume', {
            method: 'POST',
            body: JSON.stringify({ challenge, now }),
          })
        )
      ).status
    ).toBe(400);
  });
});

describe('EnrolmentRegistry enroll + rotate', () => {
  it('assigns monotonic kids', async () => {
    const { registry } = reg();
    const r1 = await registry.fetch(
      new Request('https://do/enroll', {
        method: 'POST',
        body: JSON.stringify({
          alias: 'device-a',
          publicKey: btoa('key1'),
          authTier: 'hardware_confirm',
          leafCertSha256: 'a'.repeat(64),
          now: 1000,
        }),
      })
    );
    const b1 = (await r1.json()) as { kid: string };
    expect(b1.kid).toBe('k1');

    const r2 = await registry.fetch(
      new Request('https://do/enroll', {
        method: 'POST',
        body: JSON.stringify({
          alias: 'device-b',
          publicKey: btoa('key2'),
          authTier: 'hardware_confirm',
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

    const oldLookup = await registry.fetch(
      new Request(`https://do/lookup/${kid}`)
    );
    const oldRec = (await oldLookup.json()) as {
      record: { status: string };
    };
    expect(oldRec.record.status).toBe('retired');

    const newLookup = await registry.fetch(
      new Request(`https://do/lookup/${rb.kid}`)
    );
    const newRec = (await newLookup.json()) as {
      record: { status: string };
    };
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
});
