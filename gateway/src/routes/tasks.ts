/**
 * Task routes — status/done/upload/publish/reconcile/halt/resume.
 * Spec §9.4. This module wires publish with capability gate + core guards.
 * Full publish transaction remains Phase 2.
 */

import { getAdapter } from '../index';
import { register, type RouteHandler } from './router';
import { errorResponse } from '../errors/respond';
import { gateCapability } from '../capability/gate';
import { createResolveKey } from '../auth/device';
import { runGuards } from '../guards/index';
import '../guards/register-core';

interface PublishBody {
  capability?: string;
}

const publishHandler: RouteHandler = async (request, env, _ctx, params) => {
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  const taskId = params['id'] ?? '';

  let body: PublishBody;
  try {
    body = (await request.json()) as PublishBody;
  } catch {
    return errorResponse('NETWORK', 'invalid JSON body', requestId);
  }
  if (!body.capability || typeof body.capability !== 'string') {
    return errorResponse(
      'NETWORK',
      'capability string required in body',
      requestId
    );
  }

  const resolveKey = createResolveKey(env);

  const expectedBranch = `task-${taskId}`;
  const gated = await gateCapability(env, {
    rawCapability: body.capability,
    resolveKey,
    expectedBranch,
  });
  if (!gated.ok) {
    return errorResponse(gated.code, gated.message, requestId);
  }

  let adapter;
  try {
    adapter = await getAdapter(env);
  } catch (e) {
    return errorResponse(
      'NETWORK',
      e instanceof Error ? e.message : String(e),
      requestId
    );
  }

  // Pre-load branch meta for G4/G13
  let branchMeta: { exists: boolean; head: string } | undefined;
  try {
    const ref = await adapter.consumer().getRef(gated.capability.branch);
    branchMeta = { exists: true, head: ref.sha };
  } catch {
    branchMeta = { exists: false, head: '' };
  }

  const guardResult = await runGuards({
    capability: gated.capability,
    consumer: adapter.consumer(),
    consumerIsPrivate: true, // create() already enforced G1
    branchMeta,
  });
  if (!guardResult.ok) {
    return errorResponse(guardResult.code, guardResult.message, requestId);
  }

  // Until the real publish transaction is implemented (Stage 3.2), fail closed with 501 NOT_IMPLEMENTED.
  return errorResponse(
    'NOT_IMPLEMENTED',
    'publish transaction not yet implemented',
    requestId
  );
};

export function registerTaskRoutes(): void {
  register('POST', '/tasks/:id/publish', publishHandler, {
    requiresOperatorToken: true,
    requiresCapability: true,
  });
}
