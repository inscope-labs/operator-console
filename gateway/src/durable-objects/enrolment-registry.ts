/**
 * Enrolment registry Durable Object — GW-4 / Stage 2.2.
 * Spec §7 + addendum: challenges (single-use, TTL 300s) and enrolment records.
 * Internal fetch routes only — no public admin surface.
 *
 * Challenge is consumed on the first attestation attempt (success or failure).
 * Full attestation chain is never stored; challenge bytes are deleted after consume.
 * Storage is the Durable Object's persistent storage — a new Worker isolate that
 * binds to the same DO id observes the same records (restart survival).
 */

import { ENROLL_CHALLENGE_TTL_SECONDS, type AuthTier } from '../config/constants';

export interface ChallengeRecord {
  /** base64url challenge string as issued to the client. */
  challenge: string;
  expiresAt: number;
  consumed: boolean;
}

export interface EnrolmentRecord {
  kid: string; // k<N>
  alias: string;
  /** SPKI public key, base64url (normalised). */
  publicKey: string;
  authTier: AuthTier;
  status: 'active' | 'retired';
  enrolledAt: number;
  leafCertSha256: string; // hex
}

interface MetaState {
  nextKidSeq: number;
}

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

function error(code: string, message: string, status: number): Response {
  return json({ error: code, message }, status);
}

/** Normalise standard or url-safe base64 to base64url (no padding). */
export function toBase64Url(input: string): string {
  const cleaned = input.replace(/\s+/g, '');
  const b64 = cleaned.replace(/-/g, '+').replace(/_/g, '/');
  const pad = b64.length % 4 === 0 ? '' : '='.repeat(4 - (b64.length % 4));
  try {
    const raw = atob(b64 + pad);
    return btoa(raw).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  } catch {
    return cleaned.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  }
}

type Txn = {
  get: <T>(key: string) => Promise<T | undefined>;
  put: (key: string, value: unknown) => Promise<void>;
  delete: (key: string) => Promise<boolean | void>;
};

export class EnrolmentRegistry implements DurableObject {
  constructor(private readonly state: DurableObjectState) {}

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    const path = url.pathname;

    try {
      if (request.method === 'POST' && path === '/challenge/create') {
        return this.createChallenge(request);
      }
      if (request.method === 'POST' && path === '/challenge/consume') {
        return this.consumeChallenge(request);
      }
      if (request.method === 'POST' && path === '/enroll') {
        return this.enroll(request);
      }
      if (request.method === 'POST' && path === '/rotate') {
        return this.rotate(request);
      }
      if (request.method === 'GET' && path.startsWith('/lookup/')) {
        const kid = decodeURIComponent(path.slice('/lookup/'.length));
        return this.lookup(kid);
      }
      if (request.method === 'GET' && path === '/active') {
        return this.listActive();
      }
      return error(
        'NETWORK',
        `EnrolmentRegistry: no route ${request.method} ${path}`,
        404
      );
    } catch (e) {
      const msg = e instanceof Error ? e.message : 'internal error';
      return error('NETWORK', msg, 500);
    }
  }

  private async withTxn<T>(fn: (txn: Txn) => Promise<T>): Promise<T> {
    const storage = this.state.storage as DurableObjectStorage & {
      transaction?: (c: (txn: Txn) => Promise<T>) => Promise<T>;
    };
    if (typeof storage.transaction === 'function') {
      return storage.transaction(fn);
    }
    return fn({
      get: (k) => storage.get(k),
      put: (k, v) => storage.put(k, v),
      delete: (k) => storage.delete(k),
    });
  }

  private async meta(txn?: Txn): Promise<MetaState> {
    const get = txn?.get ?? ((k: string) => this.state.storage.get(k));
    const m = await get<MetaState>('meta');
    return m ?? { nextKidSeq: 1 };
  }

  private async createChallenge(request: Request): Promise<Response> {
    let body: { challenge?: string; now?: number } = {};
    try {
      body = (await request.json()) as typeof body;
    } catch {
      return error('NETWORK', 'invalid JSON', 400);
    }
    if (!body.challenge || typeof body.challenge !== 'string') {
      return error('NETWORK', 'challenge required', 400);
    }
    const now = body.now ?? Math.floor(Date.now() / 1000);
    const record: ChallengeRecord = {
      challenge: body.challenge,
      expiresAt: now + ENROLL_CHALLENGE_TTL_SECONDS,
      consumed: false,
    };
    await this.state.storage.put(`challenge:${body.challenge}`, record);
    return json({ ok: true, expiresAt: record.expiresAt });
  }

  /**
   * Atomically validate + consume a challenge.
   * First attempt consumes it; reuse or expiry → ENROLL_CHALLENGE_INVALID.
   * Challenge record is deleted after successful consume (bytes not retained).
   */
  private async consumeChallenge(request: Request): Promise<Response> {
    let body: { challenge?: string; now?: number } = {};
    try {
      body = (await request.json()) as typeof body;
    } catch {
      return error('NETWORK', 'invalid JSON', 400);
    }
    if (!body.challenge) {
      return error('ENROLL_CHALLENGE_INVALID', 'challenge required', 400);
    }
    const now = body.now ?? Math.floor(Date.now() / 1000);
    const key = `challenge:${body.challenge}`;

    let invalid = false;
    await this.withTxn(async (txn) => {
      const rec = await txn.get<ChallengeRecord>(key);
      if (!rec || rec.consumed || rec.expiresAt < now) {
        invalid = true;
        return;
      }
      // Delete rather than leave a consumed flag — no challenge bytes retained.
      await txn.delete(key);
    });

    if (invalid) {
      return error(
        'ENROLL_CHALLENGE_INVALID',
        'challenge unknown, expired, or used',
        400
      );
    }
    return json({ ok: true });
  }

  private async enroll(request: Request): Promise<Response> {
    let body: {
      alias?: string;
      publicKey?: string;
      authTier?: AuthTier;
      leafCertSha256?: string;
      now?: number;
    } = {};
    try {
      body = (await request.json()) as typeof body;
    } catch {
      return error('NETWORK', 'invalid JSON', 400);
    }
    if (!body.alias || !body.publicKey || !body.authTier || !body.leafCertSha256) {
      return error(
        'NETWORK',
        'alias, publicKey, authTier, leafCertSha256 required',
        400
      );
    }
    const now = body.now ?? Math.floor(Date.now() / 1000);

    let kid = '';
    let authTier: AuthTier = body.authTier;
    await this.withTxn(async (txn) => {
      const meta = await this.meta(txn);
      kid = `k${meta.nextKidSeq}`;
      meta.nextKidSeq += 1;
      const record: EnrolmentRecord = {
        kid,
        alias: body.alias!,
        publicKey: toBase64Url(body.publicKey!),
        authTier: body.authTier!,
        status: 'active',
        enrolledAt: now,
        leafCertSha256: body.leafCertSha256!.toLowerCase(),
      };
      authTier = record.authTier;
      await txn.put(`enrol:${kid}`, record);
      await txn.put(`alias:${body.alias}:active`, kid);
      await txn.put('meta', meta);
    });

    return json({ ok: true, kid, authTier });
  }

  /** Retire oldKid and activate new enrolment in one atomic batch. */
  private async rotate(request: Request): Promise<Response> {
    let body: {
      oldKid?: string;
      alias?: string;
      publicKey?: string;
      authTier?: AuthTier;
      leafCertSha256?: string;
      now?: number;
    } = {};
    try {
      body = (await request.json()) as typeof body;
    } catch {
      return error('NETWORK', 'invalid JSON', 400);
    }
    if (!body.oldKid || !body.publicKey || !body.authTier || !body.leafCertSha256) {
      return error(
        'NETWORK',
        'oldKid, publicKey, authTier, leafCertSha256 required',
        400
      );
    }
    const now = body.now ?? Math.floor(Date.now() / 1000);

    type RotateOk = { kid: string; retiredKid: string; authTier: AuthTier };
    let result: RotateOk | undefined;
    let fail: string | undefined;

    await this.withTxn(async (txn) => {
      const old = await txn.get<EnrolmentRecord>(`enrol:${body.oldKid}`);
      if (!old || old.status !== 'active') {
        fail = 'old enrolment not active';
        return;
      }
      const alias = body.alias ?? old.alias;
      const meta = await this.meta(txn);
      const kid = `k${meta.nextKidSeq}`;
      meta.nextKidSeq += 1;
      old.status = 'retired';
      const neu: EnrolmentRecord = {
        kid,
        alias,
        publicKey: toBase64Url(body.publicKey!),
        authTier: body.authTier!,
        status: 'active',
        enrolledAt: now,
        leafCertSha256: body.leafCertSha256!.toLowerCase(),
      };
      await txn.put(`enrol:${old.kid}`, old);
      await txn.put(`enrol:${kid}`, neu);
      await txn.put(`alias:${alias}:active`, kid);
      await txn.put('meta', meta);
      result = { kid, retiredKid: old.kid, authTier: neu.authTier };
    });

    if (fail !== undefined || result === undefined) {
      return error('NETWORK', fail ?? 'rotate failed', 400);
    }
    const ok: RotateOk = result;
    return json({
      ok: true,
      kid: ok.kid,
      retiredKid: ok.retiredKid,
      authTier: ok.authTier,
    });
  }

  private async lookup(kid: string): Promise<Response> {
    const rec = await this.state.storage.get<EnrolmentRecord>(`enrol:${kid}`);
    if (!rec) {
      return error('UNKNOWN_KID', 'enrolment not found', 404);
    }
    return json({ ok: true, record: rec });
  }

  private async listActive(): Promise<Response> {
    const all = await this.state.storage.list<EnrolmentRecord>({
      prefix: 'enrol:',
    });
    const active: EnrolmentRecord[] = [];
    for (const [, rec] of all) {
      if (rec.status === 'active') active.push(rec);
    }
    return json({ ok: true, records: active });
  }
}
