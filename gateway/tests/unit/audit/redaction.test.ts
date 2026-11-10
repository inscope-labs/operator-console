import { describe, it, expect } from 'vitest';
import {
  redactString,
  redactRecord,
  FORBIDDEN_FIELD_NAMES,
} from '../../../src/audit/redaction';

describe('redaction gate', () => {
  it('redacts capability wire, github tokens, and PEM headers', () => {
    expect(redactString('ctxop-v1\nk2\n...')).toBe('[REDACTED]');
    expect(redactString('token ghp_' + 'a'.repeat(20))).toBe('[REDACTED]');
    expect(redactString('github_pat_' + 'x'.repeat(20))).toBe('[REDACTED]');
    expect(redactString('-----BEGIN PRIVATE KEY-----\nabc')).toBe('[REDACTED]');
  });

  it('redacts long base64url tokens', () => {
    expect(redactString('A'.repeat(40))).toBe('[REDACTED_TOKEN]');
  });

  it('leaves ordinary paths alone', () => {
    expect(redactString('src/app.ts')).toBe('src/app.ts');
  });

  it('deep-redacts records', () => {
    const out = redactRecord({
      note: 'has ghp_' + 'b'.repeat(20),
      nested: { sig: 'ctxop-v1' },
    });
    expect(out.note).toBe('[REDACTED]');
    expect((out.nested as { sig: string }).sig).toBe('[REDACTED]');
  });

  it('lists forbidden field names for emit strip', () => {
    expect(FORBIDDEN_FIELD_NAMES).toContain('nonce');
    expect(FORBIDDEN_FIELD_NAMES).toContain('signature');
  });
});
