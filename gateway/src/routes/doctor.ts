/**
 * POST /doctor preflight & diagnostics
 *
 * Fail-closed stub returning 501 NOT_IMPLEMENTED (T1-103) until Phase 4 (Stage 4.2).
 */

import { register, type RouteHandler } from './router';
import { errorResponse } from '../errors/respond';

const doctorStubHandler: RouteHandler = async (request) => {
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  return errorResponse(
    'NOT_IMPLEMENTED',
    'doctor endpoint not yet implemented',
    requestId
  );
};

export function registerDoctorRoutes(): void {
  register('GET', '/doctor', doctorStubHandler, {
    requiresOperatorToken: true,
  });
  register('POST', '/doctor', doctorStubHandler, {
    requiresOperatorToken: true,
  });
}
