import { describe, it, expect } from 'vitest';
import { parseGatewayConfig, isAuthTierAllowed } from '../../../src/config/env';

describe('parseGatewayConfig (Stage 1.2 — Environment model)', () => {
  it('defaults production tiers and strict mode when ENVIRONMENT=production', () => {
    const r = parseGatewayConfig({ ENVIRONMENT: 'production' });
    expect(r.ok).toBe(true);
    if (r.ok) {
      expect(r.config.environment).toBe('production');
      expect(r.config.attestationMode).toBe('strict');
      expect(r.config.allowedAuthTiers).toEqual(['hardware_confirm']);
    }
  });

  it('accepts ENVIRONMENT=staging with strict mode and both auth tiers', () => {
    const r = parseGatewayConfig({ ENVIRONMENT: 'staging' });
    expect(r.ok).toBe(true);
    if (r.ok) {
      expect(r.config.environment).toBe('staging');
      expect(r.config.attestationMode).toBe('strict');
      expect(r.config.allowedAuthTiers).toEqual([
        'hardware_confirm',
        'in_app_confirm',
      ]);
    }
  });

  it('defaults both tiers when ENVIRONMENT=test', () => {
    const r = parseGatewayConfig({ ENVIRONMENT: 'test' });
    expect(r.ok).toBe(true);
    if (r.ok) {
      expect(r.config.environment).toBe('test');
      expect(r.config.allowedAuthTiers).toEqual([
        'hardware_confirm',
        'in_app_confirm',
      ]);
    }
  });

  it('rejects unknown ENVIRONMENT (e.g. development, local)', () => {
    const r1 = parseGatewayConfig({ ENVIRONMENT: 'development' });
    expect(r1.ok).toBe(false);
    if (!r1.ok) expect(r1.problems).toContain('ENVIRONMENT_INVALID');

    const r2 = parseGatewayConfig({ ENVIRONMENT: 'local' });
    expect(r2.ok).toBe(false);
    if (!r2.ok) expect(r2.problems).toContain('ENVIRONMENT_INVALID');
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

  it('refuses lenient attestation in staging', () => {
    const r = parseGatewayConfig({
      ENVIRONMENT: 'staging',
      ATTESTATION_MODE: 'lenient',
    });
    expect(r.ok).toBe(false);
    if (!r.ok) {
      expect(r.problems).toContain('ATTESTATION_MODE_REFUSED_OUTSIDE_TEST');
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
      ENVIRONMENT: 'staging',
      ATTESTATION_ROOT_SHA256: JSON.stringify([hex]),
    });
    expect(r.ok).toBe(true);
    if (r.ok) expect(r.config.attestationRootSha256).toEqual([hex]);
  });

  it('rejects malformed root fingerprints', () => {
    const r = parseGatewayConfig({
      ENVIRONMENT: 'staging',
      ATTESTATION_ROOT_SHA256: JSON.stringify(['not-hex']),
    });
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.problems).toContain('ATTESTATION_ROOT_SHA256_INVALID');
  });

  it('isAuthTierAllowed respects config for production vs staging/test', () => {
    const prod = parseGatewayConfig({ ENVIRONMENT: 'production' });
    expect(prod.ok).toBe(true);
    if (prod.ok) {
      expect(isAuthTierAllowed(prod.config, 'hardware_confirm')).toBe(true);
      expect(isAuthTierAllowed(prod.config, 'in_app_confirm')).toBe(false);
    }

    const staging = parseGatewayConfig({ ENVIRONMENT: 'staging' });
    expect(staging.ok).toBe(true);
    if (staging.ok) {
      expect(isAuthTierAllowed(staging.config, 'hardware_confirm')).toBe(true);
      expect(isAuthTierAllowed(staging.config, 'in_app_confirm')).toBe(true);
    }
  });
});
