/**
 * GET /operations/:id — BD-1.7 operation lookup (contract §3.19).
 * Operator Bearer only; no capability; never executes or retries.
 */

import { register, type RouteHandler } from './router';
import { errorResponse } from '../errors/respond';
import { isValidIdempotencyKey } from '../idempotency/key';
import {
  extractBearerToken,
  hashOperatorCredential,
} from '../idempotency/credential';
import { getOperation } from '../idempotency/store-client';

function json(data: unknown, status = 200, requestId: string): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'content-type': 'application/json',
      'x-request-id': requestId,
    },
  });
}

const lookupHandler: RouteHandler = async (request, env, _ctx, params) => {
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  const id = params.id ?? '';

  if (!isValidIdempotencyKey(id)) {
    return errorResponse(
      'IDEMPOTENCY_KEY_INVALID',
      'idempotencyKey is missing or is not 32 lowercase hex characters',
      requestId
    );
  }

  const token = extractBearerToken(request);
  if (!token) {
    return errorResponse(
      'OPERATOR_AUTH',
      'Bearer OPERATOR_TOKEN required or invalid',
      requestId
    );
  }
  const credentialHash = await hashOperatorCredential(token);

  const rec = await getOperation(env, credentialHash, id);
  // Pending / missing / other credential → 404 (do not distinguish)
  if (!rec || rec.status === 'pending') {
    return errorResponse(
      'OPERATION_NOT_FOUND',
      'No operation record is visible for this key under the calling credential',
      requestId
    );
  }

  const body: Record<string, unknown> = {
    id: rec.id,
    operation: rec.operation,
    status: rec.status,
    timestamp: rec.timestamp,
  };
  if (rec.branch !== undefined) body.branch = rec.branch;
  if (rec.headSha !== undefined) body.headSha = rec.headSha;
  if (rec.auditRef !== undefined) body.auditRef = rec.auditRef;
  if (rec.status === 'failed' || rec.status === 'refused') {
    body.code = rec.code ?? 'NETWORK';
  }
  // completed must carry auditRef and headSha per schema
  if (rec.status === 'completed') {
    if (!body.auditRef) body.auditRef = '';
    if (!body.headSha) body.headSha = '';
  }

  return json(body, 200, requestId);
};

export function registerOperationRoutes(): void {
  register('GET', '/operations/:id', lookupHandler, {
    requiresOperatorToken: true,
  });
}
