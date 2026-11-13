/**
 * OperationStore Durable Object — BD-1.7 write-ahead idempotency records.
 *
 * Single-writer atomicity: one DO instance per operator credential hash.
 * Concurrent same-key requests are serialized by the DO runtime.
 *
 * Retention: ≥7 days; alarm purges expired non-pending records.
 * Pending older than PENDING_TIMEOUT_SECONDS is marked failed (NETWORK).
 */

import type { OperationRecord, OperationStatus } from '../idempotency/types';
import {
  RETENTION_SECONDS,
  PENDING_TIMEOUT_SECONDS,
} from '../idempotency/types';

const REC_PREFIX = 'op:';
const ALARM_MIN_MS = 60_000;

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

export class OperationStore implements DurableObject {
  constructor(private readonly state: DurableObjectState) {}

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    const path = url.pathname;

    if (request.method === 'POST' && path === '/begin') {
      return this.begin(request);
    }
    if (request.method === 'POST' && path === '/complete') {
      return this.complete(request);
    }
    if (request.method === 'GET' && path.startsWith('/get/')) {
      const id = decodeURIComponent(path.slice('/get/'.length));
      return this.get(id);
    }
    if (request.method === 'POST' && path === '/purge') {
      return this.purge();
    }
    return json(
      { error: 'NETWORK', message: `OperationStore: no route ${request.method} ${path}` },
      404
    );
  }

  async alarm(): Promise<void> {
    await this.purgeExpired(Math.floor(Date.now() / 1000));
    await this.scheduleNextAlarm();
  }

  private async begin(request: Request): Promise<Response> {
    let body: {
      credentialHash?: string;
      id?: string;
      operation?: string;
      fingerprint?: string;
      now?: number;
    };
    try {
      body = (await request.json()) as typeof body;
    } catch {
      return json({ error: 'NETWORK', message: 'invalid JSON' }, 400);
    }
    if (
      !body.credentialHash ||
      !body.id ||
      !body.operation ||
      !body.fingerprint
    ) {
      return json({ error: 'NETWORK', message: 'missing begin fields' }, 400);
    }
    const now = body.now ?? Math.floor(Date.now() / 1000);
    await this.purgeExpired(now);

    const key = `${REC_PREFIX}${body.id}`;
    const existing = await this.state.storage.get<OperationRecord>(key);

    if (existing) {
      if (existing.credentialHash !== body.credentialHash) {
        // Treat as not found for this credential (do not leak)
        return json({ kind: 'not_visible' }, 404);
      }
      if (existing.fingerprint !== body.fingerprint) {
        return json({
          kind: 'reused',
          record: existing,
        });
      }
      if (existing.status === 'pending') {
        // Stale pending → fail it so the client can recover with a new key
        if (now - existing.timestamp > PENDING_TIMEOUT_SECONDS) {
          existing.status = 'failed';
          existing.code = 'NETWORK';
          await this.state.storage.put(key, existing);
          // Allow a new begin after timeout by treating as terminal — still same fp
          return json({ kind: 'replay', record: existing });
        }
        return json({ kind: 'pending_inflight', record: existing });
      }
      return json({ kind: 'replay', record: existing });
    }

    const record: OperationRecord = {
      id: body.id,
      credentialHash: body.credentialHash,
      operation: body.operation,
      fingerprint: body.fingerprint,
      status: 'pending',
      timestamp: now,
    };
    await this.state.storage.put(key, record);
    await this.scheduleNextAlarm();
    return json({ kind: 'created', record });
  }

  private async complete(request: Request): Promise<Response> {
    let body: {
      id?: string;
      credentialHash?: string;
      status?: OperationStatus;
      code?: string;
      auditRef?: string;
      branch?: string;
      headSha?: string;
      responseStatus?: number;
      responseBody?: string;
      now?: number;
    };
    try {
      body = (await request.json()) as typeof body;
    } catch {
      return json({ error: 'NETWORK', message: 'invalid JSON' }, 400);
    }
    if (!body.id || !body.credentialHash || !body.status) {
      return json({ error: 'NETWORK', message: 'missing complete fields' }, 400);
    }
    const key = `${REC_PREFIX}${body.id}`;
    const existing = await this.state.storage.get<OperationRecord>(key);
    if (!existing || existing.credentialHash !== body.credentialHash) {
      return json({ error: 'OPERATION_NOT_FOUND' }, 404);
    }
    existing.status = body.status;
    if (body.code !== undefined) existing.code = body.code;
    if (body.auditRef !== undefined) existing.auditRef = body.auditRef;
    if (body.branch !== undefined) existing.branch = body.branch;
    if (body.headSha !== undefined) existing.headSha = body.headSha;
    if (body.responseStatus !== undefined)
      existing.responseStatus = body.responseStatus;
    if (body.responseBody !== undefined) existing.responseBody = body.responseBody;
    if (body.now !== undefined) existing.timestamp = body.now;
    await this.state.storage.put(key, existing);
    return json({ ok: true, record: existing });
  }

  private async get(id: string): Promise<Response> {
    const rec = await this.state.storage.get<OperationRecord>(`${REC_PREFIX}${id}`);
    if (!rec) return json({ error: 'OPERATION_NOT_FOUND' }, 404);
    return json({ ok: true, record: rec });
  }

  private async purge(): Promise<Response> {
    const n = await this.purgeExpired(Math.floor(Date.now() / 1000));
    return json({ ok: true, purged: n });
  }

  private async purgeExpired(now: number): Promise<number> {
    const all = await this.state.storage.list<OperationRecord>({
      prefix: REC_PREFIX,
    });
    let n = 0;
    for (const [key, rec] of all) {
      // Never delete pending younger than retention window
      if (rec.status === 'pending' && now - rec.timestamp < RETENTION_SECONDS) {
        // Fail stale pending
        if (now - rec.timestamp > PENDING_TIMEOUT_SECONDS) {
          rec.status = 'failed';
          rec.code = 'NETWORK';
          await this.state.storage.put(key, rec);
        }
        continue;
      }
      if (now - rec.timestamp >= RETENTION_SECONDS) {
        await this.state.storage.delete(key);
        n += 1;
      }
    }
    return n;
  }

  private async scheduleNextAlarm(): Promise<void> {
    const storage = this.state.storage as DurableObjectStorage & {
      setAlarm?: (t: number) => Promise<void>;
      getAlarm?: () => Promise<number | null>;
    };
    if (typeof storage.setAlarm !== 'function') return;
    const existing = storage.getAlarm ? await storage.getAlarm() : null;
    const next = Date.now() + ALARM_MIN_MS;
    if (existing != null && existing <= next) return;
    await storage.setAlarm(next);
  }
}
