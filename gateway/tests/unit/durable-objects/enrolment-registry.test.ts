import { describe, it, expect } from 'vitest';
import { toBase64Url } from '../../../src/durable-objects/enrolment-registry';

describe('toBase64Url', () => {
  it('normalises standard base64 to base64url', () => {
    const std = btoa('hello+world/=');
    const out = toBase64Url(std);
    expect(out).not.toContain('+');
    expect(out).not.toContain('/');
    expect(out).not.toContain('=');
  });

  it('is idempotent on base64url', () => {
    const u = 'aGVsbG8';
    expect(toBase64Url(u)).toBe(u);
  });
});
