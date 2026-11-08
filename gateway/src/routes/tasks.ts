/**
 * Task routes — publish (Stage 3.2) + remaining stubs.
 * POST /tasks/:id/publish runs the privileged write pipeline:
 *   capability gate (nonce first) → rate → guards → createPullRequest → audit
 */

import { register, type RouteHandler } from './router';
import { errorResponse } from '../errors/respond';
import { getAdapter } from '../runtime';
import { gateCapability } from '../capability/gate';
import { resolveDeviceKey } from '../auth/device';
import { runWriteTransaction } from '../adapter/transaction';
import type { GuardContext } from '../guards/index';
import './../guards/register-core';

interface PublishBody {
  plan?: string;
  task?: string;
  branch?: string;
  head?: string;
  capability?: string;
  base?: string;
}

function json(data: unknown, status = 200, requestId: string): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'content-type': 'application/json',
      'x-request-id': requestId,
    },
  });
}

const publishHandler: RouteHandler = async (request, env, _ctx, params) => {
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  const taskId = params.id ?? '';

  let body: PublishBody;
  try {
    body = (await request.json()) as PublishBody;
  } catch {
    return errorResponse('NETWORK', 'invalid JSON body', requestId);
  }

  if (!body.capability || typeof body.capability !== 'string') {
    return errorResponse('BAD_ENCODING', 'capability required', requestId);
  }

  // Lab HMAC keys (SIGNING_KEYS) are Model A; permit only outside production.
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
    return errorResponse(gate.code, gate.message, requestId);
  }

  const cap = gate.capability;
  // Body fields must agree with capability when supplied.
  if (body.branch && body.branch !== cap.branch) {
    return errorResponse(
      'BRANCH_MISMATCH',
      'body branch does not match capability branch',
      requestId
    );
  }
  if (body.head && body.head.toLowerCase() !== cap.head.toLowerCase()) {
    return errorResponse(
      'HEAD_MOVED',
      'body head does not match capability head',
      requestId
    );
  }
  if (body.task && body.task !== cap.task && body.task !== taskId) {
    return errorResponse(
      'NETWORK',
      'task id does not match capability task',
      requestId
    );
  }

  let adapter;
  try {
    adapter = await getAdapter(env);
  } catch (e) {
    return errorResponse(
      'NETWORK',
      e instanceof Error ? e.message : 'adapter unavailable',
      requestId
    );
  }

  const consumer = adapter.consumer();
  const branch = cap.branch;
  const base = body.base ?? 'main';

  // Live branch tip for G4 / G13
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
  let diffText = '';
  try {
    if (exists) {
      const cmp = await consumer.compare(base, branch);
      changedPaths = cmp.files;
      deltaEmpty = cmp.aheadBy === 0;
      hasMergeCommits = cmp.hasMergeCommits;
      isAncestryOfMain = cmp.isAncestryOfBase;
      diffText = changedPaths.join('\n');
    }
  } catch (e) {
    return errorResponse(
      'NETWORK',
      e instanceof Error ? e.message : 'compare failed',
      requestId
    );
  }

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
    haltActive: false,
    diffText: diffText || ' ',
  };

  const outcome = await runWriteTransaction({
    requestId,
    op: 'publish',
    plan: cap.plan,
    task: cap.task,
    branch,
    head: cap.head,
    guardCtx,
    mutate: async () => {
      try {
        const pr = await consumer.createPullRequest({
          title: `publish ${cap.plan}/${cap.task}: ${branch}`,
          head: branch,
          base,
          body: `Automated publish of ${branch} @ ${cap.head}`,
        });
        return {
          ok: true as const,
          result: { prNumber: pr.number, htmlUrl: pr.htmlUrl, headSha: pr.headSha || cap.head },
        };
      } catch (e) {
        const message = e instanceof Error ? e.message : String(e);
        return {
          ok: false as const,
          code: 'NETWORK' as const,
          message,
        };
      }
    },
  });

  if (!outcome.ok) {
    return errorResponse(outcome.code, outcome.message, requestId);
  }

  return json(
    {
      accepted: true,
      operation: 'PUBLISH',
      branch,
      headSha: cap.head,
      auditRef: outcome.auditRef,
      ...(outcome.mutation && typeof outcome.mutation === 'object'
        ? { result: outcome.mutation }
        : {}),
    },
    200,
    requestId
  );
};

const notImplemented: RouteHandler = async (request) => {
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  return errorResponse(
    'NOT_IMPLEMENTED',
    'transaction not yet implemented',
    requestId
  );
};

export function registerTaskRoutes(): void {
  register('POST', '/tasks/:id/publish', publishHandler, {
    requiresOperatorToken: true,
    requiresCapability: true,
  });
  // Upload / reconcile remain 501 until their stages.
  register('POST', '/tasks/:id/upload', notImplemented, {
    requiresOperatorToken: true,
    requiresCapability: true,
  });
  register('POST', '/tasks/:id/reconcile', notImplemented, {
    requiresOperatorToken: true,
    requiresCapability: true,
  });
}
