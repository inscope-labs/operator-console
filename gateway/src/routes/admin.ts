/**
 * Admin surface — Stage 4.3.
 *
 * Paths (operator Bearer only; no unauthenticated access):
 *   GET  /admin/enrolments  — list active device enrolments (no public keys)
 *   POST /admin/revoke      — retire a device key (BD-1.10); requires confirmation
 *
 * All mutating actions emit an audit record.
 */

import { register, type RouteHandler } from './router';
import { errorResponse } from '../errors/respond';
import { emitAudit } from '../audit/emit';
import {
  listActiveEnrolments,
  revokeEnrolment,
} from '../enrolment/registry-client';

function json(data: unknown, status = 200, requestId?: string): Response {
  const headers: Record<string, string> = {
    'content-type': 'application/json',
  };
  if (requestId) headers['x-request-id'] = requestId;
  return new Response(JSON.stringify(data), { status, headers });
}

const listEnrolments: RouteHandler = async (request, env) => {
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  try {
    const records = await listActiveEnrolments(env);
    return json(
      {
        enrolments: records,
        count: records.length,
      },
      200,
      requestId
    );
  } catch (e) {
    return errorResponse(
      'NETWORK',
      e instanceof Error ? e.message : 'list enrolments failed',
      requestId
    );
  }
};

const revokeHandler: RouteHandler = async (request, env) => {
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  let body: { kid?: string; reason?: string; confirmation?: string };
  try {
    body = (await request.json()) as typeof body;
  } catch {
    return errorResponse('NETWORK', 'invalid JSON body', requestId);
  }

  if (!body.kid || typeof body.kid !== 'string') {
    return errorResponse('BAD_ENCODING', 'kid required', requestId);
  }
  const expected = `REVOKE ${body.kid}`;
  if (body.confirmation !== expected) {
    return errorResponse(
      'BAD_ENCODING',
      `confirmation must be exactly "${expected}"`,
      requestId
    );
  }

  const now = Math.floor(Date.now() / 1000);
  const result = await revokeEnrolment(env, body.kid, body.reason, now);
  if ('error' in result) {
    if (result.error === 'UNKNOWN_KID') {
      return errorResponse('UNKNOWN_KID', result.message, requestId);
    }
    return errorResponse('NETWORK', result.message, requestId);
  }

  const audit = emitAudit({
    requestId,
    op: 'admin_revoke',
    outcome: 'accepted',
    detail: {
      kid: result.kid,
      reason: body.reason,
    },
  });

  return json(
    {
      revoked: true,
      kid: result.kid,
      revokedAt: result.revokedAt,
      auditRef: audit.auditRef,
    },
    200,
    requestId
  );
};

export function registerAdminRoutes(): void {
  register('GET', '/admin/enrolments', listEnrolments, {
    requiresOperatorToken: true,
  });
  register('POST', '/admin/revoke', revokeHandler, {
    requiresOperatorToken: true,
  });
}
