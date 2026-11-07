/**
 * Shared gateway constants.
 */

/** Contract version reported by GET /health. */
export const CONTRACT_VERSION = '1.0.0';

/** Enrolment challenge TTL (seconds). Spec addendum A. */
export const ENROLL_CHALLENGE_TTL_SECONDS = 300;

/** Capability maximum TTL (seconds). Spec §8. */
export const CAPABILITY_MAX_TTL_SECONDS = 300;

/** Clock skew tolerance for exp checks (seconds). */
export const CLOCK_SKEW_SECONDS = 10;

/** Default rate floor for adapter budget checks. */
export const RATE_FLOOR = 100;

/** ETag cache soft cap. */
export const ETAG_MAX_ENTRIES = 512;

/** Auth tiers recognized by the gateway. */
export const AUTH_TIERS = ['hardware_confirm', 'in_app_confirm'] as const;
export type AuthTier = (typeof AUTH_TIERS)[number];

export const ENVIRONMENTS = ['test', 'staging', 'production'] as const;
export type EnvironmentName = (typeof ENVIRONMENTS)[number];
export type AttestationMode = 'strict' | 'lenient';
