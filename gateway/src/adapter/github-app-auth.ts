/**
 * GitHub App authentication helpers.
 * Produces a short-lived installation token (ghs_) for the InstallationTokenProvider.
 *
 * Spec §6.4 / §11.6 — App installation token only; PATs refused.
 */

export interface GitHubAppCredentials {
  /** Numeric GitHub App ID. */
  appId: string;
  /** PEM-encoded RSA private key for the App. */
  privateKeyPem: string;
  /** Installation ID for the target org/account. */
  installationId: string;
}

export interface InstallationTokenResponse {
  token: string;
  expires_at: string; // ISO 8601 from GitHub
}

/**
 * Create a GitHub App JWT (RS256, max 10 minutes).
 * Uses Web Crypto — no Node-only APIs.
 */
export async function createAppJwt(
  appId: string,
  privateKeyPem: string,
  nowSeconds: number = Math.floor(Date.now() / 1000)
): Promise<string> {
  const header = { alg: 'RS256', typ: 'JWT' };
  const payload = {
    iat: nowSeconds - 60, // clock skew
    exp: nowSeconds + 9 * 60, // < 10 minutes
    iss: appId,
  };

  const enc = (obj: unknown) =>
    bytesToBase64Url(new TextEncoder().encode(JSON.stringify(obj)));

  const signingInput = `${enc(header)}.${enc(payload)}`;
  const key = await importPkcs8(privateKeyPem);
  const sig = await crypto.subtle.sign(
    'RSASSA-PKCS1-v1_5',
    key,
    new TextEncoder().encode(signingInput)
  );
  return `${signingInput}.${bytesToBase64Url(new Uint8Array(sig))}`;
}

/**
 * Exchange App JWT for an installation access token.
 */
export async function fetchInstallationToken(
  creds: GitHubAppCredentials,
  fetchImpl: typeof fetch = fetch
): Promise<{ token: string; expiresAt: number }> {
  const jwt = await createAppJwt(creds.appId, creds.privateKeyPem);
  const url = `https://api.github.com/app/installations/${creds.installationId}/access_tokens`;
  const res = await fetchImpl(url, {
    method: 'POST',
    headers: {
      authorization: `Bearer ${jwt}`,
      accept: 'application/vnd.github+json',
      'x-github-api-version': '2022-11-28',
      'user-agent': 'cbridge-gateway',
    },
  });
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new Error(
      `TOKEN_UNAVAILABLE: installation token request failed (${res.status}): ${text.slice(0, 200)}`
    );
  }
  const body = (await res.json()) as InstallationTokenResponse;
  if (!body.token || !body.expires_at) {
    throw new Error('TOKEN_UNAVAILABLE: malformed installation token response');
  }
  if (!body.token.startsWith('ghs_')) {
    throw new Error('TOKEN_UNAVAILABLE: expected ghs_ installation token');
  }
  return {
    token: body.token,
    expiresAt: Math.floor(new Date(body.expires_at).getTime() / 1000),
  };
}

function bytesToBase64Url(bytes: Uint8Array): string {
  let binary = '';
  for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

async function importPkcs8(pem: string): Promise<CryptoKey> {
  const cleaned = pem
    .replace(/-----BEGIN PRIVATE KEY-----/g, '')
    .replace(/-----END PRIVATE KEY-----/g, '')
    .replace(/-----BEGIN RSA PRIVATE KEY-----/g, '')
    .replace(/-----END RSA PRIVATE KEY-----/g, '')
    .replace(/\s+/g, '');
  const raw = Uint8Array.from(atob(cleaned), (c) => c.charCodeAt(0));
  // PKCS#8 is the usual export from GitHub App key download.
  return crypto.subtle.importKey(
    'pkcs8',
    raw,
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign']
  );
}
