/**
 * POST /halt and POST /resume — Stage 3.5.
 * Engine-scoped capability (branch === engine, op halt|resume).
 */

import { register, type RouteHandler } from './router';
import { errorResponse } from '../errors/respond';
import { gateCapability } from '../capability/gate';
import { resolveDeviceKey } from '../auth/device';
import { emitAudit } from '../audit/emit';
import { engageHalt, clearHalt, isHalted } from '../halt/state';

function json(data: unknown, status = 200, requestId: string): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'content-type': 'application/json',
      'x-request-id': requestId,
    },
  });
}

interface HaltBody {
  branch?: string;
  head?: string;
  reason?: string;
  capability?: string;
}

async function engineGate(
  env: Parameters<RouteHandler>[1],
  body: HaltBody,
  expectedOp: 'halt' | 'resume',
  requestId: string
) {
  if (!body.capability || typeof body.capability !== 'string') {
    return { error: errorResponse('BAD_ENCODING', 'capability required', requestId) };
  }
  const allowModelA = (env.ENVIRONMENT ?? 'test') !== 'production';
  const gate = await gateCapability(env, {
    rawCapability: body.capability,
    expectedBranch: body.branch ?? 'engine',
    allowModelA,
    resolveKey: async (kid) => {
      const resolved = await resolveDeviceKey(env, kid);
      return resolved ? resolved.key : null;
    },
  });
  if (!gate.ok) {
    return { error: errorResponse(gate.code, gate.message, requestId) };
  }
  const cap = gate.capability;
  if (cap.op !== expectedOp) {
    return {
      error: errorResponse(
        'UNKNOWN_OP',
        `capability op must be ${expectedOp} (got ${cap.op})`,
        requestId
      ),
    };
  }
  if (cap.branch !== 'engine') {
    return {
      error: errorResponse(
        'BRANCH_INVALID',
        'halt/resume capability branch must be engine',
        requestId
      ),
    };
  }
  if (body.branch && body.branch !== 'engine') {
    return {
      error: errorResponse(
        'BRANCH_MISMATCH',
        'body branch must be engine',
        requestId
      ),
    };
  }
  if (body.head && body.head.toLowerCase() !== cap.head.toLowerCase()) {
    return {
      error: errorResponse(
        'HEAD_MOVED',
        'body head does not match capability head',
        requestId
      ),
    };
  }
  return { cap };
}

const haltHandler: RouteHandler = async (request, env) => {
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  let body: HaltBody;
  try {
    body = (await request.json()) as HaltBody;
  } catch {
    return errorResponse('NETWORK', 'invalid JSON body', requestId);
  }

  const gated = await engineGate(env, body, 'halt', requestId);
  if ('error' in gated && gated.error) return gated.error;
  const cap = gated.cap!;

  await engageHalt(env, {
    reason: body.reason,
    requestId,
    now: Math.floor(Date.now() / 1000),
  });

  const audit = emitAudit({
    requestId,
    op: 'halt',
    outcome: 'accepted',
    branch: 'engine',
    head: cap.head,
    detail: { reason: body.reason },
  });

  return json(
    {
      accepted: true,
      operation: 'HALT',
      branch: 'engine',
      headSha: cap.head,
      auditRef: audit.auditRef,
    },
    200,
    requestId
  );
};

const resumeHandler: RouteHandler = async (request, env) => {
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  let body: HaltBody;
  try {
    body = (await request.json()) as HaltBody;
  } catch {
    return errorResponse('NETWORK', 'invalid JSON body', requestId);
  }

  const gated = await engineGate(env, body, 'resume', requestId);
  if ('error' in gated && gated.error) return gated.error;
  const cap = gated.cap!;

  await clearHalt(env, {
    reason: body.reason,
    requestId,
    now: Math.floor(Date.now() / 1000),
  });

  const audit = emitAudit({
    requestId,
    op: 'resume',
    outcome: 'accepted',
    branch: 'engine',
    head: cap.head,
    detail: { reason: body.reason },
  });

  return json(
    {
      accepted: true,
      operation: 'RESUME',
      branch: 'engine',
      headSha: cap.head,
      auditRef: audit.auditRef,
    },
    200,
    requestId
  );
};

export function registerHaltRoutes(): void {
  register('POST', '/halt', haltHandler, {
    requiresOperatorToken: true,
    requiresCapability: true,
  });
  register('POST', '/resume', resumeHandler, {
    requiresOperatorToken: true,
    requiresCapability: true,
  });
}

export { isHalted };
