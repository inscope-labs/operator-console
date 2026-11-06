/**
 * /admin/* surface (OPERATOR_TOKEN only)
 *
 * Fail-closed stub returning 501 NOT_IMPLEMENTED (T1-103) until Phase 4 (Stage 4.3).
 */

import { register, type RouteHandler } from './router';
import { errorResponse } from '../errors/respond';

const adminStubHandler: RouteHandler = async (request) => {
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  return errorResponse(
    'NOT_IMPLEMENTED',
    'admin routes not yet implemented',
    requestId
  );
};

export function registerAdminRoutes(): void {
  register('GET', '/admin/enrolments', adminStubHandler, {
    requiresOperatorToken: true,
  });
  register('POST', '/admin/revoke', adminStubHandler, {
    requiresOperatorToken: true,
  });
  register('POST', '/admin/:action', adminStubHandler, {
    requiresOperatorToken: true,
  });
}
