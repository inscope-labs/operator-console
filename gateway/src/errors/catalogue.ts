/**
 * Stable error catalogue — §15 + GW-A addendum.
 * Codes are part of the public contract.
 * Adding a code is backwards-compatible; changing meaning requires an ADR.
 */

export type ErrorCode =
  // Transport / credential
  | 'OPERATOR_AUTH'
  | 'CREDENTIAL_NOT_ACCEPTED'
  | 'DEVICE_NOT_ENROLLED'
  | 'TOKEN_UNAVAILABLE'
  // Capability
  | 'UNKNOWN_KID'
  | 'BAD_SIGNATURE'
  | 'TTL_TOO_LONG'
  | 'UNKNOWN_OP'
  | 'BRANCH_MISMATCH'
  | 'HEAD_MOVED'
  | 'NONCE_REPLAYED'
  | 'CAPABILITY_EXPIRED'
  // Guard layer
  | 'REPO_NOT_PRIVATE'
  | 'BRANCH_INVALID'
  | 'BRANCH_MISSING'
  | 'ANCESTRY_FAIL'
  | 'DELTA_EMPTY'
  | 'MERGE_COMMITS'
  | 'DENIED_PATH'
  | 'SECRET_IN_DIFF'
  | 'HALT_ACTIVE'
  | 'SUBMISSION_IMMUTABLE'
  // Adapter / platform
  | 'UNKNOWN_REPO'
  | 'RATE_BUDGET_LOW'
  | 'RULESET_REJECTED'
  | 'RECONCILE_REQUIRED'
  | 'NETWORK'
  // Config
  | 'CONFIG_INVALID'
  // Enrolment / attestation (GW-A addendum)
  | 'ENROLL_CHALLENGE_INVALID'
  | 'ENROLL_ATTESTATION_INVALID'
  | 'AUTH_TIER_NOT_ALLOWED'
  // Fail-closed stub (Stage 1.1) — the operation exists in the contract but is
  // not implemented in this build. Never a success, never a 2xx.
  | 'NOT_IMPLEMENTED';

export interface GatewayErrorBody {
  error: ErrorCode;
  message: string;
  request_id: string;
}

export const HTTP_STATUS: Record<ErrorCode, number> = {
  OPERATOR_AUTH: 401,
  CREDENTIAL_NOT_ACCEPTED: 400,
  DEVICE_NOT_ENROLLED: 401,
  TOKEN_UNAVAILABLE: 503,
  UNKNOWN_KID: 401,
  BAD_SIGNATURE: 401,
  TTL_TOO_LONG: 400,
  UNKNOWN_OP: 400,
  BRANCH_MISMATCH: 403,
  HEAD_MOVED: 409,
  NONCE_REPLAYED: 409,
  CAPABILITY_EXPIRED: 401,
  REPO_NOT_PRIVATE: 403,
  BRANCH_INVALID: 400,
  BRANCH_MISSING: 404,
  ANCESTRY_FAIL: 409,
  DELTA_EMPTY: 400,
  MERGE_COMMITS: 400,
  DENIED_PATH: 403,
  SECRET_IN_DIFF: 403,
  HALT_ACTIVE: 409,
  SUBMISSION_IMMUTABLE: 409,
  UNKNOWN_REPO: 400,
  RATE_BUDGET_LOW: 429,
  RULESET_REJECTED: 403,
  RECONCILE_REQUIRED: 409,
  NETWORK: 502,
  CONFIG_INVALID: 503,
  ENROLL_CHALLENGE_INVALID: 400,
  ENROLL_ATTESTATION_INVALID: 400,
  AUTH_TIER_NOT_ALLOWED: 403,
  NOT_IMPLEMENTED: 501,
};

/** Human-readable defaults (no secrets). */
export const ERROR_MESSAGE: Partial<Record<ErrorCode, string>> = {
  CONFIG_INVALID: 'Gateway configuration is invalid or incomplete',
  ENROLL_CHALLENGE_INVALID: 'Enrolment challenge unknown, expired, or already used',
  ENROLL_ATTESTATION_INVALID:
    'Attestation chain, challenge binding, key, or tier mismatch',
  AUTH_TIER_NOT_ALLOWED: 'Derived auth tier is not permitted in this environment',
  NOT_IMPLEMENTED:
    'Operation is not implemented in this gateway build; no work was performed',
};
