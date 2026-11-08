/**
 * Stage 2.4 — every capability-section catalogue code exercised.
 * Clock-skew tests use injectable `ctx.now`.
 */
import { describe, it, expect, beforeAll } from 'vitest';
import {
  verifyCapability,
  bytesToBase64Url,
  type VerifyContext,
} from '../../../src/capability/verify';
import { encodeCapability, decodeCapability } from '../../../src/capability/codec';
import {
  CAPABILITY_VERSION,
  MAX_TTL_SECONDS,
  CLOCK_SKEW_SECONDS,
  type OperatorCapability,
} from '../../../src/capability/types';
import { checkAndStoreNonce } from '../../../src/capability/nonce';
import { g13ExactSha } from '../../../src/guards/g13-exact-sha';

const NOW = 1_700_000_000;

let ecdsaKeyPair: CryptoKeyPair;

beforeAll(async () => {
  ecdsaKeyPair = (await crypto.subtle.generateKey(
    { name: 'ECDSA', namedCurve: 'P-256' },
    true,
    ['sign', 'verify']
  )) as CryptoKeyPair;
});

function baseCap(over: Partial<OperatorCapability> = {}): OperatorCapability {
  return {
    version: CAPABILITY_VERSION,
    kid: 'k1',
    exp: NOW + 120,
    order: 'o1',
    plan: 'atlas',
    task: '7',
    op: 'publish',
    branch: 'task-7-work',
    head: 'a'.repeat(40),
    nonce: 'nonce-' + Math.random().toString(16).slice(2),
    ...over,
  };
}

async function sign(message: string): Promise<string> {
  const sig = await crypto.subtle.sign(
    { name: 'ECDSA', hash: 'SHA-256' },
    ecdsaKeyPair.privateKey,
    new TextEncoder().encode(message)
  );
  // WebCrypto may produce IEEE P1363 or DER depending on runtime; verify accepts both paths.
  return bytesToBase64Url(new Uint8Array(sig));
}

async function signedRaw(cap: OperatorCapability): Promise<string> {
  const message = encodeCapability(cap);
  return message + '\n' + (await sign(message));
}

function ctx(over: Partial<VerifyContext> = {}): VerifyContext {
  return {
    now: NOW,
    resolveKey: async (kid) => (kid === 'k1' ? ecdsaKeyPair.publicKey : null),
    ...over,
  };
}

describe('Stage 2.4 capability catalogue coverage', () => {
  it('UNKNOWN_KID', async () => {
    const raw = await signedRaw(baseCap({ kid: 'nope' }));
    const r = await verifyCapability(raw, ctx());
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.code).toBe('UNKNOWN_KID');
  });

  it('BAD_SIGNATURE', async () => {
    const message = encodeCapability(baseCap());
    const raw = message + '\n' + bytesToBase64Url(new Uint8Array(64).fill(7));
    const r = await verifyCapability(raw, ctx());
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.code).toBe('BAD_SIGNATURE');
  });

  it('TTL_TOO_LONG beyond max + skew', async () => {
    const raw = await signedRaw(
      baseCap({ exp: NOW + MAX_TTL_SECONDS + CLOCK_SKEW_SECONDS + 1 })
    );
    const r = await verifyCapability(raw, ctx({ now: NOW }));
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.code).toBe('TTL_TOO_LONG');
  });

  it('CAPABILITY_EXPIRED beyond skew', async () => {
    const raw = await signedRaw(baseCap({ exp: NOW - CLOCK_SKEW_SECONDS - 1 }));
    const r = await verifyCapability(raw, ctx({ now: NOW }));
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.code).toBe('CAPABILITY_EXPIRED');
  });

  it('UNKNOWN_OP', async () => {
    const lines = encodeCapability(baseCap()).split('\n');
    lines[6] = 'not_an_op';
    const message = lines.join('\n');
    const raw = message + '\n' + (await sign(message));
    const r = await verifyCapability(raw, ctx());
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.code).toBe('UNKNOWN_OP');
  });

  it('BRANCH_MISMATCH', async () => {
    const raw = await signedRaw(baseCap());
    const r = await verifyCapability(raw, ctx({ expectedBranch: 'task-other' }));
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.code).toBe('BRANCH_MISMATCH');
  });

  it('BRANCH_INVALID (wrong branch pattern)', async () => {
    const lines = [
      CAPABILITY_VERSION,
      'k1',
      String(NOW + 120),
      'o',
      'p',
      't',
      'publish',
      'main',
      'a'.repeat(40),
      'n1',
    ];
    const message = lines.join('\n');
    const raw = message + '\n' + (await sign(message));
    const r = await verifyCapability(raw, ctx());
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.code).toBe('BRANCH_INVALID');
  });

  it('BAD_FIELD_COUNT', async () => {
    const message = 'ctxop-v1\nk1\n' + String(NOW + 120);
    const raw = message + '\n' + (await sign(message));
    const r = await verifyCapability(raw, ctx());
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.code).toBe('BAD_FIELD_COUNT');
  });

  it('BAD_ENCODING (CR in wire form)', async () => {
    const message = encodeCapability(baseCap()).replace('\n', '\r\n');
    const raw = message + '\n' + (await sign(message.replace(/\r/g, '')));
    const r = await verifyCapability(raw, ctx());
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.code).toBe('BAD_ENCODING');
  });

  it('HEAD_MOVED is enforced by G13 (capability section code)', async () => {
    const r = await g13ExactSha({
      capability: baseCap({ head: 'a'.repeat(40) }),
      branchMeta: { exists: true, head: 'b'.repeat(40) },
    });
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.code).toBe('HEAD_MOVED');
  });

  it('NONCE_REPLAYED is enforced by nonce store (capability section code)', async () => {
    const env = {
      NONCE_STORE: {
        idFromName: () => ({ toString: () => 'id' }),
        get: () => ({
          fetch: async () =>
            new Response(
              JSON.stringify({ error: 'NONCE_REPLAYED', message: 'used' }),
              { status: 409 }
            ),
        }),
      },
    } as unknown as import('../../../src/runtime').Env;
    const r = await checkAndStoreNonce(env, baseCap());
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.code).toBe('NONCE_REPLAYED');
  });
});

describe('Stage 2.4 clock skew (injectable now)', () => {
  it('accepts exp at now - CLOCK_SKEW_SECONDS (inclusive boundary)', async () => {
    const raw = await signedRaw(baseCap({ exp: NOW - CLOCK_SKEW_SECONDS }));
    const r = await verifyCapability(raw, ctx({ now: NOW }));
    expect(r.ok).toBe(true);
  });

  it('rejects exp just beyond skew window', async () => {
    const raw = await signedRaw(baseCap({ exp: NOW - CLOCK_SKEW_SECONDS - 1 }));
    const r = await verifyCapability(raw, ctx({ now: NOW }));
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.code).toBe('CAPABILITY_EXPIRED');
  });

  it('accepts TTL at max + CLOCK_SKEW_SECONDS', async () => {
    const raw = await signedRaw(
      baseCap({ exp: NOW + MAX_TTL_SECONDS + CLOCK_SKEW_SECONDS })
    );
    const r = await verifyCapability(raw, ctx({ now: NOW }));
    expect(r.ok).toBe(true);
  });

  it('rejects TTL beyond max + CLOCK_SKEW_SECONDS', async () => {
    const raw = await signedRaw(
      baseCap({ exp: NOW + MAX_TTL_SECONDS + CLOCK_SKEW_SECONDS + 1 })
    );
    const r = await verifyCapability(raw, ctx({ now: NOW }));
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.code).toBe('TTL_TOO_LONG');
  });
});

describe('Stage 2.4 Model B default (Model A disabled)', () => {
  it('refuses HMAC ArrayBuffer keys unless allowModelA is true', async () => {
    const secret = crypto.getRandomValues(new Uint8Array(32));
    const message = encodeCapability(baseCap({ kid: 'hmac1' }));
    const hmacKey = await crypto.subtle.importKey(
      'raw',
      secret,
      { name: 'HMAC', hash: 'SHA-256' },
      false,
      ['sign']
    );
    const sig = await crypto.subtle.sign(
      'HMAC',
      hmacKey,
      new TextEncoder().encode(message)
    );
    const raw = message + '\n' + bytesToBase64Url(new Uint8Array(sig));

    const denied = await verifyCapability(raw, {
      now: NOW,
      resolveKey: async () =>
        secret.buffer.slice(secret.byteOffset, secret.byteOffset + secret.byteLength),
      allowModelA: false,
    });
    expect(denied.ok).toBe(false);
    if (!denied.ok) expect(denied.code).toBe('BAD_SIGNATURE');

    const allowed = await verifyCapability(raw, {
      now: NOW,
      resolveKey: async () =>
        secret.buffer.slice(secret.byteOffset, secret.byteOffset + secret.byteLength),
      allowModelA: true,
    });
    expect(allowed.ok).toBe(true);
  });
});

describe('decodeCapability structured errors', () => {
  it('throws BAD_FIELD_COUNT on wrong line count', () => {
    expect(() => decodeCapability('ctxop-v1\nk1')).toThrow(/BAD_FIELD_COUNT/);
  });
});
