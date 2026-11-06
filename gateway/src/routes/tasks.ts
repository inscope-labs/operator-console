/**
 * Task routes — status/done/upload/publish/reconcile/halt/resume.
 * Spec §9.4.
 *
 * Stage 1.1 (GATEWAY-A-COMPLETION-PLAN): the publish transaction does not exist
 * in this build. `POST /tasks/:id/publish` is therefore a fail-closed stub that
 * answers HTTP 501 `NOT_IMPLEMENTED` for every request. It never returns 2xx and
 * never claims that a publication happened.
 *
 * The stub refuses at the capability boundary on purpose:
 *   - the request body is never parsed, so no payload is interpreted;
 *   - the capability is never verified and its nonce is never consumed, so an
 *     operator does not spend a single-use capability on a call that cannot run;
 *   - no GitHub adapter is constructed and no repository state is read or written.
 *
 * The operator-token and configuration gates still apply — the router rejects
 * both before this handler runs — so the stub is not an open surface.
 *
 * The real transaction (capability gate → ordered guards → GitHub mutation →
 * audit record) is Stage 3.2 of the same plan.
 */

import { register, type RouteHandler } from './router';
import { errorResponse } from '../errors/respond';
import '../guards/register-core';

/**
 * Stable refusal text. Contains no success wording, no "accepted" claim and no
 * caller-controlled input.
 */
const PUBLISH_NOT_IMPLEMENTED =
  'Publish is not implemented in this gateway build; no branch was published';

const publishHandler: RouteHandler = async (request, _env, _ctx, _params) => {
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  return errorResponse('NOT_IMPLEMENTED', PUBLISH_NOT_IMPLEMENTED, requestId);
};

export function registerTaskRoutes(): void {
  register('POST', '/tasks/:id/publish', publishHandler, {
    requiresOperatorToken: true,
    requiresCapability: true,
  });
}
