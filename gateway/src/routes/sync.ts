/**
 * POST /sync, GET /verify-main
 *
 * Fail-closed stub returning 501 NOT_IMPLEMENTED (T1-103) until Phase 4 (Stage 4.2).
 */

import { register, type RouteHandler } from './router';
import { errorResponse } from '../errors/respond';

const syncStubHandler: RouteHandler = async (request) => {
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  return errorResponse(
    'NOT_IMPLEMENTED',
    'sync endpoint not yet implemented',
    requestId
  );
};

export function registerSyncRoutes(): void {
  register('POST', '/sync', syncStubHandler, {
    requiresOperatorToken: true,
  });
  register('GET', '/verify-main', syncStubHandler, {
    requiresOperatorToken: true,
  });
}
