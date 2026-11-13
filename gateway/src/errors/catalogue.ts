/**
 * Stable error catalogue — §15 + GW-A addendum.
 * Codes are part of the public contract.
 * Adding a code is backwards-compatible; changing meaning requires an ADR.
 *
 * Frozen as of Stage 1.3 (GATEWAY-A-COMPLETION-PLAN). See
 * gateway/docs/error-catalogue.md and ADR 0003.
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
  | 'BAD_FIELD_COUNT'
  | 'BAD_ENCODING'
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
  // Config / environment
  | 'CONFIG_INVALID'
  | 'ENVIRONMENT_MISMATCH'
  // Enrolment / attestation (GW-A addendum)
  | 'ENROLL_CHALLENGE_INVALID'
  | 'ENROLL_ATTESTATION_INVALID'
  | 'AUTH_TIER_NOT_ALLOWED'
  // Stub / unverified transaction
  | 'NOT_IMPLEMENTED'
  // Operation idempotency (BD-1.7 / ADR 0013)
  | 'IDEMPOTENCY_KEY_INVALID'
  | 'IDEMPOTENCY_KEY_REUSED'
  | 'OPERATION_NOT_FOUND';

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
  BAD_FIELD_COUNT: 400,
  BAD_ENCODING: 400,
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
  ENVIRONMENT_MISMATCH: 403,
  ENROLL_CHALLENGE_INVALID: 400,
  ENROLL_ATTESTATION_INVALID: 400,
  AUTH_TIER_NOT_ALLOWED: 403,
  NOT_IMPLEMENTED: 501,
  IDEMPOTENCY_KEY_INVALID: 400,
  IDEMPOTENCY_KEY_REUSED: 409,
  OPERATION_NOT_FOUND: 404,
};

/** Human-readable defaults (no secrets). Every code has a default. */
export const ERROR_MESSAGE: Record<ErrorCode, string> = {
  OPERATOR_AUTH: 'Bearer OPERATOR_TOKEN required or invalid',
  CREDENTIAL_NOT_ACCEPTED: 'Presented credential is not accepted',
  DEVICE_NOT_ENROLLED: 'Device key is not enrolled or has been revoked',
  TOKEN_UNAVAILABLE: 'Required platform token is unavailable',
  UNKNOWN_KID: 'Key selector is not recognized in the device registry',
  BAD_SIGNATURE: 'Capability signature verification failed',
  TTL_TOO_LONG: 'Capability TTL exceeds the maximum of 300 seconds',
  UNKNOWN_OP: 'Capability operation is not a member of the closed enumeration',
  BRANCH_MISMATCH: 'Capability branch does not match the request target',
  HEAD_MOVED: 'Expected head SHA does not match the remote branch HEAD',
  NONCE_REPLAYED: 'Capability nonce has already been presented',
  CAPABILITY_EXPIRED: 'Capability has expired',
  BAD_FIELD_COUNT: 'Capability wire message does not contain the required field count',
  BAD_ENCODING: 'Capability wire message has invalid encoding or framing',
  REPO_NOT_PRIVATE: 'Target repository is not private',
  BRANCH_INVALID: 'Branch name does not match the required pattern',
  BRANCH_MISSING: 'Branch does not exist on the remote',
  ANCESTRY_FAIL: 'Branch ancestry check failed',
  DELTA_EMPTY: 'Branch delta is empty',
  MERGE_COMMITS: 'Branch contains merge commits',
  DENIED_PATH: 'Diff touches a denied path',
  SECRET_IN_DIFF: 'Diff contains a detected secret pattern',
  HALT_ACTIVE: 'Global engine halt is active; write operations are blocked',
  SUBMISSION_IMMUTABLE: 'Submission is immutable after acceptance',
  UNKNOWN_REPO: 'Repository is not configured for this gateway',
  RATE_BUDGET_LOW: 'Adapter rate budget is exhausted',
  RULESET_REJECTED: 'Repository ruleset rejected the operation',
  RECONCILE_REQUIRED: 'Reconcile is required before this operation',
  NETWORK: 'Upstream or network error',
  CONFIG_INVALID: 'Gateway configuration is invalid or incomplete',
  ENVIRONMENT_MISMATCH: 'Client environment does not match gateway environment',
  ENROLL_CHALLENGE_INVALID: 'Enrolment challenge unknown, expired, or already used',
  ENROLL_ATTESTATION_INVALID:
    'Attestation chain, challenge binding, key, or tier mismatch',
  AUTH_TIER_NOT_ALLOWED: 'Derived auth tier is not permitted in this environment',
  NOT_IMPLEMENTED: 'Operation or route not yet implemented',
  IDEMPOTENCY_KEY_INVALID:
    'idempotencyKey is missing or is not 32 lowercase hex characters',
  IDEMPOTENCY_KEY_REUSED:
    'idempotencyKey is already recorded with a different request fingerprint',
  OPERATION_NOT_FOUND:
    'No operation record is visible for this key under the calling credential',
};

/** Ordered list of every public code (for freeze tests and docs generation). */
export const ALL_ERROR_CODES: readonly ErrorCode[] = Object.keys(
  HTTP_STATUS
) as ErrorCode[];
