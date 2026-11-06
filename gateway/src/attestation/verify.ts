/**
 * Android Key Attestation verification — GW-5 (remediated).
 * Fail closed: any parse/verify exception => ENROLL_ATTESTATION_INVALID.
 */

import { decodeDer, walk, decodeOid, type DerNode } from './der';
import type { AttestationMode, AuthTier } from '../config/constants';

export const KEY_DESCRIPTION_OID = [1, 3, 6, 1, 4, 1, 11129, 2, 1, 17];
export const TAG_NO_AUTH_REQUIRED = 503;
export const TAG_USER_AUTH_TYPE = 504;

/** Algorithm OIDs */
const OID_EC_PUBLIC_KEY = [1, 2, 840, 10045, 2, 1];
const OID_RSA_ENCRYPTION = [1, 2, 840, 113549, 1, 1, 1];
const OID_P256 = [1, 2, 840, 10045, 3, 1, 7];
const OID_P384 = [1, 3, 132, 0, 34];
const OID_ECDSA_SHA256 = [1, 2, 840, 10045, 4, 3, 2];
const OID_ECDSA_SHA384 = [1, 2, 840, 10045, 4, 3, 3];
const OID_RSA_SHA256 = [1, 2, 840, 113549, 1, 1, 11];
const OID_RSA_SHA384 = [1, 2, 840, 113549, 1, 1, 12];
const OID_RSA_SHA512 = [1, 2, 840, 113549, 1, 1, 13];

export type SecurityLevel = 'Software' | 'TrustedEnvironment' | 'StrongBox' | 'Unknown';

export interface AttestationVerifyInput {
  chain: string[];
  challenge: Uint8Array;
  publicKey: string;
  claimedAuthTier: AuthTier;
  mode: AttestationMode;
  trustedRootSha256: string[];
  allowedAuthTiers: AuthTier[];
  now?: number;
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

function fail(message: string): AttestationResult {
  return { ok: false, code: 'ENROLL_ATTESTATION_INVALID', message };
}

function oidEq(a: number[], b: number[]): boolean {
  return a.length === b.length && a.every((v, i) => v === b[i]);
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

function bytesToBase64Url(bytes: Uint8Array): string {
  let binary = '';
  for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function bytesEqual(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) return false;
  let d = 0;
  for (let i = 0; i < a.length; i++) d |= a[i] ^ b[i];
  return d === 0;
}

async function sha256Hex(data: Uint8Array): Promise<string> {
  const dig = await crypto.subtle.digest('SHA-256', data);
  return [...new Uint8Array(dig)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

/** Slice the first TLV element after an outer SEQUENCE header. */
function firstElementTlv(seqDer: Uint8Array): Uint8Array {
  if (seqDer[0] !== 0x30) throw new Error('expected SEQUENCE');
  let o = 1;
  let len = seqDer[o++];
  if (len & 0x80) {
    const n = len & 0x7f;
    len = 0;
    for (let i = 0; i < n; i++) len = (len << 8) | seqDer[o++];
  }
  const start = o;
  // consume one TLV
  o++; // tag
  let l = seqDer[o++];
  if (l & 0x80) {
    const n = l & 0x7f;
    l = 0;
    for (let i = 0; i < n; i++) l = (l << 8) | seqDer[o++];
  }
  return seqDer.subarray(start, o + l);
}

function readInt(n: DerNode): number {
  let v = 0;
  for (let i = 0; i < n.value.length; i++) v = (v << 8) | n.value[i];
  return v;
}

function parseTime(n: DerNode): Date {
  const s = new TextDecoder().decode(n.value);
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
  throw new Error('bad time');
}

interface ParsedCert {
  der: Uint8Array;
  tbsTlv: Uint8Array;
  tbsNode: DerNode;
  signatureAlgoOid: number[];
  signatureRaw: Uint8Array; // BIT STRING content without unused-bits byte
  spkiTlv: Uint8Array;
  spkiKeyBits: Uint8Array;
  subjectTlv: Uint8Array;
  issuerTlv: Uint8Array;
  notBefore: Date;
  notAfter: Date;
  isCA: boolean;
  pathLen: number | null;
  hasKeyUsage: boolean;
  keyCertSign: boolean;
}

/** Extract subjectPublicKeyInfo SEQUENCE TLV from original TBS bytes by walking offsets — use node rebuild. */
function rebuildUniversalSeq(node: DerNode): Uint8Array {
  if (!node.children) {
    const tag = node.tagNumber === 3 ? 0x03 : node.tagNumber === 6 ? 0x06 : node.tagNumber === 4 ? 0x04 : node.tagNumber === 2 ? 0x02 : 0x04;
    const len =
      node.value.length < 0x80
        ? new Uint8Array([node.value.length])
        : node.value.length < 256
          ? new Uint8Array([0x81, node.value.length])
          : new Uint8Array([0x82, (node.value.length >> 8) & 0xff, node.value.length & 0xff]);
    const out = new Uint8Array(1 + len.length + node.value.length);
    out[0] = tag;
    out.set(len, 1);
    out.set(node.value, 1 + len.length);
    return out;
  }
  const parts = node.children.map(rebuildUniversalSeq);
  let contentLen = 0;
  for (const p of parts) contentLen += p.length;
  const len =
    contentLen < 0x80
      ? new Uint8Array([contentLen])
      : contentLen < 256
        ? new Uint8Array([0x81, contentLen])
        : new Uint8Array([0x82, (contentLen >> 8) & 0xff, contentLen & 0xff]);
  const out = new Uint8Array(1 + len.length + contentLen);
  out[0] = 0x30;
  out.set(len, 1);
  let o = 1 + len.length;
  for (const p of parts) {
    out.set(p, o);
    o += p.length;
  }
  return out;
}

function parseCertificate(der: Uint8Array): ParsedCert {
  const root = decodeDer(der);
  if (!root.children || root.children.length < 3) throw new Error('certificate structure');
  const tbsTlv = firstElementTlv(der);
  const tbsNode = root.children[0];
  const sigAlgOid = (() => {
    const n = root.children![1];
    let oid: number[] | null = null;
    walk(n, (x) => {
      if (!oid && x.tagNumber === 6 && !x.constructed) oid = decodeOid(x.value);
    });
    if (!oid) throw new Error('signatureAlgorithm OID missing');
    return oid;
  })();
  let sig = root.children[2].value;
  if (root.children[2].tagNumber === 3 && sig.length > 0) sig = sig.subarray(1);

  // TBS field order with optional version [0]
  const kids = tbsNode.children ?? [];
  let idx = 0;
  if (kids[0]?.tagClass === 2 && kids[0].tagNumber === 0) idx = 1; // version
  // serial idx, signature idx+1, issuer idx+2, validity idx+3, subject idx+4, spki idx+5
  const issuerNode = kids[idx + 2];
  const validityNode = kids[idx + 3];
  const subjectNode = kids[idx + 4];
  const spkiNode = kids[idx + 5];
  if (!issuerNode || !validityNode || !subjectNode || !spkiNode) {
    throw new Error('tbs fields missing');
  }
  const notBefore = parseTime(validityNode.children![0]);
  const notAfter = parseTime(validityNode.children![1]);
  const spkiTlv = rebuildUniversalSeq(spkiNode);
  const bitString = spkiNode.children![spkiNode.children!.length - 1];
  const spkiKeyBits =
    bitString.tagNumber === 3 && bitString.value.length > 0
      ? bitString.value.subarray(1)
      : bitString.value;
  const subjectTlv = rebuildUniversalSeq(subjectNode);
  const issuerTlv = rebuildUniversalSeq(issuerNode);

  let isCA = false;
  let pathLen: number | null = null;
  let hasKeyUsage = false;
  let keyCertSign = false;

  // extensions [3]
  const extContainer = kids.find((c) => c.tagClass === 2 && c.tagNumber === 3);
  if (extContainer?.children) {
    // EXPLICIT: children[0] is Extensions SEQUENCE
    const extensions = extContainer.children[0] ?? extContainer;
    for (const ext of extensions.children ?? []) {
      if (!ext.children || ext.children.length < 2) continue;
      const oid = decodeOid(ext.children[0].value);
      const valNode = ext.children[ext.children.length - 1];
      const val = valNode.tagNumber === 4 ? valNode.value : valNode.value;
      // basicConstraints 2.5.29.19
      if (oidEq(oid, [2, 5, 29, 19])) {
        const bc = decodeDer(val);
        // SEQUENCE { cA BOOLEAN DEFAULT FALSE, pathLenConstraint INTEGER OPTIONAL }
        if (bc.children) {
          for (const f of bc.children) {
            if (f.tagNumber === 1) isCA = f.value.length > 0 && f.value[0] !== 0;
            if (f.tagNumber === 2) pathLen = readInt(f);
          }
        }
      }
      // keyUsage 2.5.29.15
      if (oidEq(oid, [2, 5, 29, 15])) {
        hasKeyUsage = true;
        // BIT STRING — keyCertSign is bit 5
        const bits = valNode.tagNumber === 4 ? decodeDer(val) : valNode;
        const raw = bits.tagNumber === 3 ? bits.value : bits.value;
        if (raw.length >= 2) {
          const unused = raw[0];
          const b1 = raw[1];
          // bits: 0 digitalSignature ... 5 keyCertSign
          keyCertSign = (b1 & (0x80 >> 5)) !== 0 || (unused < 5 && false);
          // clearer: bit 5 from MSB of first content byte
          keyCertSign = (b1 & 0x04) !== 0; // 0x80>>5 = 0x04
        }
      }
    }
  }

  return {
    der,
    tbsTlv,
    tbsNode,
    signatureAlgoOid: sigAlgOid,
    signatureRaw: sig,
    spkiTlv,
    spkiKeyBits,
    subjectTlv,
    issuerTlv,
    notBefore,
    notAfter,
    isCA,
    pathLen,
    hasKeyUsage,
    keyCertSign,
  };
}

function parseParentKeyAlg(spkiNodeOrTlv: Uint8Array): {
  type: 'ECDSA' | 'RSA';
  curve?: 'P-256' | 'P-384';
  hashHint?: string;
} {
  const spki = decodeDer(spkiNodeOrTlv);
  const alg = spki.children?.[0];
  if (!alg?.children) throw new Error('SPKI algorithm missing');
  const algOid = decodeOid(alg.children[0].value);
  if (oidEq(algOid, OID_EC_PUBLIC_KEY)) {
    // parameters: named curve OID
    const param = alg.children[1];
    if (!param || param.tagNumber !== 6) throw new Error('EC curve OID missing');
    const curveOid = decodeOid(param.value);
    if (oidEq(curveOid, OID_P256)) return { type: 'ECDSA', curve: 'P-256' };
    if (oidEq(curveOid, OID_P384)) return { type: 'ECDSA', curve: 'P-384' };
    throw new Error('unsupported EC curve');
  }
  if (oidEq(algOid, OID_RSA_ENCRYPTION)) {
    return { type: 'RSA' };
  }
  throw new Error('unsupported parent key algorithm');
}

function parseSigAlg(oid: number[]): {
  type: 'ECDSA' | 'RSA';
  hash: 'SHA-256' | 'SHA-384' | 'SHA-512';
} {
  if (oidEq(oid, OID_ECDSA_SHA256)) return { type: 'ECDSA', hash: 'SHA-256' };
  if (oidEq(oid, OID_ECDSA_SHA384)) return { type: 'ECDSA', hash: 'SHA-384' };
  if (oidEq(oid, OID_RSA_SHA256)) return { type: 'RSA', hash: 'SHA-256' };
  if (oidEq(oid, OID_RSA_SHA384)) return { type: 'RSA', hash: 'SHA-384' };
  if (oidEq(oid, OID_RSA_SHA512)) return { type: 'RSA', hash: 'SHA-512' };
  throw new Error('unsupported signature algorithm OID');
}

/** DER ECDSA-Sig-Value SEQUENCE { r, s } → fixed-width r||s for the curve. */
export function ecdsaDerToRaw(derSig: Uint8Array, coordLen: number): Uint8Array {
  const node = decodeDer(derSig);
  if (!node.children || node.children.length < 2) throw new Error('bad ECDSA sig');
  const r = node.children[0].value;
  const s = node.children[1].value;
  const out = new Uint8Array(coordLen * 2);
  // strip leading zero padding from integers, then left-pad to coordLen
  const copy = (src: Uint8Array, destOff: number) => {
    let i = 0;
    while (i < src.length - 1 && src[i] === 0) i++;
    const useful = src.subarray(i);
    if (useful.length > coordLen) throw new Error('ECDSA coord too large');
    out.set(useful, destOff + (coordLen - useful.length));
  };
  copy(r, 0);
  copy(s, coordLen);
  return out;
}

function authListHasTag(list: DerNode | undefined, tag: number): boolean {
  if (!list) return false;
  // Only direct / nested context tags inside this list — walk this subtree only
  let found = false;
  walk(list, (n) => {
    if (n.tagClass === 2 && n.tagNumber === tag) found = true;
  });
  return found;
}

export function parseKeyDescription(extValue: Uint8Array): {
  attestationSecurityLevel: number;
  challenge: Uint8Array;
  softwareEnforced: DerNode | undefined;
  teeEnforced: DerNode | undefined;
} {
  const node = decodeDer(extValue);
  if (!node.children || node.children.length < 5) throw new Error('KeyDescription structure');
  const attestationSecurityLevel = readInt(node.children[1]);
  const challenge = node.children[4].value;
  // softwareEnforced / teeEnforced are the last two SEQUENCE fields typically at indices 6 and 7
  // Structure: version, secLevel, kmVersion, kmSecLevel, challenge, uniqueId, softwareEnforced, teeEnforced
  const softwareEnforced = node.children[6];
  const teeEnforced = node.children[7];
  return { attestationSecurityLevel, challenge, softwareEnforced, teeEnforced };
}

function findKeyDescriptionExtension(cert: ParsedCert): Uint8Array | null {
  const kids = cert.tbsNode.children ?? [];
  const extContainer = kids.find((c) => c.tagClass === 2 && c.tagNumber === 3);
  if (!extContainer) return null;
  const extensions = extContainer.children?.[0] ?? extContainer;
  for (const ext of extensions.children ?? []) {
    if (!ext.children || ext.children.length < 2) continue;
    if (ext.children[0].tagNumber !== 6) continue;
    const oid = decodeOid(ext.children[0].value);
    if (!oidEq(oid, KEY_DESCRIPTION_OID)) continue;
    const valNode = ext.children[ext.children.length - 1];
    return valNode.tagNumber === 4 ? valNode.value : valNode.value;
  }
  return null;
}

function securityLevelName(level: number): SecurityLevel {
  if (level === 0) return 'Software';
  if (level === 1) return 'TrustedEnvironment';
  if (level === 2) return 'StrongBox';
  return 'Unknown';
}

function extractKeyBitsFromSpki(spki: Uint8Array): Uint8Array {
  try {
    const n = decodeDer(spki);
    const bit = n.children?.[n.children.length - 1];
    if (bit && bit.tagNumber === 3 && bit.value.length > 0) return bit.value.subarray(1);
  } catch {
    /* fall through */
  }
  return spki;
}

export async function verifyAttestation(
  input: AttestationVerifyInput
): Promise<AttestationResult> {
  try {
    if (!input.chain || input.chain.length < 2) {
      return fail('attestation chain must contain at least 2 certificates');
    }

    const certs = input.chain.map((c) => b64ToBytes(c));
    const parsed = certs.map(parseCertificate);
    const now = input.now ?? Math.floor(Date.now() / 1000);
    const nowDate = new Date(now * 1000);

    // Validity
    for (const p of parsed) {
      if (nowDate < p.notBefore || nowDate > p.notAfter) {
        return fail('certificate validity window failed');
      }
    }

    // Leaf must not be CA; issuers must be CA
    if (parsed[0].isCA) return fail('leaf certificate must not be a CA');
    for (let i = 1; i < parsed.length; i++) {
      if (!parsed[i].isCA) return fail('issuer certificate must have basicConstraints CA:TRUE');
      if (parsed[i].hasKeyUsage && !parsed[i].keyCertSign) {
        return fail('issuer keyUsage missing keyCertSign');
      }
    }
    // pathLen: for each CA at index i>0, number of following CAs toward leaf must be <= pathLen
    // Chain: leaf(0), intermediate(1), ... root(n-1). For cert at i, remaining CA path length is i-1.
    for (let i = 1; i < parsed.length; i++) {
      if (parsed[i].pathLen !== null && parsed[i].pathLen !== undefined) {
        const remainingCas = i - 1; // CAs strictly between this cert and leaf
        if (remainingCas > (parsed[i].pathLen as number)) {
          return fail('basicConstraints pathLen violated');
        }
      }
    }

    // issuer == parent subject
    for (let i = 0; i < parsed.length - 1; i++) {
      if (!bytesEqual(parsed[i].issuerTlv, parsed[i + 1].subjectTlv)) {
        return fail('child issuer does not match parent subject');
      }
    }

    // Verify signatures leaf→root
    for (let i = 0; i < parsed.length - 1; i++) {
      const child = parsed[i];
      const parent = parsed[i + 1];
      const parentKey = parseParentKeyAlg(parent.spkiTlv);
      const sigAlg = parseSigAlg(child.signatureAlgoOid);
      if (sigAlg.type !== parentKey.type) {
        return fail('signature algorithm does not match parent key type');
      }

      let key: CryptoKey;
      if (parentKey.type === 'ECDSA') {
        key = await crypto.subtle.importKey(
          'spki',
          parent.spkiTlv,
          { name: 'ECDSA', namedCurve: parentKey.curve! },
          false,
          ['verify']
        );
        const coordLen = parentKey.curve === 'P-384' ? 48 : 32;
        const rawSig = ecdsaDerToRaw(child.signatureRaw, coordLen);
        const ok = await crypto.subtle.verify(
          { name: 'ECDSA', hash: sigAlg.hash },
          key,
          rawSig,
          child.tbsTlv
        );
        if (!ok) return fail('certificate chain signature verification failed');
      } else {
        key = await crypto.subtle.importKey(
          'spki',
          parent.spkiTlv,
          { name: 'RSASSA-PKCS1-v1_5', hash: sigAlg.hash },
          false,
          ['verify']
        );
        const ok = await crypto.subtle.verify(
          { name: 'RSASSA-PKCS1-v1_5' },
          key,
          child.signatureRaw,
          child.tbsTlv
        );
        if (!ok) return fail('certificate chain signature verification failed');
      }
    }

    // Root trust
    const rootHash = await sha256Hex(certs[certs.length - 1]);
    if (input.mode === 'strict') {
      if (!input.trustedRootSha256.map((h) => h.toLowerCase()).includes(rootHash)) {
        return fail('attestation root is not trusted');
      }
    }

    // KeyDescription on leaf
    const kdRaw = findKeyDescriptionExtension(parsed[0]);
    if (!kdRaw) return fail('KeyDescription extension missing on leaf');
    const kd = parseKeyDescription(kdRaw);

    if (!bytesEqual(kd.challenge, input.challenge)) {
      return fail('attestation challenge mismatch');
    }

    const secLevel = securityLevelName(kd.attestationSecurityLevel);
    if (input.mode === 'strict') {
      if (secLevel !== 'TrustedEnvironment' && secLevel !== 'StrongBox') {
        return fail('security level insufficient for strict mode');
      }
    }

    // Leaf key vs submitted
    const submitted = b64ToBytes(input.publicKey);
    const submittedBits =
      submitted.length > 0 && submitted[0] === 0x30
        ? extractKeyBitsFromSpki(submitted)
        : submitted;
    if (
      !bytesEqual(parsed[0].spkiKeyBits, submittedBits) &&
      !bytesEqual(parsed[0].spkiKeyBits, submitted)
    ) {
      return fail('leaf public key does not match submitted publicKey');
    }

    // Tier derivation — only the two authorization lists
    const noAuth =
      authListHasTag(kd.softwareEnforced, TAG_NO_AUTH_REQUIRED) ||
      authListHasTag(kd.teeEnforced, TAG_NO_AUTH_REQUIRED);
    let derived: AuthTier;
    if (noAuth) {
      derived = 'in_app_confirm';
    } else {
      // hardware_confirm requires userAuthType (504) in teeEnforced
      if (!authListHasTag(kd.teeEnforced, TAG_USER_AUTH_TYPE)) {
        return fail('hardware_confirm requires userAuthType (504) in teeEnforced');
      }
      derived = 'hardware_confirm';
    }

    if (derived !== input.claimedAuthTier) {
      return fail('claimed authTier does not match attestation');
    }

    if (!input.allowedAuthTiers.includes(derived)) {
      return {
        ok: false,
        code: 'AUTH_TIER_NOT_ALLOWED',
        message: 'derived auth tier is not permitted in this environment',
      };
    }

    return {
      ok: true,
      authTier: derived,
      leafCertSha256: await sha256Hex(certs[0]),
      publicKeySpkiBase64Url: bytesToBase64Url(
        submitted[0] === 0x30 ? submitted : parsed[0].spkiTlv
      ),
      securityLevel: secLevel,
    };
  } catch (e) {
    return fail(e instanceof Error ? e.message : 'attestation verification failed');
  }
}
