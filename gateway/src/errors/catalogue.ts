/**
 * Stable error catalogue — §15.
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
  | 'NETWORK';

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
};
