/**
 * Nonce replay protection via the NonceStore Durable Object.
 * Spec §12.4 — check-and-store before performing a mutating operation.
 */

import type { OperatorCapability } from './types';
import type { Env } from '../index';

export type NonceCheckResult =
  | { ok: true }
  | { ok: false; code: 'NONCE_REPLAYED' | 'NETWORK'; message: string };

/**
 * Atomically record the capability nonce.
 * Must be called after signature verification and before the side-effect.
 */
export async function checkAndStoreNonce(
  env: Env,
  capability: OperatorCapability
): Promise<NonceCheckResult> {
  if (!env.NONCE_STORE) {
    return {
      ok: false,
      code: 'NETWORK',
      message: 'NONCE_STORE binding missing',
    };
  }
  try {
    // One DO instance per nonce prefix space; use a fixed name for the store.
    const id = env.NONCE_STORE.idFromName('cbridge-nonce-store');
    const stub = env.NONCE_STORE.get(id);
    const res = await stub.fetch('https://nonce/check-and-store', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({
        nonce: capability.nonce,
        exp: capability.exp,
        kid: capability.kid,
        op: capability.op,
      }),
    });
    if (res.status === 409) {
      const body = (await res.json().catch(() => ({}))) as { message?: string };
      return {
        ok: false,
        code: 'NONCE_REPLAYED',
        message: body.message ?? 'nonce already used',
      };
    }
    if (!res.ok) {
      const text = await res.text().catch(() => '');
      return {
        ok: false,
        code: 'NETWORK',
        message: `nonce store error ${res.status}: ${text.slice(0, 120)}`,
      };
    }
    return { ok: true };
  } catch (e) {
    return {
      ok: false,
      code: 'NETWORK',
      message: e instanceof Error ? e.message : String(e),
    };
  }
}
