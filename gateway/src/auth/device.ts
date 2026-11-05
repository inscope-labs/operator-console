/**
 * Device key registry — GW-6 / Stage 7.
 * kid → { algorithm, key }. Algorithm is a property of the kid, never the request.
 *
 * - Active ECDSA enrolments from EnrolmentRegistry (retired → null / UNKNOWN_KID)
 * - HMAC lab kids from SIGNING_KEYS JSON
 */

import type { Env } from '../index';
import { lookupKid } from '../enrolment/registry-client';

export type DeviceKeyAlgorithm = 'ECDSA' | 'HMAC' | 'Ed25519';

export interface ResolvedDeviceKey {
  algorithm: DeviceKeyAlgorithm;
  /** CryptoKey for ECDSA/Ed25519; raw ArrayBuffer for HMAC secret. */
  key: CryptoKey | ArrayBuffer;
  /** Optional auth tier from enrolment (informational). */
  authTier?: string;
}

export interface SigningKeyEntry {
  kid: string;
  /** HMAC | ECDSA | Ed25519 — lab keys are typically HMAC. */
  algorithm: string;
  /** HMAC: secret as utf8 or base64url. ECDSA: SPKI base64url (rare in SIGNING_KEYS). */
  secret?: string;
  publicKey?: string;
  encoding?: 'utf8' | 'base64' | 'base64url';
}

function b64ToBytes(s: string): Uint8Array {
  const cleaned = s.replace(/\s+/g, '');
  const b64 = cleaned.replace(/-/g, '+').replace(/_/g, '/');
  const pad = b64.length % 4 === 0 ? '' : '='.repeat(4 - (b64.length % 4));
  const bin = atob(b64 + pad);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function parseSigningKeys(raw: string | undefined): SigningKeyEntry[] {
  if (!raw || !raw.trim()) return [];
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) return [];
    return parsed as SigningKeyEntry[];
  } catch {
    return [];
  }
}

async function importEcdsaSpki(spkiB64: string): Promise<CryptoKey> {
  const spki = b64ToBytes(spkiB64);
  // Try P-256 first (Android attestation default); P-384 fallback.
  try {
    return await crypto.subtle.importKey(
      'spki',
      spki,
      { name: 'ECDSA', namedCurve: 'P-256' },
      false,
      ['verify']
    );
  } catch {
    return await crypto.subtle.importKey(
      'spki',
      spki,
      { name: 'ECDSA', namedCurve: 'P-384' },
      false,
      ['verify']
    );
  }
}

/**
 * Resolve a capability kid to a verification key.
 * Returns null if unknown, retired, or misconfigured (caller maps to UNKNOWN_KID).
 */
export async function resolveDeviceKey(
  env: Env,
  kid: string
): Promise<ResolvedDeviceKey | null> {
  if (!kid) return null;

  // 1. Lab / HMAC keys from SIGNING_KEYS (explicit configuration)
  const entries = parseSigningKeys(env.SIGNING_KEYS);
  const lab = entries.find((e) => e.kid === kid);
  if (lab) {
    const alg = (lab.algorithm || 'HMAC').toUpperCase();
    if (alg === 'HMAC' || alg === 'HS256') {
      const secret = lab.secret ?? '';
      let bytes: Uint8Array;
      if (lab.encoding === 'base64' || lab.encoding === 'base64url') {
        bytes = b64ToBytes(secret);
      } else {
        bytes = new TextEncoder().encode(secret);
      }
      // Copy into a plain ArrayBuffer (avoid SharedArrayBuffer typing issues)
      const copy = new Uint8Array(bytes.byteLength);
      copy.set(bytes);
      return { algorithm: 'HMAC', key: copy.buffer };
    }
    if (alg === 'ECDSA' && lab.publicKey) {
      const key = await importEcdsaSpki(lab.publicKey);
      return { algorithm: 'ECDSA', key };
    }
    return null;
  }

  // 2. Enrolment registry — active ECDSA kids only
  try {
    const record = await lookupKid(env, kid);
    if (!record) return null;
    if (record.status !== 'active') return null;
    const key = await importEcdsaSpki(record.publicKey);
    return {
      algorithm: 'ECDSA',
      key,
      authTier: record.authTier,
    };
  } catch {
    // Missing binding or network — fail closed
    return null;
  }
}

/** Adapter for VerifyContext.resolveKey. */
export function createResolveKey(
  env: Env
): (kid: string) => Promise<CryptoKey | ArrayBuffer | null> {
  return async (kid: string) => {
    const resolved = await resolveDeviceKey(env, kid);
    return resolved ? resolved.key : null;
  };
}
