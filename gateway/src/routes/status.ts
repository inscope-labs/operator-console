/**
 * Read-only status routes.
 * Spec §9.3 — no capability required; OPERATOR_TOKEN required.
 */

import type { Env } from '../index';
import { getAdapter } from '../index';
import { register, type RouteHandler } from './router';
import { errorResponse } from '../errors/respond';

const json = (body: unknown, status = 200): Response =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });

const getStatus: RouteHandler = async (request, env, _ctx, _params) => {
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  try {
    const adapter = await getAdapter(env);
    // Probe consumer main ref as a liveness signal.
    let head: string | null = null;
    try {
      const ref = await adapter.consumer().getRef('main');
      head = ref.sha;
    } catch {
      head = null;
    }
    return json({
      ok: true,
      surface: 'status',
      phase: 1,
      consumer_main_sha: head,
      token_expires_at: adapter.tokenExpiresAt(),
    });
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e);
    // Credentials missing → still return a structured stub so the route is usable in CI.
    if (message.includes('credentials incomplete')) {
      return json({
        ok: true,
        surface: 'status',
        phase: 1,
        message: 'adapter not configured — stub response',
        consumer_main_sha: null,
        token_expires_at: null,
      });
    }
    return errorResponse('NETWORK', message, requestId);
  }
};

const getNext: RouteHandler = async (_request, _env, _ctx, _params) => {
  return json({
    ok: true,
    surface: 'next',
    phase: 1,
    tasks: [],
    message: 'plan store not yet wired',
  });
};

const getCheck: RouteHandler = async (_request, _env, _ctx, _params) => {
  return json({
    ok: true,
    surface: 'check',
    phase: 1,
    valid: null,
    message: 'plan validation not yet wired',
  });
};

const getRenderCheck: RouteHandler = async (_request, _env, _ctx, _params) => {
  return json({
    ok: true,
    surface: 'render/check',
    phase: 1,
    current: null,
    message: 'render check not yet wired',
  });
};

const getCoverage: RouteHandler = async (_request, _env, _ctx, _params) => {
  return json({
    ok: true,
    surface: 'coverage',
    phase: 1,
    report: null,
    message: 'coverage not yet wired',
  });
};

export function registerStatusRoutes(): void {
  const auth = { requiresOperatorToken: true, requiresCapability: false };
  register('GET', '/status', getStatus, auth);
  register('GET', '/next', getNext, auth);
  register('GET', '/check', getCheck, auth);
  register('GET', '/render/check', getRenderCheck, auth);
  register('GET', '/coverage', getCoverage, auth);
}
