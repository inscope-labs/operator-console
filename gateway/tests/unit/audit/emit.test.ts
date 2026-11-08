import { describe, it, expect, beforeEach } from 'vitest';
import {
  emitAudit,
  clearAuditRecords,
  listAuditRecords,
} from '../../../src/audit/emit';

beforeEach(() => {
  clearAuditRecords();
});

describe('emitAudit', () => {
  it('returns auditRef and redacts forbidden detail fields', () => {
    const rec = emitAudit({
      requestId: 'r1',
      op: 'publish',
      outcome: 'accepted',
      detail: {
        nonce: 'should-not-appear',
        note: 'ctxop-v1 must redact',
        path: 'src/ok.ts',
      },
    });
    expect(rec.auditRef).toMatch(/^audit-/);
    expect(rec.detail).toBeDefined();
    expect(rec.detail?.nonce).toBeUndefined();
    expect(rec.detail?.note).toBe('[REDACTED]');
    expect(rec.detail?.path).toBe('src/ok.ts');
    expect(listAuditRecords()).toHaveLength(1);
  });
});
