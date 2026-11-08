/**
 * Upload payload validation — Stage 3.3.
 * Size limit + SHA-256 integrity. Uses frozen catalogue codes only.
 */

import { MAX_UPLOAD_BYTES } from '../config/constants';
import type { ErrorCode } from '../errors/catalogue';

export interface UploadPayloadOk {
  ok: true;
  bytes: Uint8Array;
  sha256Hex: string;
}

export interface UploadPayloadErr {
  ok: false;
  code: ErrorCode;
  message: string;
}

export type UploadPayloadResult = UploadPayloadOk | UploadPayloadErr;

const SHA256_HEX = /^[0-9a-f]{64}$/i;

function bytesToHex(buf: ArrayBuffer): string {
  const u = new Uint8Array(buf);
  let s = '';
  for (let i = 0; i < u.length; i++) s += u[i].toString(16).padStart(2, '0');
  return s;
}

/**
 * Decode base64 zip, enforce size, verify sha256 hex matches.
 */
export async function validateUploadPayload(opts: {
  zipBase64?: string;
  zipSha256?: string;
  maxBytes?: number;
}): Promise<UploadPayloadResult> {
  if (!opts.zipBase64 || typeof opts.zipBase64 !== 'string') {
    return { ok: false, code: 'BAD_ENCODING', message: 'zipBase64 required' };
  }
  if (!opts.zipSha256 || typeof opts.zipSha256 !== 'string') {
    return { ok: false, code: 'BAD_ENCODING', message: 'zipSha256 required' };
  }
  if (!SHA256_HEX.test(opts.zipSha256)) {
    return {
      ok: false,
      code: 'BAD_ENCODING',
      message: 'zipSha256 must be 64 hex characters',
    };
  }

  let bytes: Uint8Array;
  try {
    const cleaned = opts.zipBase64.replace(/\s+/g, '');
    const bin = atob(cleaned);
    bytes = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  } catch {
    return { ok: false, code: 'BAD_ENCODING', message: 'zipBase64 is not valid base64' };
  }

  const max = opts.maxBytes ?? MAX_UPLOAD_BYTES;
  if (bytes.byteLength > max) {
    return {
      ok: false,
      code: 'BAD_ENCODING',
      message: `zip payload exceeds maximum of ${max} bytes`,
    };
  }
  if (bytes.byteLength === 0) {
    return { ok: false, code: 'BAD_ENCODING', message: 'zip payload is empty' };
  }

  const digest = await crypto.subtle.digest('SHA-256', bytes);
  const actual = bytesToHex(digest);
  if (actual !== opts.zipSha256.toLowerCase()) {
    return {
      ok: false,
      code: 'BAD_ENCODING',
      message: 'zipSha256 does not match payload',
    };
  }

  return { ok: true, bytes, sha256Hex: actual };
}

export function submissionObjectPath(submissionId: string): string {
  // Keep path under submissions/ only — never engine/, plans/, .github/
  const safe = submissionId
    .replace(/[^A-Za-z0-9_-]/g, '_')
    .replace(/_+/g, '_')
    .replace(/^_|_$/g, '')
    .slice(0, 128) || 'submission';
  return `submissions/${safe}.zip`;
}
