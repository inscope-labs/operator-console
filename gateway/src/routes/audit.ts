/**
 * GET /audit and GET /audit/:ref — Stage 5.1 / contract §3.20.
 */

import { register, type RouteHandler } from './router';
import { errorResponse } from '../errors/respond';
import {
  listAudits,
  resolveAuditRef,
  toAuditSummary,
} from '../audit/emit';

function json(data: unknown, status = 200, requestId?: string): Response {
  const headers: Record<string, string> = {
    'content-type': 'application/json',
  };
  if (requestId) headers['x-request-id'] = requestId;
  return new Response(JSON.stringify(data), { status, headers });
}

const listHandler: RouteHandler = async (request, env) => {
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  try {
    const records = await listAudits(env, 50);
    return json(
      records.map(toAuditSummary),
      200,
      requestId
    );
  } catch (e) {
    return errorResponse(
      'NETWORK',
      e instanceof Error ? e.message : 'audit list failed',
      requestId
    );
  }
};

const getHandler: RouteHandler = async (request, env, _ctx, params) => {
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  const ref = params.ref ?? params.id ?? '';
  try {
    const rec = await resolveAuditRef(env, ref);
    if (!rec) {
      return errorResponse('NETWORK', `audit ref not found: ${ref}`, requestId);
    }
    return json(toAuditSummary(rec), 200, requestId);
  } catch (e) {
    return errorResponse(
      'NETWORK',
      e instanceof Error ? e.message : 'audit lookup failed',
      requestId
    );
  }
};

export function registerAuditRoutes(): void {
  register('GET', '/audit', listHandler, { requiresOperatorToken: true });
  register('GET', '/audit/:ref', getHandler, { requiresOperatorToken: true });
}
