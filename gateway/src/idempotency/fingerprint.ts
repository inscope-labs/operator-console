/**
 * Request fingerprint for idempotency records (BD-1.7).
 * SHA-256 over operation name and every request field except capability and zipBase64
 * (upload represented by zipSha256). Deterministic key order.
 */

function stableStringify(value: unknown): string {
  if (value === null || typeof value !== 'object') {
    return JSON.stringify(value);
  }
  if (Array.isArray(value)) {
    return '[' + value.map(stableStringify).join(',') + ']';
  }
  const obj = value as Record<string, unknown>;
  const keys = Object.keys(obj).sort();
  return (
    '{' +
    keys
      .map((k) => JSON.stringify(k) + ':' + stableStringify(obj[k]))
      .join(',') +
    '}'
  );
}

function bytesToHex(buf: ArrayBuffer): string {
  return Array.from(new Uint8Array(buf))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

/**
 * Build fingerprint from operation name and the JSON body (capability/zipBase64 stripped).
 */
export async function computeFingerprint(
  operation: string,
  body: Record<string, unknown>
): Promise<string> {
  const filtered: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(body)) {
    if (k === 'capability' || k === 'zipBase64') continue;
    filtered[k] = v;
  }
  const payload = stableStringify({ operation, body: filtered });
  const dig = await crypto.subtle.digest(
    'SHA-256',
    new TextEncoder().encode(payload)
  );
  return bytesToHex(dig);
}
