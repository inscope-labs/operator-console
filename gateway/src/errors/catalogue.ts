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
  | 'NOT_ENROLLED'
  | 'TOKEN_UNAVAILABLE'
  | 'NETWORK'
  | 'NOT_FOUND'
  | 'RATE_BUDGET_LOW'
  // Capability
  | 'UNKNOWN_KID'
  | 'BAD_SIGNATURE'
  | 'TTL_TOO_LONG'
  | 'UNKNOWN_OP'
  | 'BRANCH_MISMATCH'
  | 'HEAD_MOVED'
  | 'HEAD_DRIFT'
  | 'NONCE_REPLAYED'
  | 'CAPABILITY_EXPIRED'
  | 'EXPIRED'
  | 'BAD_FIELD_COUNT'
  | 'BAD_ENCODING'
  | 'BRANCH_NOT_TASK'
  // Guard layer (G1–G16)
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
  | 'GUARD_FAILED'
  // Adapter / platform
  | 'UNKNOWN_REPO'
  | 'RULESET_REJECTED'
  | 'RECONCILE_REQUIRED'
  // Config & environment
  | 'CONFIG_INVALID'
  | 'ENVIRONMENT_MISMATCH'
  // Enrolment / attestation (GW-A addendum)
  | 'ENROLL_CHALLENGE_INVALID'
  | 'ENROLL_ATTESTATION_INVALID'
  | 'AUTH_TIER_NOT_ALLOWED'
  // Stub / unverified transaction
  | 'NOT_IMPLEMENTED';

export interface GatewayErrorBody {
  /** Machine-readable error code — authoritative per docs/gateway-contract.md §4. */
  code: ErrorCode;
  /** Human-readable failure explanation (no secrets or nonces). */
  message: string;
  /** Unique request trace identifier. */
  request_id: string;
  /** Backwards-compatible alias for code. */
  error?: ErrorCode;
}

export const HTTP_STATUS: Record<ErrorCode, number> = {
  OPERATOR_AUTH: 401,
  CREDENTIAL_NOT_ACCEPTED: 400,
  DEVICE_NOT_ENROLLED: 401,
  NOT_ENROLLED: 403,
  TOKEN_UNAVAILABLE: 503,
  NETWORK: 502,
  NOT_FOUND: 404,
  RATE_BUDGET_LOW: 429,
  UNKNOWN_KID: 401,
  BAD_SIGNATURE: 401,
  TTL_TOO_LONG: 400,
  UNKNOWN_OP: 400,
  BRANCH_MISMATCH: 403,
  HEAD_MOVED: 409,
  HEAD_DRIFT: 409,
  NONCE_REPLAYED: 409,
  CAPABILITY_EXPIRED: 401,
  EXPIRED: 400,
  BAD_FIELD_COUNT: 400,
  BAD_ENCODING: 400,
  BRANCH_NOT_TASK: 400,
  REPO_NOT_PRIVATE: 403,
  BRANCH_INVALID: 400,
  BRANCH_MISSING: 404,
  ANCESTRY_FAIL: 409,
  DELTA_EMPTY: 400,
  MERGE_COMMITS: 400,
  DENIED_PATH: 403,
  SECRET_IN_DIFF: 403,
  HALT_ACTIVE: 423,
  SUBMISSION_IMMUTABLE: 409,
  GUARD_FAILED: 422,
  UNKNOWN_REPO: 400,
  RULESET_REJECTED: 403,
  RECONCILE_REQUIRED: 409,
  CONFIG_INVALID: 503,
  ENVIRONMENT_MISMATCH: 403,
  ENROLL_CHALLENGE_INVALID: 400,
  ENROLL_ATTESTATION_INVALID: 400,
  AUTH_TIER_NOT_ALLOWED: 403,
  NOT_IMPLEMENTED: 501,
};

/** Human-readable defaults (no secrets). */
export const ERROR_MESSAGE: Partial<Record<ErrorCode, string>> = {
  CONFIG_INVALID: 'Gateway configuration is invalid or incomplete',
  ENVIRONMENT_MISMATCH: 'Gateway is configured for a different environment',
  ENROLL_CHALLENGE_INVALID: 'Enrolment challenge unknown, expired, or already used',
  ENROLL_ATTESTATION_INVALID:
    'Attestation chain, challenge binding, key, or tier mismatch',
  AUTH_TIER_NOT_ALLOWED: 'Derived auth tier is not permitted in this environment',
  NOT_IMPLEMENTED: 'Operation or route not yet implemented',
  HEAD_DRIFT: 'The branch has moved since the capability was minted',
  HEAD_MOVED: 'The branch has moved since the capability was minted',
  NONCE_REPLAYED: 'The capability has already been used',
  HALT_ACTIVE: 'Global engine halt marker is active; write operations are blocked',
  NOT_ENROLLED: 'Device key is not enrolled or has been revoked',
  DEVICE_NOT_ENROLLED: 'Device key is not enrolled or has been revoked',
  OPERATOR_AUTH: 'Bearer OPERATOR_TOKEN required or invalid',
  TOKEN_UNAVAILABLE: 'GitHub repository access token is temporarily unavailable',
  REPO_NOT_PRIVATE: 'Consumer repository is not private',
  BRANCH_INVALID: 'Branch name does not conform to task branch naming rules',
  BRANCH_NOT_TASK: 'Branch name must match ^task-[A-Za-z0-9._-]+$',
  BRANCH_MISSING: 'Target branch does not exist in consumer repository',
  DENIED_PATH: 'Diff touches protected paths',
  SECRET_IN_DIFF: 'Diff contains detected secret or token patterns',
  DELTA_EMPTY: 'Task branch diff contains zero changes against upstream base',
  MERGE_COMMITS: 'Task branch contains forbidden merge commits',
  ANCESTRY_FAIL: 'Task branch is not a direct descendant of upstream base',
  SUBMISSION_IMMUTABLE: 'Submission artifact has already been published and cannot be modified',
  GUARD_FAILED: 'Gateway guard check refused the operation',
};
