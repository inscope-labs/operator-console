import { describe, it, expect, beforeEach } from 'vitest';
import { clearRoutes, dispatch } from '../../../src/routes/router';
import { registerAuditRoutes } from '../../../src/routes/audit';
import {
  emitAudit,
  clearAuditRecords,
  resolveAuditRef,
} from '../../../src/audit/emit';
import type { Env } from '../../../src/runtime';

function baseEnv(): Env {
  return {
    ENVIRONMENT: 'test',
    OPERATOR_TOKEN: 'tok',
    SIGNING_KEYS: '[]',
    REPOS: '{}',
    GITHUB_APP_ID: '1',
    GITHUB_APP_PRIVATE_KEY: 'k',
    GITHUB_APP_INSTALLATION_ID: '1',
  } as unknown as Env;
}

const ctx = {
  waitUntil: () => undefined,
  passThroughOnException: () => undefined,
} as unknown as ExecutionContext;

beforeEach(() => {
  clearRoutes();
  clearAuditRecords();
  registerAuditRoutes();
});

describe('GET /audit', () => {
  it('resolves auditRef returned by emitAudit', async () => {
    const env = baseEnv();
    const rec = emitAudit({
      requestId: 'r1',
      op: 'publish',
      outcome: 'accepted',
      plan: 'atlas',
      task: '7',
      branch: 'task-7',
      head: 'a'.repeat(40),
    });
    const found = await resolveAuditRef(env, rec.auditRef);
    expect(found?.auditRef).toBe(rec.auditRef);

    const res = await dispatch(
      new Request(`https://gw.test/audit/${rec.auditRef}`, {
        headers: { authorization: 'Bearer tok' },
      }),
      env,
      ctx
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as { auditRef: string; operation: string };
    expect(body.auditRef).toBe(rec.auditRef);
    expect(body.operation).toBe('PUBLISH');
  });

  it('lists recent audits', async () => {
    const env = baseEnv();
    emitAudit({ requestId: 'r1', op: 'halt', outcome: 'accepted' });
    const res = await dispatch(
      new Request('https://gw.test/audit', {
        headers: { authorization: 'Bearer tok' },
      }),
      env,
      ctx
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as unknown[];
    expect(body.length).toBeGreaterThan(0);
  });

  it('requires operator token', async () => {
    const res = await dispatch(
      new Request('https://gw.test/audit'),
      baseEnv(),
      ctx
    );
    expect(res.status).toBe(401);
  });
});
