/** Operation record status — contract §3.19 / §4.3 */

export type OperationStatus = 'pending' | 'completed' | 'failed' | 'refused';

export interface OperationRecord {
  id: string;
  credentialHash: string;
  operation: string;
  fingerprint: string;
  status: OperationStatus;
  /** ISO-ish unix seconds */
  timestamp: number;
  branch?: string;
  headSha?: string;
  auditRef?: string;
  /** Required when status is failed or refused */
  code?: string;
  /** Serialized prior HTTP response for same-key replay (status + body JSON) */
  responseStatus?: number;
  responseBody?: string;
}

export interface BeginResult {
  kind: 'created' | 'replay' | 'reused' | 'pending_inflight';
  record: OperationRecord;
}

export const RETENTION_SECONDS = 7 * 24 * 60 * 60; // 7 days
/** Pending older than this is marked failed (bounded timeout). */
export const PENDING_TIMEOUT_SECONDS = 15 * 60; // 15 minutes
