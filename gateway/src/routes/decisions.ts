/**
 * POST /plans/:id/decide — Stage 3.6 (BD-1.3).
 * Also registers legacy POST /decisions/:id/decide for compatibility.
 *
 * Required body fields (plan Stage 3.6): attribution, rationale, scope, evidence.
 * Contract §3.18 also accepts decision/notes; notes maps to rationale when present.
 */

import { register, type RouteHandler } from './router';
import { errorResponse } from '../errors/respond';
import { getAdapter } from '../runtime';
import { gateCapability } from '../capability/gate';
import { resolveDeviceKey } from '../auth/device';
import { runWriteTransaction } from '../adapter/transaction';
import type { GuardContext } from '../guards/index';
import { isHalted } from '../halt/state';
import {
  gateIdempotencyWrite,
  finishFromResponse,
} from '../idempotency/write-gate';
import '../guards/register-core';

function json(data: unknown, status = 200, requestId: string): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'content-type': 'application/json',
      'x-request-id': requestId,
    },
  });
}

interface DecideBody {
  plan?: string;
  task?: string;
  branch?: string;
  head?: string;
  decision?: string;
  notes?: string;
  attribution?: string;
  rationale?: string;
  scope?: string;
  evidence?: string;
  capability?: string;
  base?: string;
}

function requireFields(body: DecideBody): { ok: true } | { ok: false; message: string } {
  const attribution = body.attribution?.trim();
  const rationale = (body.rationale ?? body.notes)?.trim();
  const scope = body.scope?.trim();
  const evidence = body.evidence?.trim();
  const decision = body.decision?.trim();
  if (!attribution) {
    return { ok: false, message: 'attribution required' };
  }
  if (!rationale) {
    return { ok: false, message: 'rationale (or notes) required' };
  }
  if (!scope) {
    return { ok: false, message: 'scope required' };
  }
  if (!evidence) {
    return { ok: false, message: 'evidence required' };
  }
  if (!decision) {
    return { ok: false, message: 'decision required' };
  }
  return { ok: true };
}

const decideHandler: RouteHandler = async (request, env, _ctx, params) => {
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  const planId = params.id ?? '';

  let body: DecideBody;
  try {
    body = (await request.json()) as DecideBody;
  } catch {
    return errorResponse('NETWORK', 'invalid JSON body', requestId);
  }

  const idem = await gateIdempotencyWrite(
    request,
    env,
    'DECIDE',
    body as unknown as Record<string, unknown>,
    requestId
  );
  if (!idem.ok) return idem.response;
  if (idem.replay) return idem.replay;
  const _idemKey = idem.key;
  const _idemCred = idem.credentialHash;


  if (!body.capability || typeof body.capability !== 'string') {
    return await finishFromResponse(env, _idemCred, _idemKey, errorResponse('BAD_ENCODING', 'capability required', requestId));
  }

  const fields = requireFields(body);
  if (!fields.ok) {
    return await finishFromResponse(env, _idemCred, _idemKey, errorResponse('BAD_ENCODING', fields.message, requestId));
  }

  const attribution = body.attribution!.trim();
  const rationale = (body.rationale ?? body.notes)!.trim();
  const scope = body.scope!.trim();
  const evidence = body.evidence!.trim();
  const decision = body.decision!.trim();

  const allowModelA = (env.ENVIRONMENT ?? 'test') !== 'production';
  const gate = await gateCapability(env, {
    rawCapability: body.capability,
    expectedBranch: body.branch,
    allowModelA,
    resolveKey: async (kid) => {
      const resolved = await resolveDeviceKey(env, kid);
      return resolved ? resolved.key : null;
    },
  });
  if (!gate.ok) {
    return await finishFromResponse(env, _idemCred, _idemKey, errorResponse(gate.code, gate.message, requestId));
  }

  const cap = gate.capability;
  if (cap.op !== 'decide') {
    return await finishFromResponse(env, _idemCred, _idemKey, errorResponse(
      'UNKNOWN_OP',
      `capability op must be decide (got ${cap.op})`,
      requestId
    ));
  }
  if (body.branch && body.branch !== cap.branch) {
    return await finishFromResponse(env, _idemCred, _idemKey, errorResponse(
      'BRANCH_MISMATCH',
      'body branch does not match capability branch',
      requestId
    ));
  }
  if (body.head && body.head.toLowerCase() !== cap.head.toLowerCase()) {
    return await finishFromResponse(env, _idemCred, _idemKey, errorResponse(
      'HEAD_MOVED',
      'body head does not match capability head',
      requestId
    ));
  }

  let adapter;
  try {
    adapter = await getAdapter(env);
  } catch (e) {
    return await finishFromResponse(env, _idemCred, _idemKey, errorResponse(
      'NETWORK',
      e instanceof Error ? e.message : 'adapter unavailable',
      requestId
    ));
  }

  const consumer = adapter.consumer();
  const branch = cap.branch;
  const base = body.base ?? 'main';
  const plan = body.plan ?? planId ?? cap.plan;

  let liveHead = '';
  let exists = false;
  try {
    const ref = await consumer.getRef(branch);
    liveHead = ref.sha.toLowerCase();
    exists = true;
  } catch {
    exists = false;
  }

  let changedPaths: string[] = [];
  let deltaEmpty = true;
  let hasMergeCommits = false;
  let isAncestryOfMain = false;
  let diffText = ' ';
  try {
    if (exists) {
      const cmp = await consumer.compare(base, branch);
      changedPaths = cmp.files;
      deltaEmpty = cmp.aheadBy === 0;
      hasMergeCommits = cmp.hasMergeCommits;
      isAncestryOfMain = cmp.isAncestryOfBase;
      diffText = changedPaths.join('\n') || ' ';
    }
  } catch (e) {
    return await finishFromResponse(env, _idemCred, _idemKey, errorResponse(
      'NETWORK',
      e instanceof Error ? e.message : 'compare failed',
      requestId
    ));
  }

  const haltActive = await isHalted(env);
  const guardCtx: GuardContext = {
    capability: cap,
    consumer,
    consumerIsPrivate: true,
    branchMeta: {
      exists,
      head: liveHead || cap.head,
      isAncestryOfMain,
      hasMergeCommits,
      changedPaths,
      deltaEmpty,
    },
    haltActive,
    diffText,
  };

  const decisionRecord = {
    plan,
    task: body.task ?? cap.task,
    branch,
    head: cap.head,
    decision,
    attribution,
    rationale,
    scope,
    evidence,
    at: Math.floor(Date.now() / 1000),
    requestId,
  };

  const outcome = await runWriteTransaction({
    env,
    requestId,
    op: 'decide',
    plan,
    task: cap.task,
    branch,
    head: cap.head,
    guardCtx,
    mutate: async () => {
      try {
        // Durable record under decisions/ (not a denied path: engine/, plans/, .github/)
        const path = `decisions/${plan}-${cap.task}-${requestId.slice(0, 8)}.json`;
        const bytes = new TextEncoder().encode(
          JSON.stringify(decisionRecord, null, 2)
        );
        const written = await consumer.putFile({
          path,
          contentBytes: bytes,
          message: `decide ${decision} on ${plan}/${cap.task}`,
          branch,
        });
        return {
          ok: true as const,
          result: {
            decision,
            path: written.path,
            contentSha: written.contentSha,
          },
        };
      } catch (e) {
        return {
          ok: false as const,
          code: 'NETWORK' as const,
          message: e instanceof Error ? e.message : String(e),
        };
      }
    },
  });

  if (!outcome.ok) {
    return await finishFromResponse(env, _idemCred, _idemKey, errorResponse(outcome.code, outcome.message, requestId));
  }

  return await finishFromResponse(env, _idemCred, _idemKey, json(
    {
      accepted: true,
      operation: 'DECIDE',
      plan,
      decision,
      auditRef: outcome.auditRef,
    },
    200,
    requestId
  ));
};

export function registerDecisionRoutes(): void {
  register('POST', '/plans/:id/decide', decideHandler, {
    requiresOperatorToken: true,
    requiresCapability: true,
  });
  // Legacy path from Stage 1 stub
  register('POST', '/decisions/:id/decide', decideHandler, {
    requiresOperatorToken: true,
    requiresCapability: true,
  });
}
