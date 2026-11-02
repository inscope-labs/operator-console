import { describe, it, expect, beforeEach } from 'vitest';
import {
  register,
  dispatch,
  clearRoutes,
  listRoutes,
} from '../../../src/routes/router';
import type { Env } from '../../../src/index';

const env = {
  OPERATOR_TOKEN: 'test-operator-token',
  SIGNING_KEYS: '[]',
  REPOS: '{}',
} as unknown as Env;

const ctx = {} as ExecutionContext;

beforeEach(() => {
  clearRoutes();
});

describe('router register + dispatch', () => {
  it('registers and matches a GET route', async () => {
    register(
      'GET',
      '/status',
      async () =>
        new Response(JSON.stringify({ ok: true }), {
          headers: { 'content-type': 'application/json' },
        }),
      { requiresOperatorToken: true }
    );
    expect(listRoutes()).toHaveLength(1);

    const req = new Request('https://gw.test/status', {
      headers: { authorization: 'Bearer test-operator-token' },
    });
    const res = await dispatch(req, env, ctx);
    expect(res.status).toBe(200);
    const body = (await res.json()) as { ok: boolean };
    expect(body.ok).toBe(true);
    expect(res.headers.get('x-request-id')).toBeTruthy();
  });

  it('returns 401 when OPERATOR_TOKEN is missing', async () => {
    register(
      'GET',
      '/status',
      async () => new Response('should-not-run'),
      { requiresOperatorToken: true }
    );
    const req = new Request('https://gw.test/status');
    const res = await dispatch(req, env, ctx);
    expect(res.status).toBe(401);
    const body = (await res.json()) as { error: string };
    expect(body.error).toBe('OPERATOR_AUTH');
  });

  it('extracts path params', async () => {
    register(
      'GET',
      '/plans/:id',
      async (_req, _env, _ctx, params) =>
        new Response(JSON.stringify({ plan: params.id }), {
          headers: { 'content-type': 'application/json' },
        }),
      { requiresOperatorToken: true }
    );
    const req = new Request('https://gw.test/plans/plan-abc', {
      headers: { authorization: 'Bearer test-operator-token' },
    });
    const res = await dispatch(req, env, ctx);
    expect(res.status).toBe(200);
    const body = (await res.json()) as { plan: string };
    expect(body.plan).toBe('plan-abc');
  });

  it('returns structured error for unknown routes', async () => {
    const req = new Request('https://gw.test/nope', {
      headers: { authorization: 'Bearer test-operator-token' },
    });
    const res = await dispatch(req, env, ctx);
    expect(res.status).toBe(502);
    const body = (await res.json()) as { error: string; message: string };
    expect(body.error).toBe('NETWORK');
    expect(body.message).toMatch(/no route/);
  });
});
