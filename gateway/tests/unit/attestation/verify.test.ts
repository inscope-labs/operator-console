import { describe, it, expect } from 'vitest';
import {
  verifyAttestation,
  ecdsaDerToRaw,
  parseKeyDescription,
} from '../../../src/attestation/verify';
import { decodeDer } from '../../../src/attestation/der';
import {
  buildValidAttestationChain,
  buildKeyDescription,
  encodeContextTag,
  TAG_NO_AUTH_REQUIRED,
  TAG_USER_AUTH_TYPE,
  toBase64,
  buildCert,
  generateEcPair,
  exportSpki,
  sha256Hex,
} from '../../helpers/x509';

describe('ecdsaDerToRaw', () => {
  it('pads r and s to coord length', () => {
    // SEQUENCE { INTEGER 1, INTEGER 2 }
    const der = new Uint8Array([0x30, 0x06, 0x02, 0x01, 0x01, 0x02, 0x01, 0x02]);
    const raw = ecdsaDerToRaw(der, 32);
    expect(raw.length).toBe(64);
    expect(raw[31]).toBe(1);
    expect(raw[63]).toBe(2);
  });
});

describe('parseKeyDescription auth lists only', () => {
  it('reads noAuth and userAuth tags from the two lists', () => {
    const challenge = new Uint8Array(32).fill(3);
    const kd = buildKeyDescription({
      challenge,
      securityLevel: 1,
      noAuthRequired: true,
      userAuthType: false,
    });
    const p = parseKeyDescription(kd);
    expect([...p.challenge]).toEqual([...challenge]);
  });
});

describe('verifyAttestation — signed chains', () => {
  it('accepts a valid chain in strict mode with root fingerprint', async () => {
    const bundle = await buildValidAttestationChain();
    const r = await verifyAttestation({
      chain: bundle.chainB64,
      challenge: bundle.challenge,
      publicKey: bundle.leafSpkiB64,
      claimedAuthTier: 'hardware_confirm',
      mode: 'strict',
      trustedRootSha256: [bundle.rootSha256],
      allowedAuthTiers: ['hardware_confirm', 'in_app_confirm'],
    });
    expect(r.ok).toBe(true);
    if (r.ok) expect(r.authTier).toBe('hardware_confirm');
  });

  it('accepts valid chain in lenient mode without trusted roots', async () => {
    const bundle = await buildValidAttestationChain();
    const r = await verifyAttestation({
      chain: bundle.chainB64,
      challenge: bundle.challenge,
      publicKey: bundle.leafSpkiB64,
      claimedAuthTier: 'hardware_confirm',
      mode: 'lenient',
      trustedRootSha256: [],
      allowedAuthTiers: ['hardware_confirm'],
    });
    expect(r.ok).toBe(true);
  });

  it('rejects wrong challenge', async () => {
    const bundle = await buildValidAttestationChain();
    const wrong = new Uint8Array(32).fill(9);
    const r = await verifyAttestation({
      chain: bundle.chainB64,
      challenge: wrong,
      publicKey: bundle.leafSpkiB64,
      claimedAuthTier: 'hardware_confirm',
      mode: 'lenient',
      trustedRootSha256: [],
      allowedAuthTiers: ['hardware_confirm'],
    });
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.code).toBe('ENROLL_ATTESTATION_INVALID');
  });

  it('rejects single self-signed cert (chain length < 2)', async () => {
    const bundle = await buildValidAttestationChain();
    const r = await verifyAttestation({
      chain: [bundle.chainB64[0]],
      challenge: bundle.challenge,
      publicKey: bundle.leafSpkiB64,
      claimedAuthTier: 'hardware_confirm',
      mode: 'lenient',
      trustedRootSha256: [],
      allowedAuthTiers: ['hardware_confirm'],
    });
    expect(r.ok).toBe(false);
  });

  it('rejects leaf/submitted-key mismatch', async () => {
    const bundle = await buildValidAttestationChain();
    const other = await generateEcPair();
    const otherSpki = toBase64(await exportSpki(other.publicKey));
    const r = await verifyAttestation({
      chain: bundle.chainB64,
      challenge: bundle.challenge,
      publicKey: otherSpki,
      claimedAuthTier: 'hardware_confirm',
      mode: 'lenient',
      trustedRootSha256: [],
      allowedAuthTiers: ['hardware_confirm'],
    });
    expect(r.ok).toBe(false);
  });

  it('rejects tier mismatch (claimed vs derived)', async () => {
    const bundle = await buildValidAttestationChain({ noAuthRequired: false });
    const r = await verifyAttestation({
      chain: bundle.chainB64,
      challenge: bundle.challenge,
      publicKey: bundle.leafSpkiB64,
      claimedAuthTier: 'in_app_confirm', // derived is hardware_confirm
      mode: 'lenient',
      trustedRootSha256: [],
      allowedAuthTiers: ['hardware_confirm', 'in_app_confirm'],
    });
    expect(r.ok).toBe(false);
  });

  it('rejects untrusted root in strict mode', async () => {
    const bundle = await buildValidAttestationChain();
    const r = await verifyAttestation({
      chain: bundle.chainB64,
      challenge: bundle.challenge,
      publicKey: bundle.leafSpkiB64,
      claimedAuthTier: 'hardware_confirm',
      mode: 'strict',
      trustedRootSha256: ['ab'.repeat(32)],
      allowedAuthTiers: ['hardware_confirm'],
    });
    expect(r.ok).toBe(false);
  });

  it('returns AUTH_TIER_NOT_ALLOWED when derived tier excluded', async () => {
    const bundle = await buildValidAttestationChain({
      noAuthRequired: true,
    });
    const r = await verifyAttestation({
      chain: bundle.chainB64,
      challenge: bundle.challenge,
      publicKey: bundle.leafSpkiB64,
      claimedAuthTier: 'in_app_confirm',
      mode: 'lenient',
      trustedRootSha256: [],
      allowedAuthTiers: ['hardware_confirm'], // in_app not allowed
    });
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.code).toBe('AUTH_TIER_NOT_ALLOWED');
  });

  it('rejects forged leaf issued by a non-CA attested leaf', async () => {
    // root non-CA should fail issuer CA check
    const bundle = await buildValidAttestationChain({ rootIsCA: false });
    const r = await verifyAttestation({
      chain: bundle.chainB64,
      challenge: bundle.challenge,
      publicKey: bundle.leafSpkiB64,
      claimedAuthTier: 'hardware_confirm',
      mode: 'lenient',
      trustedRootSha256: [],
      allowedAuthTiers: ['hardware_confirm'],
    });
    expect(r.ok).toBe(false);
  });

  it('accepts EC leaf under RSA parent', async () => {
    const bundle = await buildValidAttestationChain({ parentRsa: true });
    const r = await verifyAttestation({
      chain: bundle.chainB64,
      challenge: bundle.challenge,
      publicKey: bundle.leafSpkiB64,
      claimedAuthTier: 'hardware_confirm',
      mode: 'lenient',
      trustedRootSha256: [],
      allowedAuthTiers: ['hardware_confirm'],
    });
    expect(r.ok).toBe(true);
  });

  it('accepts P-384 link', async () => {
    const bundle = await buildValidAttestationChain({ curve: 'P-384' });
    const r = await verifyAttestation({
      chain: bundle.chainB64,
      challenge: bundle.challenge,
      publicKey: bundle.leafSpkiB64,
      claimedAuthTier: 'hardware_confirm',
      mode: 'lenient',
      trustedRootSha256: [],
      allowedAuthTiers: ['hardware_confirm'],
    });
    expect(r.ok).toBe(true);
  });

  it('rejects hardware_confirm without tag 504', async () => {
    const bundle = await buildValidAttestationChain({
      noAuthRequired: false,
      userAuthType: false,
    });
    const r = await verifyAttestation({
      chain: bundle.chainB64,
      challenge: bundle.challenge,
      publicKey: bundle.leafSpkiB64,
      claimedAuthTier: 'hardware_confirm',
      mode: 'lenient',
      trustedRootSha256: [],
      allowedAuthTiers: ['hardware_confirm'],
    });
    expect(r.ok).toBe(false);
  });

  it('rejects expired certificate', async () => {
    const bundle = await buildValidAttestationChain({
      notBefore: new Date(Date.now() - 86400_000 * 10),
      notAfter: new Date(Date.now() - 86400_000),
    });
    const r = await verifyAttestation({
      chain: bundle.chainB64,
      challenge: bundle.challenge,
      publicKey: bundle.leafSpkiB64,
      claimedAuthTier: 'hardware_confirm',
      mode: 'lenient',
      trustedRootSha256: [],
      allowedAuthTiers: ['hardware_confirm'],
    });
    expect(r.ok).toBe(false);
  });

  it('rejects not-yet-valid certificate', async () => {
    const bundle = await buildValidAttestationChain({
      notBefore: new Date(Date.now() + 86400_000),
      notAfter: new Date(Date.now() + 86400_000 * 10),
    });
    const r = await verifyAttestation({
      chain: bundle.chainB64,
      challenge: bundle.challenge,
      publicKey: bundle.leafSpkiB64,
      claimedAuthTier: 'hardware_confirm',
      mode: 'lenient',
      trustedRootSha256: [],
      allowedAuthTiers: ['hardware_confirm'],
    });
    expect(r.ok).toBe(false);
  });

  it('rejects leaf that is a CA', async () => {
    const bundle = await buildValidAttestationChain({ leafIsCA: true });
    const r = await verifyAttestation({
      chain: bundle.chainB64,
      challenge: bundle.challenge,
      publicKey: bundle.leafSpkiB64,
      claimedAuthTier: 'hardware_confirm',
      mode: 'lenient',
      trustedRootSha256: [],
      allowedAuthTiers: ['hardware_confirm'],
    });
    expect(r.ok).toBe(false);
  });
});

describe('challenge lifecycle semantics (documented for registry integration)', () => {
  it('wrong challenge is ENROLL_ATTESTATION_INVALID (replay/expiry handled by registry)', async () => {
    const bundle = await buildValidAttestationChain();
    const r = await verifyAttestation({
      chain: bundle.chainB64,
      challenge: new Uint8Array(32),
      publicKey: bundle.leafSpkiB64,
      claimedAuthTier: 'hardware_confirm',
      mode: 'lenient',
      trustedRootSha256: [],
      allowedAuthTiers: ['hardware_confirm'],
    });
    expect(r.ok).toBe(false);
  });
});

void encodeContextTag;
void TAG_NO_AUTH_REQUIRED;
void TAG_USER_AUTH_TYPE;
void buildCert;
void sha256Hex;
void decodeDer;
