/**
 * Capability nonce store — replay protection.
 * Spec §12.4 / GATEWAY-A-COMPLETION-PLAN Stage 2.1.
 *
 * Each capability nonce is stored until exp.
 * Replay → NONCE_REPLAYED (409).
 * After exp the capability is refused by TTL checks regardless of store state;
 * the alarm removes expired records so storage does not grow unbounded.
 *
 * Durable Object request handling is single-threaded per object instance.
 * check-and-store still uses a storage transaction so get+put is atomic
 * relative to any overlapping transactional readers.
 */

export interface NonceRecord {
  nonce: string;
  exp: number;
  kid: string;
  op: string;
  storedAt: number;
}

const NONCE_PREFIX = 'nonce:';
/** Maximum alarm horizon (seconds). Clamps far-future exp to keep alarms bounded. */
const ALARM_HORIZON_SECONDS = 3600;
/** Minimum delay before next alarm (ms) to avoid tight loops. */
const ALARM_MIN_DELAY_MS = 1_000;

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
    if (request.method === 'GET' && url.pathname === '/stats') {
      return this.stats(requestId);
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
   * Alarm handler — purge expired nonces and reschedule if any remain.
   * Cloudflare invokes this when the alarm set via storage.setAlarm fires.
   */
  async alarm(): Promise<void> {
    await this.purgeExpiredRecords(Math.floor(Date.now() / 1000));
    await this.scheduleNextAlarm();
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
    if (!body.nonce || typeof body.nonce !== 'string') {
      return jsonError('NETWORK', 'nonce required', requestId, 400);
    }
    if (typeof body.exp !== 'number' || !Number.isFinite(body.exp)) {
      return jsonError('NETWORK', 'exp required', requestId, 400);
    }

    const now = Math.floor(Date.now() / 1000);
    // Refuse already-expired nonces so they never enter storage.
    if (body.exp < now) {
      return jsonError(
        'CAPABILITY_EXPIRED',
        'nonce exp is in the past',
        requestId,
        401
      );
    }

    const key = `${NONCE_PREFIX}${body.nonce}`;
    const record: NonceRecord = {
      nonce: body.nonce,
      exp: body.exp,
      kid: body.kid ?? '',
      op: body.op ?? '',
      storedAt: now,
    };

    let replayed = false;
    const storage = this.state.storage;

    if (typeof storage.transaction === 'function') {
      await storage.transaction(async (txn) => {
        const existing = await txn.get<NonceRecord>(key);
        if (existing) {
          replayed = true;
          return;
        }
        await txn.put(key, record);
      });
    } else {
      // Fallback for test mocks without transaction support (DO runtime is sequential).
      const existing = await storage.get<NonceRecord>(key);
      if (existing) {
        replayed = true;
      } else {
        await storage.put(key, record);
      }
    }

    if (replayed) {
      return jsonError(
        'NONCE_REPLAYED',
        'nonce already used',
        requestId,
        409
      );
    }

    await this.scheduleNextAlarm();

    return new Response(
      JSON.stringify({ ok: true, request_id: requestId }),
      { status: 200, headers: { 'content-type': 'application/json' } }
    );
  }

  private async purgeExpired(requestId: string): Promise<Response> {
    const now = Math.floor(Date.now() / 1000);
    const purged = await this.purgeExpiredRecords(now);
    await this.scheduleNextAlarm();
    return new Response(
      JSON.stringify({
        ok: true,
        purged,
        request_id: requestId,
      }),
      { status: 200, headers: { 'content-type': 'application/json' } }
    );
  }

  private async stats(requestId: string): Promise<Response> {
    const all = await this.state.storage.list<NonceRecord>({ prefix: NONCE_PREFIX });
    const now = Math.floor(Date.now() / 1000);
    let live = 0;
    let expired = 0;
    for (const rec of all.values()) {
      if (rec.exp < now) expired += 1;
      else live += 1;
    }
    const alarm =
      typeof this.state.storage.getAlarm === 'function'
        ? await this.state.storage.getAlarm()
        : null;
    return new Response(
      JSON.stringify({
        ok: true,
        live,
        expired,
        total: live + expired,
        nextAlarm: alarm,
        request_id: requestId,
      }),
      { status: 200, headers: { 'content-type': 'application/json' } }
    );
  }

  /** Delete every nonce record with exp < now. Returns count purged. */
  private async purgeExpiredRecords(now: number): Promise<number> {
    const all = await this.state.storage.list<NonceRecord>({ prefix: NONCE_PREFIX });
    const toDelete: string[] = [];
    for (const [key, rec] of all) {
      if (rec.exp < now) toDelete.push(key);
    }
    if (toDelete.length === 0) return 0;

    for (const key of toDelete) {
      await this.state.storage.delete(key);
    }
    return toDelete.length;
  }

  /**
   * Schedule (or refresh) the cleanup alarm for the earliest live expiry,
   * clamped to ALARM_HORIZON_SECONDS from now.
   */
  private async scheduleNextAlarm(): Promise<void> {
    const storage = this.state.storage;
    if (typeof storage.setAlarm !== 'function') {
      return; // test mock without alarm support
    }

    const nowSec = Math.floor(Date.now() / 1000);
    const all = await storage.list<NonceRecord>({ prefix: NONCE_PREFIX });
    let earliestExp: number | null = null;
    for (const rec of all.values()) {
      if (rec.exp >= nowSec) {
        if (earliestExp === null || rec.exp < earliestExp) {
          earliestExp = rec.exp;
        }
      }
    }

    if (earliestExp === null) {
      if (typeof storage.deleteAlarm === 'function') {
        await storage.deleteAlarm();
      }
      return;
    }

    const clampedExp = Math.min(earliestExp, nowSec + ALARM_HORIZON_SECONDS);
    const alarmAtMs = Math.max(clampedExp * 1000, Date.now() + ALARM_MIN_DELAY_MS);
    await storage.setAlarm(alarmAtMs);
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
