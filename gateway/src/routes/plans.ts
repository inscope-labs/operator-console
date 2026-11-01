/**
 * Plan list / detail (read).
 * Spec §9.3.
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

const listPlans: RouteHandler = async (request, env, _ctx, _params) => {
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  try {
    const adapter = await getAdapter(env);
    // Convention: plans live under plans/ in the consumer repo.
    const listing = await adapter.consumer().getContents('plans');
    const plans =
      listing.type === 'dir'
        ? [] // directory listing payload not expanded in minimal client yet
        : [];
    return json({
      ok: true,
      surface: 'plans',
      phase: 1,
      plans,
      path_sha: listing.sha || null,
    });
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e);
    if (message.includes('credentials incomplete')) {
      return json({
        ok: true,
        surface: 'plans',
        phase: 1,
        plans: [],
        message: 'adapter not configured — stub response',
      });
    }
    return errorResponse('NETWORK', message, requestId);
  }
};

const getPlan: RouteHandler = async (request, env, _ctx, params) => {
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  const planId = params['id'] ?? '';
  try {
    const adapter = await getAdapter(env);
    const file = await adapter
      .consumer()
      .getContents(`plans/${planId}/plan.json`);
    return json({
      ok: true,
      surface: 'plans/:id',
      phase: 1,
      plan: planId,
      sha: file.sha,
      body: file.type === 'file' ? file.content : null,
    });
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e);
    if (message.includes('credentials incomplete')) {
      return json({
        ok: true,
        surface: 'plans/:id',
        phase: 1,
        plan: planId,
        body: null,
        message: 'adapter not configured — stub response',
      });
    }
    return errorResponse('NETWORK', message, requestId);
  }
};

export function registerPlanRoutes(): void {
  const auth = { requiresOperatorToken: true, requiresCapability: false };
  register('GET', '/plans', listPlans, auth);
  register('GET', '/plans/:id', getPlan, auth);
}
