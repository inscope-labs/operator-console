/**
 * Task routes — status/done/upload/publish/reconcile/halt/resume.
 * Spec §9.4. This module wires publish with capability gate + core guards.
 * Full publish transaction remains Phase 2.
 */

import type { Env } from '../index';
import { getAdapter } from '../index';
import { register, type RouteHandler } from './router';
import { errorResponse } from '../errors/respond';
import { gateCapability } from '../capability/gate';
import { runGuards } from '../guards/index';
import '../guards/register-core';

const json = (body: unknown, status = 200): Response =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });

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

  // resolveKey: Model B looks up enrolled device keys from SIGNING_KEYS / enrolment DO.
  // Until enrolment is wired, attempt JSON map of kid → (not supported for real verify).
  // Fail closed via UNKNOWN_KID unless a test injects keys later.
  const resolveKey = async (kid: string): Promise<CryptoKey | ArrayBuffer | null> => {
    // Placeholder: enrolment registry integration is a later item.
    // SIGNING_KEYS holds public keys only (JSON); parsing left for enrolment work.
    void kid;
    void env.SIGNING_KEYS;
    return null;
  };

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

  // Phase 2: MultiRepoGitHubAdapter.transaction for the real publish.
  return json({
    ok: true,
    surface: 'tasks/:id/publish',
    phase: 1,
    task: taskId,
    branch: gated.capability.branch,
    head: gated.capability.head,
    message:
      'capability + nonce + core guards passed — publish transaction not yet implemented',
  });
};

export function registerTaskRoutes(): void {
  register('POST', '/tasks/:id/publish', publishHandler, {
    requiresOperatorToken: true,
    requiresCapability: true,
  });
}
