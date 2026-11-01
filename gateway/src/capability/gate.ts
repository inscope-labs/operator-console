/**
 * Mutating-route capability gate.
 * Order: parse body capability → verifyCapability → checkAndStoreNonce.
 * Spec §8 + §12.4.
 */

import type { Env } from '../index';
import type { OperatorCapability, SignedCapability } from './types';
import { verifyCapability, type VerifyContext, type VerifyResult } from './verify';
import { checkAndStoreNonce } from './nonce';
import type { ErrorCode } from '../errors/catalogue';

export interface GateOk {
  ok: true;
  capability: OperatorCapability;
  signed: SignedCapability;
}

export interface GateErr {
  ok: false;
  code: ErrorCode;
  message: string;
}

export type GateResult = GateOk | GateErr;

export interface GateOptions {
  /** Raw capability string from the JSON body. */
  rawCapability: string;
  /** VerifyContext without now/resolveKey filled by caller overrides. */
  resolveKey: VerifyContext['resolveKey'];
  now?: number;
  expectedBranch?: string;
}

/**
 * Full gate for confirmation-bearing routes.
 */
export async function gateCapability(
  env: Env,
  opts: GateOptions
): Promise<GateResult> {
  const verifyResult: VerifyResult = await verifyCapability(opts.rawCapability, {
    now: opts.now ?? Math.floor(Date.now() / 1000),
    resolveKey: opts.resolveKey,
    expectedBranch: opts.expectedBranch,
  });

  if (!verifyResult.ok) {
    const codeMap: Record<string, ErrorCode> = {
      UNKNOWN_KID: 'UNKNOWN_KID',
      BAD_SIGNATURE: 'BAD_SIGNATURE',
      TTL_TOO_LONG: 'TTL_TOO_LONG',
      CAPABILITY_EXPIRED: 'CAPABILITY_EXPIRED',
      UNKNOWN_OP: 'UNKNOWN_OP',
      BRANCH_MISMATCH: 'BRANCH_MISMATCH',
      BRANCH_INVALID: 'BRANCH_INVALID',
      MALFORMED: 'NETWORK',
    };
    return {
      ok: false,
      code: codeMap[verifyResult.code] ?? 'NETWORK',
      message: verifyResult.message,
    };
  }

  const nonceResult = await checkAndStoreNonce(env, verifyResult.capability);
  if (!nonceResult.ok) {
    return {
      ok: false,
      code: nonceResult.code,
      message: nonceResult.message,
    };
  }

  return {
    ok: true,
    capability: verifyResult.capability,
    signed: verifyResult.signed,
  };
}
