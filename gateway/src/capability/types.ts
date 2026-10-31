/**
 * ctxop-v1 capability model.
 * Spec: §8 of the cbridge Cloudflare Gateway specification.
 *
 * Field order is normative and must match the LF-separated wire format exactly.
 * Signature covers the entire message (op + branch + head + nonce binding).
 */

export const CAPABILITY_VERSION = 'ctxop-v1' as const;
export const MAX_TTL_SECONDS = 300; // 5 minutes — decision D-b
export const CLOCK_SKEW_SECONDS = 10;

/** Closed set of operations. Unknown values → UNKNOWN_OP. */
export type OperationType =
  | 'publish'
  | 'upload'
  | 'reconcile'
  | 'halt'
  | 'resume'
  | 'decide'
  | 'order'
  | 'set'
  | 'new'
  | 'sync'
  | 'doctor'
  | 'set_in_progress'
  | 'set_done';

export const OPERATION_TYPES: readonly OperationType[] = [
  'publish',
  'upload',
  'reconcile',
  'halt',
  'resume',
  'decide',
  'order',
  'set',
  'new',
  'sync',
  'doctor',
  'set_in_progress',
  'set_done',
] as const;

export interface OperatorCapability {
  /** Domain separator — always "ctxop-v1" */
  version: typeof CAPABILITY_VERSION;
  /** Key selector (kid). Matches enrolled device public key under Model B. */
  kid: string;
  /** Expiry in Unix seconds (inclusive). */
  exp: number;
  /** Work order identifier. */
  order: string;
  /** Plan identifier. */
  plan: string;
  /** Task identifier. */
  task: string;
  /** Operation (closed enum). */
  op: OperationType;
  /** Minted branch — must match ^task-[A-Za-z0-9._-]+$ and never "main". */
  branch: string;
  /** Expected branch head SHA (40 hex characters). Hard-checked before every write. */
  head: string;
  /** Single-use random value. Replay → NONCE_REPLAYED. */
  nonce: string;
}

export interface SignedCapability {
  capability: OperatorCapability;
  /** base64url_nopad(HMAC-SHA256(...)) or device signature under Model B. */
  signature: string;
  /** Original LF-separated message that was signed. */
  message: string;
}

export function isOperationType(value: string): value is OperationType {
  return (OPERATION_TYPES as readonly string[]).includes(value);
}
