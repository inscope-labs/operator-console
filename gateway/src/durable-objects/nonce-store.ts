/**
 * Capability nonce store — replay protection.
 * Spec §12.4.
 *
 * Each capability nonce is stored until exp.
 * Replay → NONCE_REPLAYED.
 * After exp the capability is refused by TTL checks regardless of store state.
 *
 * This Durable Object is the authoritative store until expiry.
 */

export interface NonceRecord {
  nonce: string;
  exp: number;
  kid: string;
  op: string;
  storedAt: number;
}

export class NonceStore implements DurableObject {
  constructor(private readonly state: DurableObjectState) {}

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();

    if (request.method === 'POST' && url.pathname === '/check-and-store') {
      return this.checkAndStore(request, requestId);
    }
    if (request.method === 'POST' && url.pathname === '/purge-expired') {
      return this.purgeExpired(requestId);
    }
    return new Response(
      JSON.stringify({
        error: 'NETWORK',
        message: `NonceStore: no route for ${request.method} ${url.pathname}`,
        request_id: requestId,
      }),
      { status: 404, headers: { 'content-type': 'application/json' } }
    );
  }

  /**
   * Atomically check for prior use and store the nonce.
   * Body: { nonce, exp, kid, op }
   * Returns 200 on success, 409 NONCE_REPLAYED on conflict.
   */
  private async checkAndStore(
    request: Request,
    requestId: string
  ): Promise<Response> {
    let body: { nonce: string; exp: number; kid: string; op: string };
    try {
      body = (await request.json()) as typeof body;
    } catch {
      return jsonError('NETWORK', 'invalid JSON body', requestId, 400);
    }
    if (!body.nonce || !body.exp) {
      return jsonError('NETWORK', 'nonce and exp required', requestId, 400);
    }

    const key = `nonce:${body.nonce}`;
    const existing = await this.state.storage.get<NonceRecord>(key);
    if (existing) {
      return jsonError(
        'NONCE_REPLAYED',
        `nonce already used (stored at ${existing.storedAt})`,
        requestId,
        409
      );
    }

    const record: NonceRecord = {
      nonce: body.nonce,
      exp: body.exp,
      kid: body.kid,
      op: body.op,
      storedAt: Math.floor(Date.now() / 1000),
    };
    // Store with an alarm / expiration so the DO can purge after exp.
    await this.state.storage.put(key, record);
    // TODO: schedule alarm at exp for cleanup.
    return new Response(
      JSON.stringify({ ok: true, request_id: requestId }),
      { status: 200, headers: { 'content-type': 'application/json' } }
    );
  }

  private async purgeExpired(requestId: string): Promise<Response> {
    const now = Math.floor(Date.now() / 1000);
    const all = await this.state.storage.list<NonceRecord>({ prefix: 'nonce:' });
    const toDelete: string[] = [];
    for (const [key, rec] of all) {
      if (rec.exp < now) toDelete.push(key);
    }
    if (toDelete.length > 0) {
      await this.state.storage.delete(toDelete);
    }
    return new Response(
      JSON.stringify({
        ok: true,
        purged: toDelete.length,
        request_id: requestId,
      }),
      { status: 200, headers: { 'content-type': 'application/json' } }
    );
  }
}

function jsonError(
  code: string,
  message: string,
  requestId: string,
  status: number
): Response {
  return new Response(
    JSON.stringify({ error: code, message, request_id: requestId }),
    { status, headers: { 'content-type': 'application/json' } }
  );
}
