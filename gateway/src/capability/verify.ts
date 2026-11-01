/**
 * Capability verification — signature, TTL, kid, nonce-scope binding.
 * Spec §8. Model B (recommended under D-a): console signs with the device
 * key; gateway verifies against the enrolled public key selected by kid.
 *
 * Fail closed on every ambiguous case.
 */

import {
  MAX_TTL_SECONDS,
  CLOCK_SKEW_SECONDS,
  type OperatorCapability,
  type SignedCapability,
} from './types';
import { decodeCapability, splitSignedCapability } from './codec';

export type VerifyFailure =
  | 'UNKNOWN_KID'
  | 'BAD_SIGNATURE'
  | 'TTL_TOO_LONG'
  | 'CAPABILITY_EXPIRED'
  | 'UNKNOWN_OP'
  | 'BRANCH_MISMATCH'
  | 'BRANCH_INVALID'
  | 'MALFORMED';

export interface VerifyOk {
  ok: true;
  capability: OperatorCapability;
  signed: SignedCapability;
}

export interface VerifyErr {
  ok: false;
  code: VerifyFailure;
  message: string;
}

export type VerifyResult = VerifyOk | VerifyErr;

export interface VerifyContext {
  /** Current time in Unix seconds (injectable for tests). */
  now: number;
  /**
   * Resolve a public key by kid.
   * Model B: CryptoKey (ECDSA P-256 or Ed25519) for the enrolled device.
   * Model A (compat): ArrayBuffer HMAC secret.
   * Returns null if the kid is unknown or retired.
   */
  resolveKey: (kid: string) => Promise<CryptoKey | ArrayBuffer | null>;
  /**
   * Optional: expected branch from the work order.
   * When supplied, a mismatch yields BRANCH_MISMATCH.
   */
  expectedBranch?: string;
}

/**
 * Verify a raw capability string (message + signature).
 * Does not check nonce replay — that is the responsibility of the nonce store.
 * Does not perform the hard ref (HEAD) check — that belongs on the write path.
 */
export async function verifyCapability(
  raw: string,
  ctx: VerifyContext
): Promise<VerifyResult> {
  let message: string;
  let signature: string;
  try {
    ({ message, signature } = splitSignedCapability(raw));
  } catch {
    return { ok: false, code: 'MALFORMED', message: 'cannot split signature' };
  }

  if (!signature) {
    return { ok: false, code: 'MALFORMED', message: 'empty signature' };
  }

  let capability: OperatorCapability;
  try {
    capability = decodeCapability(message);
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    if (msg.startsWith('UNKNOWN_OP')) {
      return { ok: false, code: 'UNKNOWN_OP', message: msg };
    }
    if (msg.startsWith('BRANCH_INVALID')) {
      return { ok: false, code: 'BRANCH_INVALID', message: msg };
    }
    return { ok: false, code: 'MALFORMED', message: msg };
  }

  // TTL bounds
  const ttl = capability.exp - ctx.now;
  if (ttl > MAX_TTL_SECONDS + CLOCK_SKEW_SECONDS) {
    return {
      ok: false,
      code: 'TTL_TOO_LONG',
      message: `TTL ${ttl}s exceeds maximum ${MAX_TTL_SECONDS}s`,
    };
  }
  if (capability.exp < ctx.now - CLOCK_SKEW_SECONDS) {
    return {
      ok: false,
      code: 'CAPABILITY_EXPIRED',
      message: `expired at ${capability.exp}, now ${ctx.now}`,
    };
  }

  // Branch scope
  if (ctx.expectedBranch && capability.branch !== ctx.expectedBranch) {
    return {
      ok: false,
      code: 'BRANCH_MISMATCH',
      message: `capability branch ${capability.branch} != expected ${ctx.expectedBranch}`,
    };
  }

  // Key resolution
  const key = await ctx.resolveKey(capability.kid);
  if (!key) {
    return {
      ok: false,
      code: 'UNKNOWN_KID',
      message: `kid not enrolled or retired: ${capability.kid}`,
    };
  }

  const signatureValid = await verifySignature(message, signature, key);
  if (!signatureValid) {
    return {
      ok: false,
      code: 'BAD_SIGNATURE',
      message: 'signature verification failed',
    };
  }

  return {
    ok: true,
    capability,
    signed: { capability, signature, message },
  };
}

/**
 * Cryptographic verification.
 * - CryptoKey → ECDSA P-256 (SHA-256) or Ed25519 (algorithm from key).
 * - ArrayBuffer → HMAC-SHA256 (Model A compatibility).
 * Signature is base64url (no padding).
 */
async function verifySignature(
  message: string,
  signatureB64url: string,
  key: CryptoKey | ArrayBuffer
): Promise<boolean> {
  try {
    const data = new TextEncoder().encode(message);
    const sig = base64UrlToBytes(signatureB64url);

    if (key instanceof ArrayBuffer || ArrayBuffer.isView(key)) {
      // Model A: HMAC-SHA256
      const raw = key instanceof ArrayBuffer ? key : key.buffer;
      const hmacKey = await crypto.subtle.importKey(
        'raw',
        raw as ArrayBuffer,
        { name: 'HMAC', hash: 'SHA-256' },
        false,
        ['verify']
      );
      return crypto.subtle.verify('HMAC', hmacKey, sig, data);
    }

    // Model B: asymmetric
    const algo = key.algorithm as KeyAlgorithm & { name: string; namedCurve?: string };
    if (algo.name === 'ECDSA') {
      return crypto.subtle.verify(
        { name: 'ECDSA', hash: 'SHA-256' },
        key,
        sig,
        data
      );
    }
    if (algo.name === 'Ed25519') {
      return crypto.subtle.verify('Ed25519', key, sig, data);
    }

    // Unknown key type — fail closed
    return false;
  } catch {
    return false;
  }
}

function base64UrlToBytes(s: string): Uint8Array {
  const padded = s.replace(/-/g, '+').replace(/_/g, '/');
  const pad = padded.length % 4 === 0 ? '' : '='.repeat(4 - (padded.length % 4));
  const binary = atob(padded + pad);
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i);
  return out;
}

/** base64url (no padding) encoder — used by tests and by the console minter. */
export function bytesToBase64Url(bytes: Uint8Array): string {
  let binary = '';
  for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
