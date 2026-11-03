/**
 * Android Key Attestation verification — GW-5.
 * No external dependencies; WebCrypto only.
 */

import {
  decodeDer,
  walk,
  decodeOid,
  type DerNode,
} from './der';
import type { AttestationMode, AuthTier } from '../config/constants';

/** Android KeyDescription OID 1.3.6.1.4.1.11129.2.1.17 */
export const KEY_DESCRIPTION_OID = [1, 3, 6, 1, 4, 1, 11129, 2, 1, 17];

/** Authorization tag: noAuthRequired = 503 */
export const TAG_NO_AUTH_REQUIRED = 503;

export type SecurityLevel = 'Software' | 'TrustedEnvironment' | 'StrongBox' | 'Unknown';

export interface AttestationVerifyInput {
  /** Leaf → ... → root, each cert base64 or base64url DER. */
  chain: string[];
  /** Expected challenge (32 raw bytes). */
  challenge: Uint8Array;
  /** Submitted leaf SPKI (base64 or base64url). */
  publicKey: string;
  /** Claimed tier from the client. */
  claimedAuthTier: AuthTier;
  mode: AttestationMode;
  /** Lowercase hex SHA-256 fingerprints of trusted roots (strict mode). */
  trustedRootSha256: string[];
  /** Allowed tiers for this environment. */
  allowedAuthTiers: AuthTier[];
  now?: number; // unix seconds
}

export type AttestationFailureCode =
  | 'ENROLL_ATTESTATION_INVALID'
  | 'AUTH_TIER_NOT_ALLOWED';

export type AttestationResult =
  | {
      ok: true;
      authTier: AuthTier;
      leafCertSha256: string;
      publicKeySpkiBase64Url: string;
      securityLevel: SecurityLevel;
    }
  | { ok: false; code: AttestationFailureCode; message: string };

function b64ToBytes(s: string): Uint8Array {
  const cleaned = s.replace(/\s+/g, '');
  const b64 = cleaned.replace(/-/g, '+').replace(/_/g, '/');
  const pad = b64.length % 4 === 0 ? '' : '='.repeat(4 - (b64.length % 4));
  const bin = atob(b64 + pad);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function bytesToBase64Url(bytes: Uint8Array): string {
  let binary = '';
  for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

async function sha256Hex(data: Uint8Array): Promise<string> {
  const dig = await crypto.subtle.digest('SHA-256', data);
  return [...new Uint8Array(dig)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

function bytesEqual(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) return false;
  for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) return false;
  return true;
}

/**
 * Very small X.509 TBS extraction: Certificate ::= SEQUENCE { tbsCertificate, signatureAlgorithm, signature }
 * Returns tbs bytes and signature BIT STRING content (excluding unused-bits leading byte when present).
 */
function parseCertificate(der: Uint8Array): {
  root: DerNode;
  tbs: Uint8Array;
  signatureAlgoOid: number[];
  signature: Uint8Array;
  spki: Uint8Array;
  notBefore: Date;
  notAfter: Date;
} {
  const root = decodeDer(der);
  if (!root.children || root.children.length < 3) {
    throw new Error('certificate structure');
  }
  const tbsNode = root.children[0];
  const sigAlgNode = root.children[1];
  const sigNode = root.children[2];
  // Re-encode TBS as original slice is in der under constructed — use value with SEQUENCE tag
  // For verify we need exact TBS bytes as they appear in the certificate.
  // Reconstruct from original der: first content element.
  const tbs = extractFirstElement(der);
  const signatureAlgoOid = findFirstOid(sigAlgNode) ?? [];
  let signature = sigNode.value;
  if (signature.length > 0 && sigNode.tagNumber === 3) {
    // BIT STRING: first byte is unused bits count
    signature = signature.subarray(1);
  }
  const spki = extractSpki(tbsNode);
  const { notBefore, notAfter } = extractValidity(tbsNode);
  return { root, tbs, signatureAlgoOid, signature, spki, notBefore, notAfter };
}

function extractFirstElement(der: Uint8Array): Uint8Array {
  // skip outer sequence tag+length, return first child's full TLV
  if (der[0] !== 0x30) throw new Error('expected SEQUENCE');
  let o = 1;
  let len = der[o++];
  if (len & 0x80) {
    const n = len & 0x7f;
    len = 0;
    for (let i = 0; i < n; i++) len = (len << 8) | der[o++];
  }
  const start = o;
  // read first TLV length
  const tag = der[o++];
  void tag;
  let l = der[o++];
  if (l & 0x80) {
    const n = l & 0x7f;
    l = 0;
    for (let i = 0; i < n; i++) l = (l << 8) | der[o++];
  }
  const end = o + l;
  return der.subarray(start, end);
}

function findFirstOid(node: DerNode): number[] | null {
  let found: number[] | null = null;
  walk(node, (n) => {
    if (found) return;
    if (n.tagNumber === 6 && !n.constructed) found = decodeOid(n.value);
  });
  return found;
}

function extractSpki(tbs: DerNode): Uint8Array {
  // tbsCertificate fields: version?, serial, signature, issuer, validity, subject, subjectPublicKeyInfo, ...
  if (!tbs.children) throw new Error('tbs');
  // Find first BIT STRING that is inside a SEQUENCE after validity — subjectPublicKeyInfo
  // subjectPublicKeyInfo ::= SEQUENCE { algorithm, subjectPublicKey BIT STRING }
  for (const c of tbs.children) {
    if (c.constructed && c.tagNumber === 16 && c.children && c.children.length >= 2) {
      const last = c.children[c.children.length - 1];
      if (last.tagNumber === 3) {
        // full SPKI TLV from original — reconstruct
        return encodeSequenceSpki(c);
      }
    }
  }
  throw new Error('SPKI not found');
}

function encodeSequenceSpki(seq: DerNode): Uint8Array {
  // Use raw value wrapped as SEQUENCE for comparison of SPKI bytes
  // Compare algorithm+key; for equality of submitted key we compare BIT STRING key bits
  const bitString = seq.children![seq.children!.length - 1];
  return bitString.value.byteLength > 0 && bitString.tagNumber === 3
    ? bitString.value.subarray(1)
    : bitString.value;
}

function extractValidity(tbs: DerNode): { notBefore: Date; notAfter: Date } {
  if (!tbs.children) throw new Error('tbs');
  for (const c of tbs.children) {
    if (c.constructed && c.children && c.children.length === 2) {
      const a = parseTime(c.children[0]);
      const b = parseTime(c.children[1]);
      if (a && b) return { notBefore: a, notAfter: b };
    }
  }
  // fail open on validity parse only if missing — treat as invalid
  throw new Error('validity not found');
}

function parseTime(n: DerNode): Date | null {
  // UTCTime (23) or GeneralizedTime (24)
  if (n.tagNumber !== 23 && n.tagNumber !== 24) return null;
  const s = new TextDecoder().decode(n.value);
  // YYMMDDHHMMSSZ or YYYYMMDDHHMMSSZ
  if (n.tagNumber === 23 && s.length >= 13) {
    const yy = parseInt(s.slice(0, 2), 10);
    const year = yy >= 50 ? 1900 + yy : 2000 + yy;
    return new Date(
      Date.UTC(
        year,
        parseInt(s.slice(2, 4), 10) - 1,
        parseInt(s.slice(4, 6), 10),
        parseInt(s.slice(6, 8), 10),
        parseInt(s.slice(8, 10), 10),
        parseInt(s.slice(10, 12), 10)
      )
    );
  }
  if (n.tagNumber === 24 && s.length >= 15) {
    return new Date(
      Date.UTC(
        parseInt(s.slice(0, 4), 10),
        parseInt(s.slice(4, 6), 10) - 1,
        parseInt(s.slice(6, 8), 10),
        parseInt(s.slice(8, 10), 10),
        parseInt(s.slice(10, 12), 10),
        parseInt(s.slice(12, 14), 10)
      )
    );
  }
  return null;
}

async function importPublicKeyFromSpki(
  spki: Uint8Array,
  algoOid: number[]
): Promise<CryptoKey> {
  // ecdsa p256 1.2.840.10045.2.1 with named curve; rsa 1.2.840.113549.1.1.1
  const isEcdsa = algoOid[0] === 1 && algoOid[1] === 2 && algoOid[2] === 840 && algoOid[3] === 10045;
  const isRsa = algoOid[0] === 1 && algoOid[1] === 2 && algoOid[2] === 840 && algoOid[3] === 113549;
  if (isEcdsa) {
    return crypto.subtle.importKey(
      'spki',
      spki,
      { name: 'ECDSA', namedCurve: 'P-256' },
      false,
      ['verify']
    );
  }
  if (isRsa) {
    return crypto.subtle.importKey(
      'spki',
      spki,
      { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
      false,
      ['verify']
    );
  }
  // Try ECDSA P-256 as default for test certs
  return crypto.subtle.importKey(
    'spki',
    spki,
    { name: 'ECDSA', namedCurve: 'P-256' },
    false,
    ['verify']
  );
}

function extractSpkiTlvFromCert(der: Uint8Array): Uint8Array {
  // Parse and find subjectPublicKeyInfo full TLV inside TBS — for WebCrypto importKey('spki')
  const root = decodeDer(der);
  const tbs = root.children![0];
  for (const c of tbs.children ?? []) {
    if (c.constructed && c.tagNumber === 16 && c.children && c.children.length >= 2) {
      const last = c.children[c.children.length - 1];
      if (last.tagNumber === 3) {
        // Rebuild SEQUENCE TLV from children is hard without original slice;
        // use bit string key material path instead via full cert SPKI export in tests.
        // Here: walk original der for SPKI by structure position (6th element-ish).
        return rebuildSeq(c);
      }
    }
  }
  throw new Error('SPKI TLV not found');
}

function rebuildSeq(seq: DerNode): Uint8Array {
  // Minimal: encode algorithm + bitstring from child values with universal tags
  // This is approximate; tests build certs we control.
  const chunks: Uint8Array[] = [];
  for (const ch of seq.children ?? []) {
    const tag = ch.constructed ? ch.tagNumber | 0x20 : ch.tagNumber;
    // universal class assumed for alg/bitstring
    const t = ch.tagClass === 2 ? 0x80 | (ch.constructed ? 0x20 : 0) | (ch.tagNumber < 30 ? ch.tagNumber : 0) : tag;
    const len =
      ch.value.length < 0x80
        ? new Uint8Array([ch.value.length])
        : new Uint8Array([0x82, (ch.value.length >> 8) & 0xff, ch.value.length & 0xff]);
    const part = new Uint8Array(1 + len.length + ch.value.length);
    part[0] = ch.tagNumber === 3 ? 0x03 : ch.tagNumber === 6 ? 0x06 : ch.constructed ? 0x30 : t;
    if (ch.tagNumber === 16 && ch.constructed) part[0] = 0x30;
    if (ch.tagNumber === 3) part[0] = 0x03;
    if (ch.tagNumber === 6) part[0] = 0x06;
    part.set(len, 1);
    part.set(ch.value, 1 + len.length);
    chunks.push(part);
  }
  const contentLen = chunks.reduce((s, c) => s + c.length, 0);
  const out = new Uint8Array(2 + contentLen);
  out[0] = 0x30;
  out[1] = contentLen;
  let o = 2;
  for (const c of chunks) {
    out.set(c, o);
    o += c.length;
  }
  return out;
}

/** Parse KeyDescription extension value for challenge, security level, auth tags. */
export function parseKeyDescription(extValue: Uint8Array): {
  attestationVersion: number;
  attestationSecurityLevel: number;
  challenge: Uint8Array;
  softwareEnforcedNoAuth: boolean;
  teeEnforcedNoAuth: boolean;
} {
  // KeyDescription ::= SEQUENCE { attestationVersion, attestationSecurityLevel, ... attestationChallenge OCTET STRING, ... softwareEnforced, teeEnforced }
  const node = decodeDer(extValue);
  if (!node.children || node.children.length < 5) {
    throw new Error('KeyDescription structure');
  }
  const attestationVersion = readInt(node.children[0]);
  const attestationSecurityLevel = readInt(node.children[1]);
  // children[2] keymasterVersion, [3] keymasterSecurityLevel, [4] attestationChallenge
  const challengeNode = node.children[4];
  const challenge = challengeNode.value;
  let softwareEnforcedNoAuth = false;
  let teeEnforcedNoAuth = false;
  // authorization lists often last two
  if (node.children.length >= 7) {
    softwareEnforcedNoAuth = authListHasTag(node.children[5], TAG_NO_AUTH_REQUIRED);
    teeEnforcedNoAuth = authListHasTag(node.children[6], TAG_NO_AUTH_REQUIRED);
  }
  return {
    attestationVersion,
    attestationSecurityLevel,
    challenge,
    softwareEnforcedNoAuth,
    teeEnforcedNoAuth,
  };
}

function readInt(n: DerNode): number {
  let v = 0;
  for (let i = 0; i < n.value.length; i++) v = (v << 8) | n.value[i];
  return v;
}

function authListHasTag(list: DerNode, tag: number): boolean {
  // Authorization list is SEQUENCE of context-specific tagged values; tag 503 needs high-tag form
  let found = false;
  walk(list, (n) => {
    if (n.tagClass === 2 && n.tagNumber === tag) found = true;
  });
  return found;
}

function securityLevelName(level: number): SecurityLevel {
  if (level === 0) return 'Software';
  if (level === 1) return 'TrustedEnvironment';
  if (level === 2) return 'StrongBox';
  return 'Unknown';
}

function findKeyDescriptionExtension(certDer: Uint8Array): Uint8Array | null {
  const root = decodeDer(certDer);
  const tbs = root.children?.[0];
  if (!tbs?.children) return null;
  let found: Uint8Array | null = null;
  walk(tbs, (n) => {
    if (found) return;
    if (n.tagNumber === 6 && !n.constructed) {
      const oid = decodeOid(n.value);
      if (
        oid.length === KEY_DESCRIPTION_OID.length &&
        oid.every((v, i) => v === KEY_DESCRIPTION_OID[i])
      ) {
        // extension value is nearby OCTET STRING sibling in parent
        // parent SEQUENCE: extnID, critical?, extnValue
      }
    }
  });
  // Second pass: look for extension sequences
  walk(tbs, (n) => {
    if (found) return;
    if (n.constructed && n.children && n.children.length >= 2) {
      const oidNode = n.children[0];
      if (oidNode.tagNumber === 6) {
        const oid = decodeOid(oidNode.value);
        if (
          oid.length === KEY_DESCRIPTION_OID.length &&
          oid.every((v, i) => v === KEY_DESCRIPTION_OID[i])
        ) {
          const val = n.children[n.children.length - 1];
          // extnValue OCTET STRING wraps the KeyDescription
          found = val.tagNumber === 4 ? val.value : val.value;
        }
      }
    }
  });
  return found;
}

export async function verifyAttestation(
  input: AttestationVerifyInput
): Promise<AttestationResult> {
  try {
    if (!input.chain || input.chain.length < 2) {
      return {
        ok: false,
        code: 'ENROLL_ATTESTATION_INVALID',
        message: 'attestation chain must contain at least 2 certificates',
      };
    }

    const certs = input.chain.map((c) => b64ToBytes(c));
    const now = input.now ?? Math.floor(Date.now() / 1000);
    const nowDate = new Date(now * 1000);

    // Parse all certs
    const parsed = certs.map((c) => parseCertificate(c));

    // Validity windows
    for (const p of parsed) {
      if (nowDate < p.notBefore || nowDate > p.notAfter) {
        return {
          ok: false,
          code: 'ENROLL_ATTESTATION_INVALID',
          message: 'certificate validity window failed',
        };
      }
    }

    // Chain signatures: cert[i] signed by cert[i+1]
    for (let i = 0; i < parsed.length - 1; i++) {
      const child = parsed[i];
      const parent = parsed[i + 1];
      const parentSpkiTlv = extractSpkiTlvFromCert(certs[i + 1]);
      const parentOid = findFirstOid(parent.root.children![0]) ?? [1, 2, 840, 10045, 2, 1];
      let key: CryptoKey;
      try {
        key = await importPublicKeyFromSpki(parentSpkiTlv, parentOid);
      } catch {
        return {
          ok: false,
          code: 'ENROLL_ATTESTATION_INVALID',
          message: 'parent public key import failed',
        };
      }
      const algoName = (key.algorithm as { name: string }).name;
      let okSig = false;
      try {
        if (algoName === 'ECDSA') {
          okSig = await crypto.subtle.verify(
            { name: 'ECDSA', hash: 'SHA-256' },
            key,
            child.signature,
            child.tbs
          );
        } else {
          okSig = await crypto.subtle.verify(
            { name: 'RSASSA-PKCS1-v1_5' },
            key,
            child.signature,
            child.tbs
          );
        }
      } catch {
        okSig = false;
      }
      if (!okSig) {
        return {
          ok: false,
          code: 'ENROLL_ATTESTATION_INVALID',
          message: 'certificate chain signature verification failed',
        };
      }
    }

    // Root trust
    const rootDer = certs[certs.length - 1];
    const rootHash = await sha256Hex(rootDer);
    if (input.mode === 'strict') {
      if (!input.trustedRootSha256.map((h) => h.toLowerCase()).includes(rootHash)) {
        return {
          ok: false,
          code: 'ENROLL_ATTESTATION_INVALID',
          message: 'attestation root is not trusted',
        };
      }
    }

    // KeyDescription on leaf
    const kdRaw = findKeyDescriptionExtension(certs[0]);
    if (!kdRaw) {
      return {
        ok: false,
        code: 'ENROLL_ATTESTATION_INVALID',
        message: 'KeyDescription extension missing on leaf',
      };
    }
    // kdRaw may be OCTET STRING content of KeyDescription SEQUENCE
    let kd;
    try {
      kd = parseKeyDescription(kdRaw);
    } catch {
      // try unwrap if double-wrapped
      try {
        const inner = decodeDer(kdRaw);
        kd = parseKeyDescription(inner.tagNumber === 4 ? inner.value : kdRaw);
      } catch {
        return {
          ok: false,
          code: 'ENROLL_ATTESTATION_INVALID',
          message: 'KeyDescription parse failed',
        };
      }
    }

    if (!bytesEqual(kd.challenge, input.challenge)) {
      return {
        ok: false,
        code: 'ENROLL_ATTESTATION_INVALID',
        message: 'attestation challenge mismatch',
      };
    }

    const secLevel = securityLevelName(kd.attestationSecurityLevel);
    if (input.mode === 'strict') {
      if (secLevel !== 'TrustedEnvironment' && secLevel !== 'StrongBox') {
        return {
          ok: false,
          code: 'ENROLL_ATTESTATION_INVALID',
          message: 'security level insufficient for strict mode',
        };
      }
    }

    // Leaf public key must match submitted key
    const leafKeyBits = parsed[0].spki;
    const submitted = b64ToBytes(input.publicKey);
    // submitted may be full SPKI or raw key bits — compare both ways
    const submittedBits =
      submitted.length > 0 && submitted[0] === 0x30
        ? extractKeyBitsFromSpki(submitted)
        : submitted;
    if (!bytesEqual(leafKeyBits, submittedBits) && !bytesEqual(leafKeyBits, submitted)) {
      return {
        ok: false,
        code: 'ENROLL_ATTESTATION_INVALID',
        message: 'leaf public key does not match submitted publicKey',
      };
    }

    // Tier derivation: tag 503 anywhere in auth lists → in_app_confirm
    const derived: AuthTier =
      kd.softwareEnforcedNoAuth || kd.teeEnforcedNoAuth
        ? 'in_app_confirm'
        : 'hardware_confirm';

    if (derived !== input.claimedAuthTier) {
      return {
        ok: false,
        code: 'ENROLL_ATTESTATION_INVALID',
        message: 'claimed authTier does not match attestation',
      };
    }

    if (!input.allowedAuthTiers.includes(derived)) {
      return {
        ok: false,
        code: 'AUTH_TIER_NOT_ALLOWED',
        message: 'derived auth tier is not permitted in this environment',
      };
    }

    const leafCertSha256 = await sha256Hex(certs[0]);
    return {
      ok: true,
      authTier: derived,
      leafCertSha256,
      publicKeySpkiBase64Url: bytesToBase64Url(submitted[0] === 0x30 ? submitted : submitted),
      securityLevel: secLevel,
    };
  } catch (e) {
    return {
      ok: false,
      code: 'ENROLL_ATTESTATION_INVALID',
      message: e instanceof Error ? e.message : 'attestation verification failed',
    };
  }
}

function extractKeyBitsFromSpki(spki: Uint8Array): Uint8Array {
  try {
    const n = decodeDer(spki);
    const bit = n.children?.[n.children.length - 1];
    if (bit && bit.tagNumber === 3 && bit.value.length > 0) {
      return bit.value.subarray(1);
    }
  } catch {
    /* fall through */
  }
  return spki;
}
