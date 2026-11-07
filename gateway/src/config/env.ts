/**
 * Typed, validated gateway configuration (zod).
 * Fail closed: invalid/missing config → CONFIG_INVALID for every route except /health.
 *
 * ENVIRONMENT: test | staging | production (Stage 1.2 / BD-1.1).
 * Lenient attestation is refused when ENVIRONMENT is production.
 *
 * Default auth-tier policy:
 *   production  → hardware_confirm only
 *   test/staging → hardware_confirm + in_app_confirm
 */

import { z } from 'zod';
import type { AuthTier, AttestationMode, EnvironmentName } from './constants';
import { AUTH_TIERS } from './constants';

const AuthTierSchema = z.enum(['hardware_confirm', 'in_app_confirm']);
const EnvironmentSchema = z.enum(['test', 'staging', 'production']);
const AttestationModeSchema = z.enum(['strict', 'lenient']);

const HexSha256Schema = z
  .string()
  .regex(/^[0-9a-fA-F]{64}$/, 'ATTESTATION_ROOT_SHA256 entries must be 64-char hex');

export interface GatewayConfig {
  environment: EnvironmentName;
  allowedAuthTiers: AuthTier[];
  attestationMode: AttestationMode;
  attestationRootSha256: string[];
  /** Non-secret presence flags for /health problem classification. */
  bindings: {
    hasOperatorToken: boolean;
    hasRepos: boolean;
    hasSigningKeys: boolean;
    hasGithubApp: boolean;
  };
}

export type ConfigProblemClass =
  | 'ENVIRONMENT_INVALID'
  | 'ATTESTATION_MODE_INVALID'
  | 'ATTESTATION_MODE_REFUSED_IN_PRODUCTION'
  | 'ATTESTATION_MODE_REFUSED_OUTSIDE_TEST'
  | 'ALLOWED_AUTH_TIERS_INVALID'
  | 'ATTESTATION_ROOT_SHA256_INVALID'
  | 'PARSE_ERROR';

export type ConfigResult =
  | { ok: true; config: GatewayConfig }
  | { ok: false; problems: ConfigProblemClass[] };

function defaultTiers(environment: EnvironmentName): AuthTier[] {
  return environment === 'production'
    ? ['hardware_confirm']
    : ['hardware_confirm', 'in_app_confirm'];
}

function parseJsonArray(raw: string | undefined, field: string): unknown {
  if (raw === undefined || raw === '') return undefined;
  try {
    return JSON.parse(raw);
  } catch {
    throw new Error(`${field}_PARSE`);
  }
}

/**
 * Parse Worker env into a validated GatewayConfig.
 * Does not log or echo secret values.
 */
export function parseGatewayConfig(env: Record<string, unknown>): ConfigResult {
  const problems: ConfigProblemClass[] = [];

  const envRaw = env['ENVIRONMENT'];
  const envParsed = EnvironmentSchema.safeParse(
    typeof envRaw === 'string' && envRaw.length > 0 ? envRaw : undefined
  );
  if (!envParsed.success) {
    problems.push('ENVIRONMENT_INVALID');
  }
  const environment: EnvironmentName = envParsed.success ? envParsed.data : 'production';

  let attestationMode: AttestationMode = 'strict';
  const modeRaw = env['ATTESTATION_MODE'];
  if (modeRaw === undefined || modeRaw === '') {
    attestationMode = 'strict';
  } else {
    const modeParsed = AttestationModeSchema.safeParse(modeRaw);
    if (!modeParsed.success) {
      problems.push('ATTESTATION_MODE_INVALID');
    } else {
      attestationMode = modeParsed.data;
      if (attestationMode === 'lenient' && environment !== 'test') {
        problems.push(
          environment === 'production'
            ? 'ATTESTATION_MODE_REFUSED_IN_PRODUCTION'
            : 'ATTESTATION_MODE_REFUSED_OUTSIDE_TEST'
        );
      }
    }
  }

  let allowedAuthTiers: AuthTier[] = defaultTiers(environment);
  const tiersRaw = env['ALLOWED_AUTH_TIERS'];
  if (tiersRaw !== undefined && tiersRaw !== '') {
    try {
      const arr = parseJsonArray(String(tiersRaw), 'ALLOWED_AUTH_TIERS');
      const tiersParsed = z.array(AuthTierSchema).min(1).safeParse(arr);
      if (!tiersParsed.success) {
        problems.push('ALLOWED_AUTH_TIERS_INVALID');
      } else {
        allowedAuthTiers = tiersParsed.data;
      }
    } catch {
      problems.push('ALLOWED_AUTH_TIERS_INVALID');
    }
  }

  let attestationRootSha256: string[] = [];
  const rootsRaw = env['ATTESTATION_ROOT_SHA256'];
  if (rootsRaw !== undefined && rootsRaw !== '') {
    try {
      const arr = parseJsonArray(String(rootsRaw), 'ATTESTATION_ROOT_SHA256');
      const rootsParsed = z.array(HexSha256Schema).safeParse(arr);
      if (!rootsParsed.success) {
        problems.push('ATTESTATION_ROOT_SHA256_INVALID');
      } else {
        attestationRootSha256 = rootsParsed.data.map((h) => h.toLowerCase());
      }
    } catch {
      problems.push('ATTESTATION_ROOT_SHA256_INVALID');
    }
  }
  // strict mode with empty roots is allowed at parse time; attestation verify fails closed later

  if (problems.length > 0) {
    return { ok: false, problems };
  }

  const str = (k: string): string => {
    const v = env[k];
    return typeof v === 'string' ? v : '';
  };

  return {
    ok: true,
    config: {
      environment,
      allowedAuthTiers,
      attestationMode,
      attestationRootSha256,
      bindings: {
        hasOperatorToken: str('OPERATOR_TOKEN').length > 0,
        hasRepos: str('REPOS').length > 0,
        hasSigningKeys: str('SIGNING_KEYS').length > 0,
        hasGithubApp:
          str('GITHUB_APP_ID').length > 0 &&
          str('GITHUB_APP_PRIVATE_KEY').length > 0 &&
          str('GITHUB_APP_INSTALLATION_ID').length > 0,
      },
    },
  };
}

/** True when tier is permitted by config. */
export function isAuthTierAllowed(config: GatewayConfig, tier: AuthTier): boolean {
  return config.allowedAuthTiers.includes(tier);
}

export { AUTH_TIERS };
