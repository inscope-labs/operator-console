/**
 * Audit emission (Stage 3.1 primitive).
 * Spec §13–14 — every privileged outcome emits a redacted audit record and
 * returns a durable `auditRef` for the contract success body.
 *
 * Full append to the consumer-repo audit stream lands with real write
 * transactions (Stage 3.2+). This module provides a stable in-Worker
 * record + ref so the transaction helper never returns a bare 200 without
 * an audit reference.
 */

import { redactRecord, FORBIDDEN_FIELD_NAMES } from './redaction';

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
  /** Unix seconds. */
  at: number;
  /** Optional free-form detail (will be redacted). */
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
}

/** In-memory store for unit tests and single-isolate inspection. */
const RECORDS: AuditRecord[] = [];

function newAuditRef(now: number): string {
  const stamp = new Date(now * 1000).toISOString().replace(/[-:TZ.]/g, '').slice(0, 14);
  const rand = crypto.randomUUID().slice(0, 8);
  return `audit-${stamp}-${rand}`;
}

/**
 * Strip forbidden field names then redact string values.
 */
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
  return record;
}

/** Test helpers. */
export function listAuditRecords(): readonly AuditRecord[] {
  return RECORDS;
}

export function clearAuditRecords(): void {
  RECORDS.length = 0;
}
