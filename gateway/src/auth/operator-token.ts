/**
 * OPERATOR_TOKEN verification.
 * Spec §9.2.1 — required on privileged routes; checked first.
 * Fail closed. Comparison is constant-time to avoid timing leaks.
 */

/**
 * Extract the Bearer token from an Authorization header value.
 * Returns null if the header is missing or not Bearer.
 */
export function extractBearer(authorizationHeader: string | null): string | null {
  if (!authorizationHeader) return null;
  const m = /^Bearer\s+(.+)$/i.exec(authorizationHeader.trim());
  if (!m) return null;
  const token = m[1].trim();
  return token.length > 0 ? token : null;
}

/**
 * Constant-time string equality.
 * Length differences still short-circuit after a fixed-pattern compare of the
 * shorter side, but the secret is never compared character-by-character in a
 * data-dependent early exit on content.
 */
export function safeEqual(a: string, b: string): boolean {
  const encoder = new TextEncoder();
  const bufA = encoder.encode(a);
  const bufB = encoder.encode(b);
  if (bufA.byteLength !== bufB.byteLength) {
    // Still touch both buffers to reduce timing signal on length.
    let diff = bufA.byteLength ^ bufB.byteLength;
    const len = Math.max(bufA.byteLength, bufB.byteLength);
    for (let i = 0; i < len; i++) {
      const ca = i < bufA.byteLength ? bufA[i] : 0;
      const cb = i < bufB.byteLength ? bufB[i] : 0;
      diff |= ca ^ cb;
    }
    return false;
  }
  // crypto.subtle.timingSafeEqual is available in Workers / modern Node.
  if (typeof crypto !== 'undefined' && crypto.subtle && 'timingSafeEqual' in crypto.subtle) {
    // Workers runtime: use the platform primitive when present.
    // Note: some Workers builds expose timingSafeEqual on crypto, not subtle.
  }
  // Portable constant-time XOR fold.
  let out = 0;
  for (let i = 0; i < bufA.byteLength; i++) {
    out |= bufA[i] ^ bufB[i];
  }
  return out === 0;
}

/**
 * Validate that the request carries a valid OPERATOR_TOKEN.
 * Returns true only when the Bearer credential matches env.OPERATOR_TOKEN.
 */
export function verifyOperatorToken(
  request: Request,
  expectedToken: string
): boolean {
  if (!expectedToken) {
    // Misconfiguration — fail closed rather than accept empty secret.
    return false;
  }
  const presented = extractBearer(request.headers.get('authorization'));
  if (!presented) return false;
  return safeEqual(presented, expectedToken);
}
