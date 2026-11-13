import { describe, it, expect } from 'vitest';
import { isValidIdempotencyKey } from '../../../src/idempotency/key';
import { computeFingerprint } from '../../../src/idempotency/fingerprint';

describe('idempotency key format', () => {
  it('accepts 32 lowercase hex', () => {
    expect(isValidIdempotencyKey('a'.repeat(32))).toBe(true);
    expect(isValidIdempotencyKey('0123456789abcdef'.repeat(2))).toBe(true);
  });
  it('rejects missing, uppercase, wrong length', () => {
    expect(isValidIdempotencyKey(undefined)).toBe(false);
    expect(isValidIdempotencyKey('A'.repeat(32))).toBe(false);
    expect(isValidIdempotencyKey('a'.repeat(31))).toBe(false);
    expect(isValidIdempotencyKey('a'.repeat(33))).toBe(false);
    expect(isValidIdempotencyKey('g'.repeat(32))).toBe(false);
  });
});

describe('fingerprint', () => {
  it('ignores capability and zipBase64; stable for same fields', async () => {
    const a = await computeFingerprint('PUBLISH', {
      idempotencyKey: 'a'.repeat(32),
      branch: 'task-1',
      capability: 'secret-cap',
      zipBase64: 'IGNORED',
      zipSha256: 'b'.repeat(64),
    });
    const b = await computeFingerprint('PUBLISH', {
      zipSha256: 'b'.repeat(64),
      branch: 'task-1',
      idempotencyKey: 'a'.repeat(32),
      capability: 'OTHER',
      zipBase64: 'DIFFERENT',
    });
    expect(a).toBe(b);
  });
});
