import { describe, it, expect } from 'vitest';
import {
  validateUploadPayload,
  submissionObjectPath,
} from '../../../src/routes/upload-validate';
import { MAX_UPLOAD_BYTES } from '../../../src/config/constants';

async function sha256Hex(bytes: Uint8Array): Promise<string> {
  const dig = await crypto.subtle.digest('SHA-256', bytes);
  return Array.from(new Uint8Array(dig))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

function toB64(bytes: Uint8Array): string {
  let s = '';
  for (let i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
  return btoa(s);
}

describe('validateUploadPayload', () => {
  it('accepts matching zipSha256', async () => {
    const bytes = new TextEncoder().encode('PK\x03\x04fake-zip');
    const hex = await sha256Hex(bytes);
    const r = await validateUploadPayload({
      zipBase64: toB64(bytes),
      zipSha256: hex,
    });
    expect(r.ok).toBe(true);
    if (r.ok) expect(r.sha256Hex).toBe(hex);
  });

  it('refuses mismatched SHA', async () => {
    const bytes = new TextEncoder().encode('hello');
    const r = await validateUploadPayload({
      zipBase64: toB64(bytes),
      zipSha256: 'a'.repeat(64),
    });
    expect(r.ok).toBe(false);
    if (!r.ok) {
      expect(r.code).toBe('BAD_ENCODING');
      expect(r.message).toMatch(/does not match/);
    }
  });

  it('refuses oversized payload', async () => {
    const bytes = new Uint8Array(100);
    const hex = await sha256Hex(bytes);
    const r = await validateUploadPayload({
      zipBase64: toB64(bytes),
      zipSha256: hex,
      maxBytes: 10,
    });
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.message).toMatch(/exceeds maximum/);
  });

  it('refuses invalid sha format', async () => {
    const r = await validateUploadPayload({
      zipBase64: toB64(new Uint8Array([1])),
      zipSha256: 'not-hex',
    });
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.code).toBe('BAD_ENCODING');
  });

  it('MAX_UPLOAD_BYTES is positive', () => {
    expect(MAX_UPLOAD_BYTES).toBeGreaterThan(1024);
  });
});

describe('submissionObjectPath', () => {
  it('nests under submissions/ and sanitises id', () => {
    expect(submissionObjectPath('sub-7731')).toBe('submissions/sub-7731.zip');
    expect(submissionObjectPath('../evil')).toContain('submissions/');
    expect(submissionObjectPath('../evil')).not.toContain('..');
  });
});
