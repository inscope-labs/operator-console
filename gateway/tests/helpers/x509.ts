/**
 * Synthetic certificate / KeyDescription builders for attestation unit tests.
 * WebCrypto keygen + minimal DER encoder (no dependencies).
 */

import {
  encodeTlv,
  encodeOid,
  encodeContextTag,
  concatBytes,
} from '../../src/attestation/der';
import { TAG_NO_AUTH_REQUIRED } from '../../src/attestation/verify';

function intBytes(n: number): Uint8Array {
  if (n === 0) return new Uint8Array([0]);
  const out: number[] = [];
  let v = n;
  while (v > 0) {
    out.unshift(v & 0xff);
    v >>= 8;
  }
  if (out[0] & 0x80) out.unshift(0);
  return new Uint8Array(out);
}

function encodeInt(n: number): Uint8Array {
  return encodeTlv(0x02, intBytes(n));
}

function encodeOctet(data: Uint8Array): Uint8Array {
  return encodeTlv(0x04, data);
}

function encodeUtf8(s: string): Uint8Array {
  return encodeTlv(0x0c, new TextEncoder().encode(s));
}

function encodeUtc(d: Date): Uint8Array {
  const yy = String(d.getUTCFullYear() % 100).padStart(2, '0');
  const mm = String(d.getUTCMonth() + 1).padStart(2, '0');
  const dd = String(d.getUTCDate()).padStart(2, '0');
  const hh = String(d.getUTCHours()).padStart(2, '0');
  const mi = String(d.getUTCMinutes()).padStart(2, '0');
  const ss = String(d.getUTCSeconds()).padStart(2, '0');
  return encodeTlv(0x17, new TextEncoder().encode(`${yy}${mm}${dd}${hh}${mi}${ss}Z`));
}

function seq(...parts: Uint8Array[]): Uint8Array {
  return encodeTlv(0x30, concatBytes(...parts), true);
}

/** Build a KeyDescription SEQUENCE with challenge and optional noAuthRequired. */
export function buildKeyDescription(opts: {
  challenge: Uint8Array;
  securityLevel: number; // 0 Software, 1 TEE, 2 StrongBox
  noAuthRequired?: boolean;
}): Uint8Array {
  const authList = opts.noAuthRequired
    ? seq(encodeContextTag(TAG_NO_AUTH_REQUIRED, new Uint8Array(), false))
    : seq();
  return seq(
    encodeInt(200), // attestationVersion
    encodeInt(opts.securityLevel),
    encodeInt(41), // keymasterVersion
    encodeInt(opts.securityLevel),
    encodeOctet(opts.challenge),
    new Uint8Array([0x04, 0x00]), // uniqueId empty octet
    authList, // softwareEnforced
    authList // teeEnforced
  );
}

/** Extension: OID + OCTET STRING(KeyDescription). */
export function buildKeyDescriptionExtension(kd: Uint8Array): Uint8Array {
  const oid = encodeTlv(0x06, encodeOid([1, 3, 6, 1, 4, 1, 11129, 2, 1, 17]));
  const val = encodeOctet(kd);
  return seq(oid, val);
}

/**
 * Build a minimal self-describing "certificate" structure for unit tests of KeyDescription path.
 * Not a fully signed X.509 cert — used where parseKeyDescription is tested in isolation.
 * For chain tests, use buildSignedCertPair.
 */
export async function generateP256Pair(): Promise<CryptoKeyPair> {
  return (await crypto.subtle.generateKey(
    { name: 'ECDSA', namedCurve: 'P-256' },
    true,
    ['sign', 'verify']
  )) as CryptoKeyPair;
}

export async function exportSpki(key: CryptoKey): Promise<Uint8Array> {
  const raw = await crypto.subtle.exportKey('spki', key);
  return new Uint8Array(raw);
}

export function toBase64(data: Uint8Array): string {
  let s = '';
  for (let i = 0; i < data.length; i++) s += String.fromCharCode(data[i]);
  return btoa(s);
}

/**
 * Build a minimal signed TBS-like structure: SEQUENCE { payload OCTET, } + signature.
 * Used to exercise chain signature verification with WebCrypto.
 */
export async function buildSignedBlob(
  payload: Uint8Array,
  signer: CryptoKey
): Promise<{ tbs: Uint8Array; signature: Uint8Array; der: Uint8Array }> {
  const tbs = seq(encodeOctet(payload), encodeUtf8('test-tbs'));
  const sig = new Uint8Array(
    await crypto.subtle.sign({ name: 'ECDSA', hash: 'SHA-256' }, signer, tbs)
  );
  const bitString = encodeTlv(0x03, concatBytes(new Uint8Array([0]), sig));
  const sigAlg = seq(encodeTlv(0x06, encodeOid([1, 2, 840, 10045, 4, 3, 2]))); // ecdsa-with-SHA256
  const der = seq(tbs, sigAlg, bitString);
  return { tbs, signature: sig, der };
}

export { TAG_NO_AUTH_REQUIRED, encodeContextTag, seq, encodeInt, encodeOctet };
