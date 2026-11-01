/**
 * Read-only status routes.
 * Spec §9.3 — no capability required.
 * Authenticated with OPERATOR_TOKEN only (gateway operator surface).
 *
 * Phase 1 stubs: structured responses without live GitHub data.
 * Adapter integration lands in item #3.
 */

import type { Env } from '../index';
import { register, type RouteHandler } from './router';

const json = (body: unknown, status = 200): Response =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });

const getStatus: RouteHandler = async (_request, _env, _ctx, _params) => {
  // TODO: comparative repository state via MultiRepoGitHubAdapter
  return json({
    ok: true,
    surface: 'status',
    phase: 1,
    message: 'read-only stub — adapter not yet wired',
    repositories: null,
    halt: false,
  });
};

const getNext: RouteHandler = async (_request, _env, _ctx, _params) => {
  // TODO: next actionable tasks from plan store
  return json({
    ok: true,
    surface: 'next',
    phase: 1,
    tasks: [],
    message: 'read-only stub — plan store not yet wired',
  });
};

const getCheck: RouteHandler = async (_request, _env, _ctx, _params) => {
  // TODO: validate the plan via adapter
  return json({
    ok: true,
    surface: 'check',
    phase: 1,
    valid: null,
    message: 'read-only stub — plan validation not yet wired',
  });
};

const getRenderCheck: RouteHandler = async (_request, _env, _ctx, _params) => {
  return json({
    ok: true,
    surface: 'render/check',
    phase: 1,
    current: null,
    message: 'read-only stub — render check not yet wired',
  });
};

const getCoverage: RouteHandler = async (_request, _env, _ctx, _params) => {
  return json({
    ok: true,
    surface: 'coverage',
    phase: 1,
    report: null,
    message: 'read-only stub — coverage not yet wired',
  });
};

/** Side-effect: register routes at module load. */
export function registerStatusRoutes(): void {
  // Read-only routes still require OPERATOR_TOKEN so the surface is not public.
  const auth = { requiresOperatorToken: true, requiresCapability: false };
  register('GET', '/status', getStatus, auth);
  register('GET', '/next', getNext, auth);
  register('GET', '/check', getCheck, auth);
  register('GET', '/render/check', getRenderCheck, auth);
  register('GET', '/coverage', getCoverage, auth);
}
