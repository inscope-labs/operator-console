/**
 * Synthetic signed X.509 chains for attestation unit tests.
 * ECDSA P-256 / P-384 and RSA parents; basicConstraints; KeyDescription.
 */

import {
  encodeTlv,
  encodeOid,
  encodeContextTag,
  concatBytes,
} from '../../src/attestation/der';
import {
  TAG_NO_AUTH_REQUIRED,
  TAG_USER_AUTH_TYPE,
} from '../../src/attestation/verify';

function intBytes(n: number): Uint8Array {
  if (n === 0) return new Uint8Array([0]);
  const out: number[] = [];
  let v = n >>> 0;
  while (v > 0) {
    out.unshift(v & 0xff);
    v >>>= 8;
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

function encodeBool(v: boolean): Uint8Array {
  return encodeTlv(0x01, new Uint8Array([v ? 0xff : 0x00]));
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

function explicitCtx(tag: number, content: Uint8Array): Uint8Array {
  // [tag] EXPLICIT — constructed context-specific
  return encodeContextTag(tag, content, true);
}

function nameCN(cn: string): Uint8Array {
  // RDNSequence: SET of SEQUENCE { OID commonName, UTF8String }
  const oid = encodeTlv(0x06, encodeOid([2, 5, 4, 3]));
  const val = encodeTlv(0x0c, new TextEncoder().encode(cn));
  const atv = seq(oid, val);
  const rdn = encodeTlv(0x31, atv, true); // SET
  return seq(rdn);
}

function algorithmId(oid: number[], param?: Uint8Array): Uint8Array {
  const parts = [encodeTlv(0x06, encodeOid(oid))];
  if (param) parts.push(param);
  else parts.push(encodeTlv(0x05, new Uint8Array())); // NULL for RSA
  return seq(...parts);
}

function spkiFromExport(spki: Uint8Array): Uint8Array {
  return spki; // already DER SPKI
}

function basicConstraintsExt(isCA: boolean, pathLen?: number): Uint8Array {
  const fields: Uint8Array[] = [encodeBool(isCA)];
  if (isCA && pathLen !== undefined) fields.push(encodeInt(pathLen));
  const bc = seq(...fields);
  const oid = encodeTlv(0x06, encodeOid([2, 5, 29, 19]));
  // critical BOOLEAN TRUE
  const critical = encodeBool(true);
  const val = encodeOctet(bc);
  return seq(oid, critical, val);
}

function keyUsageCertSignExt(): Uint8Array {
  const oid = encodeTlv(0x06, encodeOid([2, 5, 29, 15]));
  // BIT STRING: unused bits 2, value 0x04 (keyCertSign bit 5) → actually
  // bits MSB first: digitalSignature(0)... keyCertSign(5) → byte 0x04
  const bitString = encodeTlv(0x03, new Uint8Array([0x02, 0x04]));
  return seq(oid, encodeBool(true), encodeOctet(bitString));
}

export function buildKeyDescription(opts: {
  challenge: Uint8Array;
  securityLevel: number;
  noAuthRequired?: boolean;
  userAuthType?: boolean;
}): Uint8Array {
  const softParts: Uint8Array[] = [];
  const teeParts: Uint8Array[] = [];
  if (opts.noAuthRequired) {
    softParts.push(encodeContextTag(TAG_NO_AUTH_REQUIRED, new Uint8Array(), false));
  }
  if (opts.userAuthType) {
    // userAuthType INTEGER — put a dummy integer content
    teeParts.push(encodeContextTag(TAG_USER_AUTH_TYPE, intBytes(1), false));
  }
  const softwareEnforced = seq(...softParts);
  const teeEnforced = seq(...teeParts);
  return seq(
    encodeInt(200),
    encodeInt(opts.securityLevel),
    encodeInt(41),
    encodeInt(opts.securityLevel),
    encodeOctet(opts.challenge),
    encodeOctet(new Uint8Array(0)), // uniqueId
    softwareEnforced,
    teeEnforced
  );
}

function keyDescriptionExt(kd: Uint8Array): Uint8Array {
  const oid = encodeTlv(0x06, encodeOid([1, 3, 6, 1, 4, 1, 11129, 2, 1, 17]));
  return seq(oid, encodeOctet(kd));
}

function extensionsSeq(...exts: Uint8Array[]): Uint8Array {
  return explicitCtx(3, seq(...exts));
}

async function signEcdsa(
  tbs: Uint8Array,
  key: CryptoKey,
  hash: 'SHA-256' | 'SHA-384'
): Promise<Uint8Array> {
  // WebCrypto returns raw r||s; convert to DER SEQUENCE { INTEGER r, INTEGER s }
  const raw = new Uint8Array(
    await crypto.subtle.sign({ name: 'ECDSA', hash }, key, tbs)
  );
  const half = raw.length / 2;
  const r = raw.subarray(0, half);
  const s = raw.subarray(half);
  const encInt = (x: Uint8Array) => {
    let i = 0;
    while (i < x.length - 1 && x[i] === 0) i++;
    let v = x.subarray(i);
    if (v[0] & 0x80) v = concatBytes(new Uint8Array([0]), v);
    return encodeTlv(0x02, v);
  };
  return seq(encInt(r), encInt(s));
}

async function signRsa(
  tbs: Uint8Array,
  key: CryptoKey,
  _hash: 'SHA-256' | 'SHA-384' | 'SHA-512'
): Promise<Uint8Array> {
  return new Uint8Array(
    await crypto.subtle.sign({ name: 'RSASSA-PKCS1-v1_5' }, key, tbs)
  );
}

function bitString(sig: Uint8Array): Uint8Array {
  return encodeTlv(0x03, concatBytes(new Uint8Array([0]), sig));
}

export type CurveName = 'P-256' | 'P-384';

export async function generateEcPair(curve: CurveName = 'P-256'): Promise<CryptoKeyPair> {
  return (await crypto.subtle.generateKey(
    { name: 'ECDSA', namedCurve: curve },
    true,
    ['sign', 'verify']
  )) as CryptoKeyPair;
}

export async function generateRsaPair(): Promise<CryptoKeyPair> {
  return (await crypto.subtle.generateKey(
    {
      name: 'RSASSA-PKCS1-v1_5',
      modulusLength: 2048,
      publicExponent: new Uint8Array([1, 0, 1]),
      hash: 'SHA-256',
    },
    true,
    ['sign', 'verify']
  )) as CryptoKeyPair;
}

export async function exportSpki(key: CryptoKey): Promise<Uint8Array> {
  return new Uint8Array(await crypto.subtle.exportKey('spki', key) as ArrayBuffer);
}

export function toBase64(data: Uint8Array): string {
  let s = '';
  for (let i = 0; i < data.length; i++) s += String.fromCharCode(data[i]);
  return btoa(s);
}

export async function sha256Hex(data: Uint8Array): Promise<string> {
  const dig = await crypto.subtle.digest('SHA-256', data);
  return [...new Uint8Array(dig)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

export interface BuildCertOpts {
  subjectCn: string;
  issuerCn: string;
  subjectSpki: Uint8Array;
  notBefore: Date;
  notAfter: Date;
  isCA: boolean;
  pathLen?: number;
  keyDescription?: Uint8Array;
  /** Parent private key for signing */
  issuerKey: CryptoKey;
  issuerKeyType: 'ECDSA' | 'RSA';
  curve?: CurveName;
  hash?: 'SHA-256' | 'SHA-384';
}

export async function buildCert(opts: BuildCertOpts): Promise<Uint8Array> {
  const version = explicitCtx(0, encodeInt(2)); // v3
  const serial = encodeInt(Math.floor(Math.random() * 1e9) + 1);
  const hash = opts.hash ?? 'SHA-256';
  let sigAlg: Uint8Array;
  if (opts.issuerKeyType === 'ECDSA') {
    const oid =
      hash === 'SHA-384'
        ? [1, 2, 840, 10045, 4, 3, 3]
        : [1, 2, 840, 10045, 4, 3, 2];
    sigAlg = seq(encodeTlv(0x06, encodeOid(oid)));
  } else {
    const oid =
      hash === 'SHA-384'
        ? [1, 2, 840, 113549, 1, 1, 12]
        : [1, 2, 840, 113549, 1, 1, 11];
    sigAlg = algorithmId(oid);
  }
  const issuer = nameCN(opts.issuerCn);
  const subject = nameCN(opts.subjectCn);
  const validity = seq(encodeUtc(opts.notBefore), encodeUtc(opts.notAfter));
  const spki = spkiFromExport(opts.subjectSpki);

  const exts: Uint8Array[] = [];
  if (opts.isCA) {
    exts.push(basicConstraintsExt(true, opts.pathLen));
    exts.push(keyUsageCertSignExt());
  } else {
    exts.push(basicConstraintsExt(false));
  }
  if (opts.keyDescription) {
    exts.push(keyDescriptionExt(opts.keyDescription));
  }
  const extensions = extensionsSeq(...exts);

  const tbs = seq(version, serial, sigAlg, issuer, validity, subject, spki, extensions);

  let signature: Uint8Array;
  if (opts.issuerKeyType === 'ECDSA') {
    signature = await signEcdsa(tbs, opts.issuerKey, hash);
  } else {
    signature = await signRsa(tbs, opts.issuerKey, hash);
  }
  return seq(tbs, sigAlg, bitString(signature));
}

export interface ChainBundle {
  chainB64: string[];
  leafSpkiB64: string;
  rootDer: Uint8Array;
  rootSha256: string;
  leafKey: CryptoKeyPair;
  challenge: Uint8Array;
}

/**
 * Build leaf (attested, non-CA) + root (CA) chain.
 * default: hardware_confirm with TEE + userAuthType.
 */
export async function buildValidAttestationChain(
  over: {
    challenge?: Uint8Array;
    securityLevel?: number;
    noAuthRequired?: boolean;
    userAuthType?: boolean;
    leafIsCA?: boolean;
    rootIsCA?: boolean;
    notBefore?: Date;
    notAfter?: Date;
    curve?: CurveName;
    parentRsa?: boolean;
  } = {}
): Promise<ChainBundle> {
  const challenge = over.challenge ?? crypto.getRandomValues(new Uint8Array(32));
  const now = Date.now();
  const notBefore = over.notBefore ?? new Date(now - 3600_000);
  const notAfter = over.notAfter ?? new Date(now + 86400_000 * 365);
  const curve = over.curve ?? 'P-256';
  const securityLevel = over.securityLevel ?? 1;
  const noAuthRequired = over.noAuthRequired ?? false;
  const userAuthType = over.userAuthType ?? !noAuthRequired;

  const leafKey = await generateEcPair(curve);
  const leafSpki = await exportSpki(leafKey.publicKey);

  let parentKey: CryptoKeyPair;
  let issuerKeyType: 'ECDSA' | 'RSA';
  if (over.parentRsa) {
    parentKey = await generateRsaPair();
    issuerKeyType = 'RSA';
  } else {
    parentKey = await generateEcPair(curve);
    issuerKeyType = 'ECDSA';
  }
  const rootSpki = await exportSpki(parentKey.publicKey);

  const kd = buildKeyDescription({
    challenge,
    securityLevel,
    noAuthRequired,
    userAuthType,
  });

  const root = await buildCert({
    subjectCn: 'Test Root',
    issuerCn: 'Test Root',
    subjectSpki: rootSpki,
    notBefore,
    notAfter,
    isCA: over.rootIsCA ?? true,
    pathLen: 1,
    issuerKey: parentKey.privateKey,
    issuerKeyType,
    curve,
  });

  const leaf = await buildCert({
    subjectCn: 'Attested Leaf',
    issuerCn: 'Test Root',
    subjectSpki: leafSpki,
    notBefore,
    notAfter,
    isCA: over.leafIsCA ?? false,
    keyDescription: kd,
    issuerKey: parentKey.privateKey,
    issuerKeyType,
    curve,
  });

  return {
    chainB64: [toBase64(leaf), toBase64(root)],
    leafSpkiB64: toBase64(leafSpki),
    rootDer: root,
    rootSha256: await sha256Hex(root),
    leafKey,
    challenge,
  };
}

export { TAG_NO_AUTH_REQUIRED, TAG_USER_AUTH_TYPE, encodeContextTag, seq, encodeInt, encodeOctet, encodeUtc };
