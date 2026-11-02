/**
 * Redaction gate.
 * Spec §14 — applied to every string field before any sink.
 *
 * Forbidden patterns additionally include:
 * - capability signature
 * - capability nonce
 * - any device key material
 *
 * The existing build-engine redaction.ts is the base; this module extends it.
 */

const FORBIDDEN_SUBSTRINGS: readonly string[] = [
  // Capability material
  'ctxop-v1',
  // Common secret prefixes (G11 also covers these in diffs)
  'ghp_',
  'github_pat_',
  'ghs_',
  'gho_',
  // Generic high-entropy markers that should never appear in logs
  '-----BEGIN',
];

/**
 * Redact a single string value.
 * Returns the original string if clean, or a redacted placeholder.
 */
export function redactString(value: string): string {
  if (!value) return value;
  const lower = value.toLowerCase();
  for (const pattern of FORBIDDEN_SUBSTRINGS) {
    if (lower.includes(pattern.toLowerCase())) {
      return '[REDACTED]';
    }
  }
  // Heuristic: long base64url-looking tokens
  if (/^[A-Za-z0-9_-]{40,}$/.test(value)) {
    return '[REDACTED_TOKEN]';
  }
  return value;
}

/**
 * Deep-redact a plain object (one level of nesting for arrays/objects).
 * Used before structured logging and before audit emission.
 */
export function redactRecord<T extends Record<string, unknown>>(
  record: T
): T {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(record)) {
    if (typeof v === 'string') {
      out[k] = redactString(v);
    } else if (Array.isArray(v)) {
      out[k] = v.map((item) =>
        typeof item === 'string' ? redactString(item) : item
      );
    } else if (v && typeof v === 'object') {
      out[k] = redactRecord(v as Record<string, unknown>);
    } else {
      out[k] = v;
    }
  }
  return out as T;
}

/**
 * Explicitly forbid logging of signature and nonce fields by name.
 * Call sites should strip these before constructing the record;
 * this is a second line of defence.
 */
export const FORBIDDEN_FIELD_NAMES: readonly string[] = [
  'signature',
  'sig',
  'nonce',
  'capability_signature',
  'device_private_key',
  'private_key',
];
