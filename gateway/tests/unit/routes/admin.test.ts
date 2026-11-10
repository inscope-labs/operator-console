import { describe, it, expect, beforeEach } from 'vitest';
import { clearRoutes, dispatch, listRoutes } from '../../../src/routes/router';
import { registerAdminRoutes } from '../../../src/routes/admin';
import { clearAuditRecords, listAuditRecords } from '../../../src/audit/emit';
import type { Env } from '../../../src/runtime';

type Enrol = {
  kid: string;
  alias: string;
  publicKey: string;
  authTier: string;
  status: string;
  enrolledAt: number;
};

function mockEnrolmentRegistry(store: Map<string, Enrol>) {
  return {
    idFromName: () => ({ toString: () => 'enrol' }),
    get: () => ({
      fetch: async (input: RequestInfo | URL, init?: RequestInit) => {
        const req =
          typeof input === 'string' || input instanceof URL
            ? new Request(input, init)
            : input;
        const url = new URL(req.url);
        if (req.method === 'GET' && url.pathname === '/active') {
          const records = Array.from(store.values()).filter(
            (r) => r.status === 'active'
          );
          return new Response(JSON.stringify({ ok: true, records }), {
            status: 200,
          });
        }
        if (req.method === 'POST' && url.pathname === '/revoke') {
          const body = (await req.json()) as { kid?: string };
          const rec = body.kid ? store.get(body.kid) : undefined;
          if (!rec) {
            return new Response(
              JSON.stringify({ error: 'UNKNOWN_KID', message: 'not found' }),
              { status: 404 }
            );
          }
          rec.status = 'retired';
          store.set(rec.kid, rec);
          return new Response(
            JSON.stringify({
              ok: true,
              revoked: true,
              kid: rec.kid,
              revokedAt: 1700000000,
            }),
            { status: 200 }
          );
        }
        return new Response('nope', { status: 404 });
      },
    }),
  };
}

function baseEnv(store: Map<string, Enrol>): Env {
  return {
    ENVIRONMENT: 'test',
    OPERATOR_TOKEN: 'tok',
    SIGNING_KEYS: '[]',
    REPOS: '{}',
    GITHUB_APP_ID: '1',
    GITHUB_APP_PRIVATE_KEY: 'k',
    GITHUB_APP_INSTALLATION_ID: '1',
    ENROLMENT_REGISTRY: mockEnrolmentRegistry(store) as never,
  } as unknown as Env;
}

const ctx = {
  waitUntil: () => undefined,
  passThroughOnException: () => undefined,
} as unknown as ExecutionContext;

beforeEach(() => {
  clearRoutes();
  clearAuditRecords();
  registerAdminRoutes();
});

describe('Stage 4.3 admin surface', () => {
  it('registers enrolments and revoke with operator token required', () => {
    const routes = listRoutes().filter((r) => r.pattern.includes('admin'));
    expect(routes.length).toBeGreaterThanOrEqual(2);
    expect(routes.every((r) => r.requiresOperatorToken)).toBe(true);
  });

  it('refuses without operator token', async () => {
    const store = new Map<string, Enrol>();
    const res = await dispatch(
      new Request('https://gw.test/admin/enrolments'),
      baseEnv(store),
      ctx
    );
    expect(res.status).toBe(401);
  });

  it('lists active enrolments without public keys', async () => {
    const store = new Map<string, Enrol>([
      [
        'k2',
        {
          kid: 'k2',
          alias: 'console',
          publicKey: 'SECRETKEYMATERIAL',
          authTier: 'hardware_confirm',
          status: 'active',
          enrolledAt: 1,
        },
      ],
    ]);
    const res = await dispatch(
      new Request('https://gw.test/admin/enrolments', {
        headers: { authorization: 'Bearer tok' },
      }),
      baseEnv(store),
      ctx
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      enrolments: Array<Record<string, unknown>>;
    };
    expect(body.enrolments).toHaveLength(1);
    expect(body.enrolments[0].kid).toBe('k2');
    expect(body.enrolments[0].publicKey).toBeUndefined();
    expect(JSON.stringify(body)).not.toContain('SECRETKEYMATERIAL');
  });

  it('revokes with confirmation and emits audit', async () => {
    const store = new Map<string, Enrol>([
      [
        'k2',
        {
          kid: 'k2',
          alias: 'console',
          publicKey: 'pk',
          authTier: 'hardware_confirm',
          status: 'active',
          enrolledAt: 1,
        },
      ],
    ]);
    const res = await dispatch(
      new Request('https://gw.test/admin/revoke', {
        method: 'POST',
        headers: {
          authorization: 'Bearer tok',
          'content-type': 'application/json',
        },
        body: JSON.stringify({
          kid: 'k2',
          reason: 'Device retired',
          confirmation: 'REVOKE k2',
        }),
      }),
      baseEnv(store),
      ctx
    );
    const body = (await res.json()) as Record<string, unknown>;
    expect(res.status).toBe(200);
    expect(body.revoked).toBe(true);
    expect(body.kid).toBe('k2');
    expect(body.auditRef).toMatch(/^audit-/);
    expect(store.get('k2')?.status).toBe('retired');
    expect(listAuditRecords().some((r) => r.op === 'admin_revoke')).toBe(true);
  });

  it('refuses wrong confirmation', async () => {
    const store = new Map<string, Enrol>();
    const res = await dispatch(
      new Request('https://gw.test/admin/revoke', {
        method: 'POST',
        headers: {
          authorization: 'Bearer tok',
          'content-type': 'application/json',
        },
        body: JSON.stringify({
          kid: 'k2',
          confirmation: 'yes',
        }),
      }),
      baseEnv(store),
      ctx
    );
    expect(res.status).toBe(400);
  });
});
