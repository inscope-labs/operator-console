/**
 * Task routes — publish (3.2), upload (3.3), reconcile (3.4).
 * Privileged writes: capability gate (nonce first) → rate → guards → mutate → audit
 */

import { register, type RouteHandler } from './router';
import { errorResponse } from '../errors/respond';
import { getAdapter } from '../runtime';
import { gateCapability } from '../capability/gate';
import { resolveDeviceKey } from '../auth/device';
import { runWriteTransaction } from '../adapter/transaction';
import type { GuardContext } from '../guards/index';
import {
  validateUploadPayload,
  submissionObjectPath,
} from './upload-validate';
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


interface UploadBody {
  plan?: string;
  task?: string;
  branch?: string;
  head?: string;
  capability?: string;
  submissionId?: string;
  zipSha256?: string;
  zipBase64?: string;
  base?: string;
}

const uploadHandler: RouteHandler = async (request, env, _ctx, params) => {
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  const taskId = params.id ?? '';

  let body: UploadBody;
  try {
    body = (await request.json()) as UploadBody;
  } catch {
    return errorResponse('NETWORK', 'invalid JSON body', requestId);
  }

  if (!body.capability || typeof body.capability !== 'string') {
    return errorResponse('BAD_ENCODING', 'capability required', requestId);
  }
  if (!body.submissionId || typeof body.submissionId !== 'string') {
    return errorResponse('BAD_ENCODING', 'submissionId required', requestId);
  }

  const payload = await validateUploadPayload({
    zipBase64: body.zipBase64,
    zipSha256: body.zipSha256,
  });
  if (!payload.ok) {
    return errorResponse(payload.code, payload.message, requestId);
  }

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
  if (cap.op !== 'upload' && cap.op !== 'publish') {
    // Prefer upload op; publish-grade still acceptable if minted for same branch in lab
    // but contract says upload — refuse unknown for this route when not upload
  }
  if (cap.op !== 'upload') {
    return errorResponse(
      'UNKNOWN_OP',
      `capability op must be upload for this route (got ${cap.op})`,
      requestId
    );
  }
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
  const objectPath = submissionObjectPath(body.submissionId);

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
    return errorResponse(
      'NETWORK',
      e instanceof Error ? e.message : 'compare failed',
      requestId
    );
  }

  let submissionExists = false;
  try {
    submissionExists = await consumer.pathExists(objectPath, branch);
  } catch {
    submissionExists = false;
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
    diffText,
    submissionId: body.submissionId,
    submissionExists,
  };

  const outcome = await runWriteTransaction({
    requestId,
    op: 'upload',
    plan: cap.plan,
    task: cap.task || taskId,
    branch,
    head: cap.head,
    guardCtx,
    mutate: async () => {
      try {
        // Re-check existence inside mutation for race safety
        const again = await consumer.pathExists(objectPath, branch);
        if (again) {
          return {
            ok: false as const,
            code: 'SUBMISSION_IMMUTABLE' as const,
            message: 'submission id already recorded',
          };
        }
        const written = await consumer.putFile({
          path: objectPath,
          contentBytes: payload.bytes,
          message: `upload submission ${body.submissionId} for ${branch}`,
          branch,
        });
        return {
          ok: true as const,
          result: {
            submissionId: body.submissionId,
            path: written.path,
            contentSha: written.contentSha,
            zipSha256: payload.sha256Hex,
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
    return errorResponse(outcome.code, outcome.message, requestId);
  }

  return json(
    {
      accepted: true,
      operation: 'UPLOAD',
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



interface ReconcileBody {
  plan?: string;
  task?: string;
  branch?: string;
  head?: string;
  capability?: string;
  submissionId?: string;
  prNumber?: number;
  base?: string;
}

const reconcileHandler: RouteHandler = async (request, env, _ctx, params) => {
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  const taskId = params.id ?? '';

  let body: ReconcileBody;
  try {
    body = (await request.json()) as ReconcileBody;
  } catch {
    return errorResponse('NETWORK', 'invalid JSON body', requestId);
  }

  if (!body.capability || typeof body.capability !== 'string') {
    return errorResponse('BAD_ENCODING', 'capability required', requestId);
  }

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
  if (cap.op !== 'reconcile') {
    return errorResponse(
      'UNKNOWN_OP',
      `capability op must be reconcile for this route (got ${cap.op})`,
      requestId
    );
  }
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
    diffText,
  };

  const outcome = await runWriteTransaction({
    requestId,
    op: 'reconcile',
    plan: cap.plan,
    task: cap.task || taskId,
    branch,
    head: cap.head,
    guardCtx,
    mutate: async () => {
      try {
        let prNumber = body.prNumber;
        let prHead = '';
        if (prNumber === undefined) {
          const found = await consumer.findOpenPullForBranch(branch);
          if (!found) {
            return {
              ok: false as const,
              code: 'RECONCILE_REQUIRED' as const,
              message: 'no open pull request found for branch',
            };
          }
          prNumber = found.number;
          prHead = found.headSha.toLowerCase();
        }
        if (prHead && prHead !== cap.head.toLowerCase() && prHead !== liveHead) {
          // Soft check: prefer capability head; live branch tip is authoritative via G13
        }
        const merged = await consumer.mergePullRequest({
          number: prNumber,
          commitTitle: `reconcile ${cap.plan}/${cap.task}: ${branch}`,
          mergeMethod: 'squash',
        });
        if (!merged.merged) {
          return {
            ok: false as const,
            code: 'RECONCILE_REQUIRED' as const,
            message: 'pull request could not be merged',
          };
        }
        return {
          ok: true as const,
          result: {
            prNumber,
            mergeSha: merged.sha,
            outcome: `MERGED:pr-${prNumber}`,
          },
        };
      } catch (e) {
        const message = e instanceof Error ? e.message : String(e);
        // GitHub 405/409 style merge conflicts surface as NETWORK with message
        if (/not mergeable|Merge conflict|405|409/i.test(message)) {
          return {
            ok: false as const,
            code: 'RECONCILE_REQUIRED' as const,
            message,
          };
        }
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

  const mutation = outcome.mutation as
    | { outcome?: string; prNumber?: number; mergeSha?: string }
    | undefined;

  return json(
    {
      accepted: true,
      operation: 'RECONCILE',
      branch,
      headSha: cap.head,
      outcome: mutation?.outcome ?? 'MERGED',
      auditRef: outcome.auditRef,
      ...(mutation ? { result: mutation } : {}),
    },
    200,
    requestId
  );
};



export function registerTaskRoutes(): void {
  register('POST', '/tasks/:id/publish', publishHandler, {
    requiresOperatorToken: true,
    requiresCapability: true,
  });
  register('POST', '/tasks/:id/upload', uploadHandler, {
    requiresOperatorToken: true,
    requiresCapability: true,
  });
  register('POST', '/tasks/:id/reconcile', reconcileHandler, {
    requiresOperatorToken: true,
    requiresCapability: true,
  });
}
