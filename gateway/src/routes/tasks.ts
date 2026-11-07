/**
 * Task routes — publish / upload / reconcile stubs.
 * Spec §9.4. Full transactions land in Phase 3; until then every write is
 * fail-closed with 501 NOT_IMPLEMENTED (Stage 1.1 / T1-103).
 *
 * The refusal is side-effect free: the body is not interpreted, the capability
 * is not verified, and no nonce is consumed. Operator-token and config gates
 * still run in the router before this handler is reached.
 */

import { register, type RouteHandler } from './router';
import { errorResponse } from '../errors/respond';

const notImplemented: RouteHandler = async (request) => {
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  return errorResponse(
    'NOT_IMPLEMENTED',
    'publish transaction not yet implemented',
    requestId
  );
};

export function registerTaskRoutes(): void {
  register('POST', '/tasks/:id/publish', notImplemented, {
    requiresOperatorToken: true,
    requiresCapability: true,
  });
  // Upload / reconcile remain unregistered until Stage 3; router 404/502 is
  // acceptable until those stages add explicit 501 stubs.
}
