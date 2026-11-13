/**
 * Idempotency key format — BD-1.7 / contract §1.6.
 * 32 lowercase hex characters; not part of the capability.
 */

export const IDEMPOTENCY_KEY_RE = /^[0-9a-f]{32}$/;

export function isValidIdempotencyKey(value: unknown): value is string {
  return typeof value === 'string' && IDEMPOTENCY_KEY_RE.test(value);
}
