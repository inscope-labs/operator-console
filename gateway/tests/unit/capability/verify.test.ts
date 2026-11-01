/**
 * Capability verification unit tests.
 * Uses Web Crypto (ECDSA P-256) to exercise the real verify path.
 */

import { describe, it, expect, beforeAll } from 'vitest';
import {
  verifyCapability,
  bytesToBase64Url,
  type VerifyContext,
} from '../../../src/capability/verify';
import { encodeCapability } from '../../../src/capability/codec';
import {
  CAPABILITY_VERSION,
  MAX_TTL_SECONDS,
  type OperatorCapability,
} from '../../../src/capability/types';

const NOW = 1_700_000_000;

function baseCap(overrides: Partial<OperatorCapability> = {}): OperatorCapability {
  return {
    version: CAPABILITY_VERSION,
    kid: 'test_device',
    exp: NOW + 120,
    order: 'ord-1',
    plan: 'plan-a',
    task: 'task-7',
    op: 'publish',
    branch: 'task-7',
    head: 'b'.repeat(40),
    nonce: 'nonce-1',
    ...overrides,
  };
}

let publicKey: CryptoKey;
let privateKey: CryptoKey;

beforeAll(async () => {
  const pair = await crypto.subtle.generateKey(
    { name: 'ECDSA', namedCurve: 'P-256' },
    true,
    ['sign', 'verify']
  );
  publicKey = pair.publicKey;
  privateKey = pair.privateKey;
});

async function sign(message: string): Promise<string> {
  const data = new TextEncoder().encode(message);
  const sig = await crypto.subtle.sign(
    { name: 'ECDSA', hash: 'SHA-256' },
    privateKey,
    data
  );
  return bytesToBase64Url(new Uint8Array(sig));
}

async function signedRaw(cap: OperatorCapability): Promise<string> {
  const message = encodeCapability(cap);
  const signature = await sign(message);
  return message + '\n' + signature;
}

function ctx(overrides: Partial<VerifyContext> = {}): VerifyContext {
  return {
    now: NOW,
    resolveKey: async (kid) => (kid === 'test_device' ? publicKey : null),
    ...overrides,
  };
}

describe('verifyCapability — happy path', () => {
  it('accepts a valid ECDSA-signed capability', async () => {
    const raw = await signedRaw(baseCap());
    const result = await verifyCapability(raw, ctx());
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.capability.op).toBe('publish');
      expect(result.capability.branch).toBe('task-7');
      expect(result.signed.signature.length).toBeGreaterThan(0);
    }
  });
});

describe('verifyCapability — TTL', () => {
  it('rejects TTL_TOO_LONG', async () => {
    const cap = baseCap({ exp: NOW + MAX_TTL_SECONDS + 60 });
    const raw = await signedRaw(cap);
    const result = await verifyCapability(raw, ctx());
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.code).toBe('TTL_TOO_LONG');
  });

  it('rejects CAPABILITY_EXPIRED', async () => {
    const cap = baseCap({ exp: NOW - 60 });
    const raw = await signedRaw(cap);
    const result = await verifyCapability(raw, ctx());
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.code).toBe('CAPABILITY_EXPIRED');
  });

  it('accepts TTL at the maximum boundary', async () => {
    const cap = baseCap({ exp: NOW + MAX_TTL_SECONDS });
    const raw = await signedRaw(cap);
    const result = await verifyCapability(raw, ctx());
    expect(result.ok).toBe(true);
  });
});

describe('verifyCapability — kid and signature', () => {
  it('rejects UNKNOWN_KID', async () => {
    const cap = baseCap({ kid: 'unknown_device' });
    const raw = await signedRaw(cap);
    const result = await verifyCapability(raw, ctx());
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.code).toBe('UNKNOWN_KID');
  });

  it('rejects BAD_SIGNATURE when payload is tampered', async () => {
    const raw = await signedRaw(baseCap());
    const tampered = raw.replace('publish', 'upload');
    const result = await verifyCapability(tampered, ctx());
    expect(result.ok).toBe(false);
    if (!result.ok) {
      // Tamper may surface as MALFORMED (field decode) or BAD_SIGNATURE
      expect(['BAD_SIGNATURE', 'MALFORMED', 'UNKNOWN_OP']).toContain(result.code);
    }
  });

  it('rejects BAD_SIGNATURE with wrong key', async () => {
    const other = await crypto.subtle.generateKey(
      { name: 'ECDSA', namedCurve: 'P-256' },
      true,
      ['sign', 'verify']
    );
    const message = encodeCapability(baseCap());
    const sig = await crypto.subtle.sign(
      { name: 'ECDSA', hash: 'SHA-256' },
      other.privateKey,
      new TextEncoder().encode(message)
    );
    const raw = message + '\n' + bytesToBase64Url(new Uint8Array(sig));
    const result = await verifyCapability(raw, ctx());
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.code).toBe('BAD_SIGNATURE');
  });
});

describe('verifyCapability — branch scope', () => {
  it('rejects BRANCH_MISMATCH when expectedBranch differs', async () => {
    const raw = await signedRaw(baseCap());
    const result = await verifyCapability(
      raw,
      ctx({ expectedBranch: 'task-other' })
    );
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.code).toBe('BRANCH_MISMATCH');
  });

  it('rejects BRANCH_INVALID for main at decode time', async () => {
    const cap = baseCap({ branch: 'main' });
    // encode will still produce the message; decode inside verify rejects
    const message = [
      CAPABILITY_VERSION,
      cap.kid,
      String(cap.exp),
      cap.order,
      cap.plan,
      cap.task,
      cap.op,
      'main',
      cap.head,
      cap.nonce,
    ].join('\n');
    const signature = await sign(message);
    const result = await verifyCapability(message + '\n' + signature, ctx());
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.code).toBe('BRANCH_INVALID');
  });
});

describe('verifyCapability — malformed input', () => {
  it('rejects missing signature separator', async () => {
    const result = await verifyCapability('no-separator-here', ctx());
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.code).toBe('MALFORMED');
  });

  it('rejects empty signature', async () => {
    const message = encodeCapability(baseCap());
    const result = await verifyCapability(message + '\n', ctx());
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.code).toBe('MALFORMED');
  });
});

describe('verifyCapability — HMAC Model A compatibility', () => {
  it('accepts HMAC-SHA256 when resolveKey returns ArrayBuffer', async () => {
    const secret = crypto.getRandomValues(new Uint8Array(32));
    const message = encodeCapability(baseCap({ kid: 'hmac_kid' }));
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

    const result = await verifyCapability(raw, {
      now: NOW,
      resolveKey: async (kid) =>
        kid === 'hmac_kid' ? secret.buffer.slice(secret.byteOffset, secret.byteOffset + secret.byteLength) : null,
    });
    expect(result.ok).toBe(true);
  });
});
