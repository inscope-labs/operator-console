/**
 * Canonical JSON error response helper.
 * Spec §15 — every error is { error, message, request_id }.
 */

import { HTTP_STATUS, type ErrorCode, type GatewayErrorBody } from './catalogue';

export function errorResponse(
  code: ErrorCode,
  message: string,
  requestId: string = crypto.randomUUID()
): Response {
  const body: GatewayErrorBody = {
    error: code,
    message,
    request_id: requestId,
  };
  return new Response(JSON.stringify(body), {
    status: HTTP_STATUS[code] ?? 500,
    headers: {
      'content-type': 'application/json',
      'x-request-id': requestId,
    },
  });
}

/** Convenience for the common auth failures. */
export function operatorAuthResponse(requestId?: string): Response {
  return errorResponse(
    'OPERATOR_AUTH',
    'Bearer OPERATOR_TOKEN required or invalid',
    requestId
  );
}

export function capabilityExpiredResponse(requestId?: string): Response {
  return errorResponse(
    'CAPABILITY_EXPIRED',
    'Capability has expired',
    requestId
  );
}
