import { describe, it, expect } from 'vitest';
import { parseGatewayConfig, isAuthTierAllowed } from '../../../src/config/env';

describe('parseGatewayConfig', () => {
  it('defaults production tiers and strict mode when ENVIRONMENT=production', () => {
    const r = parseGatewayConfig({ ENVIRONMENT: 'production' });
    expect(r.ok).toBe(true);
    if (r.ok) {
      expect(r.config.environment).toBe('production');
      expect(r.config.attestationMode).toBe('strict');
      expect(r.config.allowedAuthTiers).toEqual(['hardware_confirm']);
    }
  });

  it('defaults both tiers when ENVIRONMENT=test', () => {
    const r = parseGatewayConfig({ ENVIRONMENT: 'test' });
    expect(r.ok).toBe(true);
    if (r.ok) {
      expect(r.config.allowedAuthTiers).toEqual([
        'hardware_confirm',
        'in_app_confirm',
      ]);
    }
  });

  it('rejects unknown ENVIRONMENT', () => {
    const r = parseGatewayConfig({ ENVIRONMENT: 'staging' });
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.problems).toContain('ENVIRONMENT_INVALID');
  });

  it('refuses lenient attestation in production', () => {
    const r = parseGatewayConfig({
      ENVIRONMENT: 'production',
      ATTESTATION_MODE: 'lenient',
    });
    expect(r.ok).toBe(false);
    if (!r.ok) {
      expect(r.problems).toContain('ATTESTATION_MODE_REFUSED_IN_PRODUCTION');
    }
  });

  it('allows lenient in test', () => {
    const r = parseGatewayConfig({
      ENVIRONMENT: 'test',
      ATTESTATION_MODE: 'lenient',
    });
    expect(r.ok).toBe(true);
    if (r.ok) expect(r.config.attestationMode).toBe('lenient');
  });

  it('parses ATTESTATION_ROOT_SHA256 hex array', () => {
    const hex = 'a'.repeat(64);
    const r = parseGatewayConfig({
      ENVIRONMENT: 'test',
      ATTESTATION_ROOT_SHA256: JSON.stringify([hex]),
    });
    expect(r.ok).toBe(true);
    if (r.ok) expect(r.config.attestationRootSha256).toEqual([hex]);
  });

  it('rejects malformed root fingerprints', () => {
    const r = parseGatewayConfig({
      ENVIRONMENT: 'test',
      ATTESTATION_ROOT_SHA256: JSON.stringify(['not-hex']),
    });
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.problems).toContain('ATTESTATION_ROOT_SHA256_INVALID');
  });

  it('isAuthTierAllowed respects config', () => {
    const r = parseGatewayConfig({ ENVIRONMENT: 'production' });
    expect(r.ok).toBe(true);
    if (r.ok) {
      expect(isAuthTierAllowed(r.config, 'hardware_confirm')).toBe(true);
      expect(isAuthTierAllowed(r.config, 'in_app_confirm')).toBe(false);
    }
  });
});
