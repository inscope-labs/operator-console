/**
 * Operator credential identity for idempotency scope.
 * Hash only — never store the raw bearer token.
 */

function bytesToHex(buf: ArrayBuffer): string {
  return Array.from(new Uint8Array(buf))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

export async function hashOperatorCredential(
  bearerToken: string
): Promise<string> {
  const dig = await crypto.subtle.digest(
    'SHA-256',
    new TextEncoder().encode(`operator-cred:v1:${bearerToken}`)
  );
  return bytesToHex(dig);
}

/** Extract Bearer token from Authorization header; null if absent/malformed. */
export function extractBearerToken(request: Request): string | null {
  const h = request.headers.get('authorization');
  if (!h) return null;
  const m = /^Bearer\s+(\S+)$/i.exec(h.trim());
  return m ? m[1] : null;
}
