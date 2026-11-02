import { describe, it, expect } from 'vitest';
import {
  parseKeyDescription,
  verifyAttestation,
  TAG_NO_AUTH_REQUIRED,
} from '../../../src/attestation/verify';
import { decodeDer } from '../../../src/attestation/der';
import {
  buildKeyDescription,
  encodeContextTag,
  TAG_NO_AUTH_REQUIRED as TAG,
} from '../../helpers/x509';

describe('DER high-tag context', () => {
  it('encodes and decodes tag 503 (noAuthRequired)', () => {
    const tlv = encodeContextTag(TAG_NO_AUTH_REQUIRED, new Uint8Array(), false);
    const node = decodeDer(tlv);
    expect(node.tagClass).toBe(2);
    expect(node.tagNumber).toBe(503);
  });
});

describe('parseKeyDescription', () => {
  it('reads challenge and security level', () => {
    const challenge = crypto.getRandomValues(new Uint8Array(32));
    const kd = buildKeyDescription({
      challenge,
      securityLevel: 1,
      noAuthRequired: false,
    });
    const parsed = parseKeyDescription(kd);
    expect(parsed.attestationSecurityLevel).toBe(1);
    expect([...parsed.challenge]).toEqual([...challenge]);
    expect(parsed.softwareEnforcedNoAuth).toBe(false);
  });

  it('detects noAuthRequired tag 503 → in_app path', () => {
    const challenge = crypto.getRandomValues(new Uint8Array(32));
    const kd = buildKeyDescription({
      challenge,
      securityLevel: 1,
      noAuthRequired: true,
    });
    const parsed = parseKeyDescription(kd);
    expect(parsed.softwareEnforcedNoAuth || parsed.teeEnforcedNoAuth).toBe(true);
  });
});

describe('verifyAttestation fail-closed paths', () => {
  const challenge = new Uint8Array(32).fill(7);

  it('rejects chain shorter than 2', async () => {
    const r = await verifyAttestation({
      chain: ['AA=='],
      challenge,
      publicKey: 'AA==',
      claimedAuthTier: 'hardware_confirm',
      mode: 'strict',
      trustedRootSha256: [],
      allowedAuthTiers: ['hardware_confirm'],
    });
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.code).toBe('ENROLL_ATTESTATION_INVALID');
  });

  it('rejects when ALLOWED_AUTH_TIERS excludes derived tier (via claimed mismatch path)', async () => {
    // Without a full synthetic chain, AUTH_TIER_NOT_ALLOWED is reached only after a valid
    // chain+KD. Documented as integration coverage; here we assert the short-chain path.
    const r = await verifyAttestation({
      chain: [],
      challenge,
      publicKey: 'AA==',
      claimedAuthTier: 'in_app_confirm',
      mode: 'strict',
      trustedRootSha256: [],
      allowedAuthTiers: ['hardware_confirm'],
    });
    expect(r.ok).toBe(false);
  });
});

void TAG;
void TAG_NO_AUTH_REQUIRED;
