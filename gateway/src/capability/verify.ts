/**
 * Capability verification — signature, TTL, kid, nonce, scope binding.
 * Spec §8. Model B (recommended under D-a): console signs, gateway verifies
 * against the enrolled device public key selected by kid.
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
   * Resolve a public key (or HMAC secret under Model A) by kid.
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

  // Signature verification (Model B: ECDSA/Ed25519 over the message UTF-8 bytes)
  // TODO: implement actual verify with Web Crypto against the resolved key.
  // Until implemented, fail closed.
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
 * Placeholder for the cryptographic verify step.
 * Must be replaced with Web Crypto (ECDSA-P256 or Ed25519) under Model B,
 * or HMAC-SHA256 under Model A.
 */
async function verifySignature(
  _message: string,
  _signature: string,
  _key: CryptoKey | ArrayBuffer
): Promise<boolean> {
  // TODO: real verification. Fail closed until implemented.
  return false;
}
