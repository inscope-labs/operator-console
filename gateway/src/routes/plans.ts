/**
 * Plan list / detail (read) and plans/new (write — capability later).
 * Spec §9.3 / §9.4.
 */

import type { Env } from '../index';
import { register, type RouteHandler } from './router';

const json = (body: unknown, status = 200): Response =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });

const listPlans: RouteHandler = async (_request, _env, _ctx, _params) => {
  // TODO: list plans from consumer repository via adapter
  return json({
    ok: true,
    surface: 'plans',
    phase: 1,
    plans: [],
    message: 'read-only stub — adapter not yet wired',
  });
};

const getPlan: RouteHandler = async (_request, _env, _ctx, params) => {
  const planId = params['id'] ?? params['plan'] ?? '';
  return json({
    ok: true,
    surface: 'plans/:id',
    phase: 1,
    plan: planId,
    body: null,
    message: 'read-only stub — adapter not yet wired',
  });
};

/** Side-effect registration. */
export function registerPlanRoutes(): void {
  const auth = { requiresOperatorToken: true, requiresCapability: false };
  register('GET', '/plans', listPlans, auth);
  register('GET', '/plans/:id', getPlan, auth);
  // POST /plans/new is confirmation-bearing — registered later with capability.
}
