/**
 * Audit emission — Stage 5.1.
 * Spec §13–14 — every privileged outcome emits a redacted audit record and
 * returns a durable `auditRef` for the contract success body.
 */

import { redactRecord, FORBIDDEN_FIELD_NAMES } from './redaction';
import type { Env } from '../runtime';

export type AuditOutcome = 'accepted' | 'refused' | 'error';

export interface AuditRecord {
  auditRef: string;
  requestId: string;
  op: string;
  outcome: AuditOutcome;
  code?: string;
  branch?: string;
  head?: string;
  plan?: string;
  task?: string;
  at: number;
  detail?: Record<string, unknown>;
}

export interface EmitAuditInput {
  requestId: string;
  op: string;
  outcome: AuditOutcome;
  code?: string;
  branch?: string;
  head?: string;
  plan?: string;
  task?: string;
  now?: number;
  detail?: Record<string, unknown>;
  /** When provided, record is also appended to AuditStream DO (durable). */
  env?: Env;
}

/** In-memory store for unit tests and single-isolate inspection. */
const RECORDS: AuditRecord[] = [];

function newAuditRef(now: number): string {
  const stamp = new Date(now * 1000)
    .toISOString()
    .replace(/[-:TZ.]/g, '')
    .slice(0, 14);
  const rand = crypto.randomUUID().slice(0, 8);
  return `audit-${stamp}-${rand}`;
}

function sanitizeDetail(
  detail: Record<string, unknown> | undefined
): Record<string, unknown> | undefined {
  if (!detail) return undefined;
  const stripped: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(detail)) {
    if (FORBIDDEN_FIELD_NAMES.includes(k)) continue;
    stripped[k] = v;
  }
  return redactRecord(stripped);
}

/**
 * Emit an audit record. Always returns an auditRef (even on refusal paths).
 * Optionally persists to AuditStream when `env` is provided.
 */
export function emitAudit(input: EmitAuditInput): AuditRecord {
  const now = input.now ?? Math.floor(Date.now() / 1000);
  const record: AuditRecord = {
    auditRef: newAuditRef(now),
    requestId: input.requestId,
    op: input.op,
    outcome: input.outcome,
    code: input.code,
    branch: input.branch,
    head: input.head,
    plan: input.plan,
    task: input.task,
    at: now,
    detail: sanitizeDetail(input.detail),
  };
  RECORDS.push(record);
  if (input.env) {
    // Fire durable append; failures must not block the response path.
    void persistAudit(input.env, record).catch(() => undefined);
  }
  return record;
}

/** Awaitable durable append (for handlers that can wait). */
export async function emitAuditDurable(
  input: EmitAuditInput
): Promise<AuditRecord> {
  const record = emitAudit({ ...input, env: undefined });
  if (input.env) {
    await persistAudit(input.env, record);
  }
  return record;
}

export async function persistAudit(
  env: Env,
  record: AuditRecord
): Promise<void> {
  const ns = env.AUDIT_STREAM;
  if (!ns) {
    // Memory-only already holds the record via RECORDS.
    return;
  }
  const stub = ns.get(ns.idFromName('cbridge-audit-stream'));
  const res = await stub.fetch('https://audit/append', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(record),
  });
  if (!res.ok) {
    throw new Error(`audit append failed: ${res.status}`);
  }
}

export async function resolveAuditRef(
  env: Env,
  auditRef: string
): Promise<AuditRecord | null> {
  // Prefer durable stream
  if (env.AUDIT_STREAM) {
    const stub = env.AUDIT_STREAM.get(
      env.AUDIT_STREAM.idFromName('cbridge-audit-stream')
    );
    const res = await stub.fetch(
      `https://audit/ref/${encodeURIComponent(auditRef)}`
    );
    if (res.ok) {
      const body = (await res.json()) as { record?: AuditRecord };
      if (body.record) return body.record;
    }
  }
  // Fallback: in-memory (tests / single-isolate)
  return RECORDS.find((r) => r.auditRef === auditRef) ?? null;
}

export async function listAudits(
  env: Env,
  limit = 50
): Promise<AuditRecord[]> {
  if (env.AUDIT_STREAM) {
    const stub = env.AUDIT_STREAM.get(
      env.AUDIT_STREAM.idFromName('cbridge-audit-stream')
    );
    const res = await stub.fetch(`https://audit/list?limit=${limit}`);
    if (res.ok) {
      const body = (await res.json()) as { records?: AuditRecord[] };
      return body.records ?? [];
    }
  }
  return RECORDS.slice(-limit).reverse();
}

/** Test helpers. */
export function listAuditRecords(): readonly AuditRecord[] {
  return RECORDS;
}

export function clearAuditRecords(): void {
  RECORDS.length = 0;
}

/** Contract-shaped summary for GET /audit. */
export function toAuditSummary(rec: AuditRecord): Record<string, unknown> {
  return {
    auditRef: rec.auditRef,
    timestamp: new Date(rec.at * 1000).toISOString().replace(/\.\d{3}Z$/, 'Z'),
    operation: rec.op.toUpperCase(),
    plan: rec.plan,
    task: rec.task,
    branch: rec.branch,
    headSha: rec.head,
    outcome: rec.outcome,
    code: rec.code,
  };
}
