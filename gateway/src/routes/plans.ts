/**
 * Plan list / status / validate / render-check — Stage 4.1.
 * Spec §3.2–3.5.
 *
 * Also POST /plans/:id/status — contract §3.17 (revision 1.3): the guarded
 * plan status transition with compare-and-swap.
 */

import { register, type RouteHandler } from './router';
import { errorResponse } from '../errors/respond';
import type { ErrorCode } from '../errors/catalogue';
import {
  listPlans,
  getPlanRecord,
  putPlanRecord,
  type PlanRecord,
} from '../plan/client';
import { isHalted } from '../halt/state';
import { getAdapter } from '../runtime';
import { gateCapability } from '../capability/gate';
import { resolveDeviceKey } from '../auth/device';
import { emitAudit } from '../audit/emit';
import {
  gateIdempotencyWrite,
  finishFromResponse,
} from '../idempotency/write-gate';

function json(
  data: unknown,
  status = 200,
  extraHeaders?: Record<string, string>
): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'content-type': 'application/json',
      ...extraHeaders,
    },
  });
}

const listPlansHandler: RouteHandler = async (request, env) => {
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  try {
    const headers: Record<string, string> = {};
    const inm = request.headers.get('if-none-match');
    if (inm) headers['if-none-match'] = inm;
    const result = await listPlans(env, headers);
    if (result.status === 304) {
      return new Response(null, {
        status: 304,
        headers: result.etag ? { etag: `"${result.etag}"` } : {},
      });
    }
    if (result.status >= 400) {
      return errorResponse('NETWORK', 'plan catalogue unavailable', requestId);
    }
    return json(result.body, 200, result.etag ? { etag: `"${result.etag}"` } : undefined);
  } catch (e) {
    return errorResponse(
      'NETWORK',
      e instanceof Error ? e.message : String(e),
      requestId
    );
  }
};

const planStatusHandler: RouteHandler = async (request, env, _ctx, params) => {
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  const planId = params.id ?? '';
  try {
    const headers: Record<string, string> = {};
    const inm = request.headers.get('if-none-match');
    if (inm) headers['if-none-match'] = inm;
    const result = await getPlanRecord(env, planId, headers);
    if (result.status === 304) {
      return new Response(null, {
        status: 304,
        headers: result.etag ? { etag: `"${result.etag}"` } : {},
      });
    }
    if (result.status === 404 || !result.body) {
      // Empty documented shape when plan unknown — still 200 with draft defaults
      // so clients can bootstrap; alternatively 404. Contract shows success shape;
      // unknown plan returns 404 NETWORK-style message.
      return errorResponse('NETWORK', `plan not found: ${planId}`, requestId);
    }
    const halted = result.body.halted === true || (await isHalted(env));
    const body = {
      id: result.body.id,
      status: result.body.status,
      halted,
      openBranches: result.body.openBranches ?? [],
      blockedTasks: result.body.blockedTasks ?? [],
      currentMilestone: result.body.currentMilestone,
    };
    return json(body, 200, result.etag ? { etag: `"${result.etag}"` } : undefined);
  } catch (e) {
    return errorResponse(
      'NETWORK',
      e instanceof Error ? e.message : String(e),
      requestId
    );
  }
};

const validateHandler: RouteHandler = async (request, env, _ctx, params) => {
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  const planId = params.id ?? '';
  try {
    const result = await getPlanRecord(env, planId);
    const errors: string[] = [];
    const warnings: string[] = [];
    if (result.status === 404 || !result.body) {
      errors.push(`plan ${planId} not found in catalogue`);
      return json({ valid: false, planId, errors, warnings });
    }
    const p = result.body;
    if (!p.title || !p.title.trim()) errors.push('title is empty');
    if (!p.status) errors.push('status is missing');
    if ((p.taskCount ?? 0) < 0) errors.push('taskCount negative');
    if ((p.completedTaskCount ?? 0) > (p.taskCount ?? 0)) {
      errors.push('completedTaskCount exceeds taskCount');
    }
    for (const t of p.tasks ?? []) {
      if (!t.id) errors.push('task missing id');
      if (t.branch && !/^task-[A-Za-z0-9._-]+$/.test(t.branch)) {
        warnings.push(`task ${t.id} has non-standard branch ${t.branch}`);
      }
    }
    return json({
      valid: errors.length === 0,
      planId,
      errors,
      warnings,
    });
  } catch (e) {
    return errorResponse(
      'NETWORK',
      e instanceof Error ? e.message : String(e),
      requestId
    );
  }
};

const renderCheckHandler: RouteHandler = async (request, env, _ctx, params) => {
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  const planId = params.id ?? '';
  try {
    const result = await getPlanRecord(env, planId);
    const renderIssues: string[] = [];
    if (result.status === 404 || !result.body) {
      renderIssues.push(`plan ${planId} not found`);
      return json({ ok: false, planId, renderIssues });
    }
    if (!result.body.title) renderIssues.push('missing title for render');
    return json({
      ok: renderIssues.length === 0,
      planId,
      renderIssues,
    });
  } catch (e) {
    return errorResponse(
      'NETWORK',
      e instanceof Error ? e.message : String(e),
      requestId
    );
  }
};

/** Internal/bootstrap write used by tests and operators via status CAS path later. */
const upsertPlanHandler: RouteHandler = async (request, env, _ctx, params) => {
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  const planId = params.id ?? '';
  let body: Record<string, unknown>;
  try {
    body = (await request.json()) as Record<string, unknown>;
  } catch {
    return errorResponse('NETWORK', 'invalid JSON body', requestId);
  }

  const idem = await gateIdempotencyWrite(
    request,
    env,
    'PLAN_UPSERT',
    body as unknown as Record<string, unknown>,
    requestId
  );
  if (!idem.ok) return idem.response;
  if (idem.replay) return idem.replay;
  const _idemKey = idem.key;
  const _idemCred = idem.credentialHash;

  const ifMatch = request.headers.get('if-match')?.replace(/"/g, '');
  const result = await putPlanRecord(env, planId, body as never, ifMatch);
  if (result.status === 409) {
    return await finishFromResponse(env, _idemCred, _idemKey, errorResponse('NETWORK', 'CAS conflict on plan etag', requestId));
  }
  if (!result.body) {
    return await finishFromResponse(env, _idemCred, _idemKey, errorResponse('NETWORK', 'failed to upsert plan', requestId));
  }
  return await finishFromResponse(env, _idemCred, _idemKey, json(result.body, result.status, {
    etag: `"${result.body.etag}"`,
  }));
};

/**
 * POST /plans/:id/status — contract §3.17 (revision 1.3).
 *
 * The console sends SET_IN_PROGRESS here before it publishes. The operation
 * changes the status of **one task** in the gateway's plan record; it does
 * **not** commit to the consumer repository, so the publish-grade guard set is
 * not run (there is no diff to vet) — this route performs the contract's own
 * ordered checks instead.
 *
 * Check order (contract §3.17):
 *   1. idempotency record          (gateIdempotencyWrite)
 *   2. body                        (BAD_ENCODING)
 *   3. capability                  (gateCapability)
 *   4. operation                   (UNKNOWN_OP / BAD_ENCODING for reserved ops)
 *   5. branch scope                (BRANCH_MISMATCH)
 *   6. live branch head            (BRANCH_MISSING / HEAD_MOVED)
 *   7. halt                        (HALT_ACTIVE)
 *   8. plan record + CAS write     (BAD_ENCODING / RECONCILE_REQUIRED)
 *   9. audit + response
 *
 * Every refusal after step 1 closes the operation record, so GET /operations/{id}
 * reports it.
 */

/** The request keys are exactly these (plan-status-transition.schema.json). */
const PLAN_STATUS_REQUEST_KEYS = [
  'plan',
  'task',
  'branch',
  'expectedHead',
  'targetStatus',
  'idempotencyKey',
  'capability',
] as const;

/** 40 lower-case hex characters (plan-status-transition.schema.json). */
const HEAD_SHA_RE = /^[0-9a-f]{40}$/;

/** The only transition supported in contract revision 1.3. */
const SUPPORTED_TARGET_STATUS = 'in_progress';

interface PlanStatusTransitionBody {
  plan: string;
  task: string;
  branch: string;
  expectedHead: string;
  targetStatus: string;
  idempotencyKey: string;
  capability: string;
}

const planStatusTransitionHandler: RouteHandler = async (
  request,
  env,
  _ctx,
  params
) => {
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  const planId = params.id ?? '';

  let raw: Record<string, unknown>;
  try {
    raw = (await request.json()) as Record<string, unknown>;
  } catch {
    return errorResponse('BAD_ENCODING', 'invalid JSON body', requestId);
  }

  // ---- 1. idempotency record -------------------------------------------------
  const idem = await gateIdempotencyWrite(
    request,
    env,
    'SET_IN_PROGRESS',
    raw,
    requestId
  );
  if (!idem.ok) return idem.response;
  if (idem.replay) return idem.replay;
  const _idemKey = idem.key;
  const _idemCred = idem.credentialHash;

  /** Every later refusal closes the operation record. */
  const refuse = (code: ErrorCode, message: string): Promise<Response> =>
    finishFromResponse(
      env,
      _idemCred,
      _idemKey,
      errorResponse(code, message, requestId)
    );

  // ---- 2. body ---------------------------------------------------------------
  const unknownKeys = Object.keys(raw).filter(
    (k) => !(PLAN_STATUS_REQUEST_KEYS as readonly string[]).includes(k)
  );
  if (unknownKeys.length > 0) {
    return await refuse(
      'BAD_ENCODING',
      `unknown request key: ${unknownKeys.join(', ')}`
    );
  }
  const body = raw as unknown as PlanStatusTransitionBody;
  for (const key of PLAN_STATUS_REQUEST_KEYS) {
    const value = body[key];
    if (typeof value !== 'string' || value.length === 0) {
      return await refuse('BAD_ENCODING', `missing or invalid ${key}`);
    }
  }
  if (body.targetStatus !== SUPPORTED_TARGET_STATUS) {
    return await refuse(
      'BAD_ENCODING',
      `targetStatus must be ${SUPPORTED_TARGET_STATUS} (got ${body.targetStatus})`
    );
  }
  if (!HEAD_SHA_RE.test(body.expectedHead)) {
    return await refuse(
      'BAD_ENCODING',
      'expectedHead must be 40 lower-case hex characters'
    );
  }
  if (planId && body.plan !== planId) {
    return await refuse(
      'BAD_ENCODING',
      `path plan ${planId} does not match body plan ${body.plan}`
    );
  }

  // ---- 3. capability ---------------------------------------------------------
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
    return await refuse(gate.code, gate.message);
  }
  const cap = gate.capability;

  // ---- 4. operation ----------------------------------------------------------
  // SET and SET_DONE are reserved in revision 1.3 (contract §3.17); every other
  // operation is not the one this route performs.
  if (cap.op === 'set' || cap.op === 'set_done') {
    return await refuse(
      'BAD_ENCODING',
      `capability op ${cap.op.toUpperCase()} is reserved in contract revision 1.3`
    );
  }
  if (cap.op !== 'set_in_progress') {
    return await refuse(
      'UNKNOWN_OP',
      `capability op must be set_in_progress (got ${cap.op})`
    );
  }
  // The path id, the body plan and the capability plan/task must name one
  // operation. The contract does not name this case — recorded in the report.
  if (cap.plan !== body.plan) {
    return await refuse(
      'BAD_ENCODING',
      `capability plan ${cap.plan} does not match body plan ${body.plan}`
    );
  }
  if (cap.task !== body.task) {
    return await refuse(
      'BAD_ENCODING',
      `capability task ${cap.task} does not match body task ${body.task}`
    );
  }

  // ---- 5. branch scope -------------------------------------------------------
  if (cap.branch !== body.branch) {
    return await refuse(
      'BRANCH_MISMATCH',
      'capability branch does not match request branch'
    );
  }
  const record = await getPlanRecord(env, body.plan);
  const planRecord = record.body;
  const taskRecord = planRecord?.tasks?.find((t) => t.id === body.task);
  if (taskRecord?.branch && taskRecord.branch !== cap.branch) {
    return await refuse(
      'BRANCH_MISMATCH',
      `task ${body.task} is recorded on branch ${taskRecord.branch}`
    );
  }

  // ---- 6. live branch head ---------------------------------------------------
  let consumer;
  try {
    const adapter = await getAdapter(env);
    consumer = adapter.consumer();
  } catch (e) {
    return await refuse(
      'NETWORK',
      e instanceof Error ? e.message : 'adapter unavailable'
    );
  }
  let liveHead = '';
  try {
    const ref = await consumer.getRef(body.branch);
    liveHead = ref.sha.toLowerCase();
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    if (msg.includes('404') || msg.includes('Not Found')) {
      return await refuse(
        'BRANCH_MISSING',
        `branch ${body.branch} does not exist on the consumer repository`
      );
    }
    return await refuse('NETWORK', msg);
  }
  if (cap.head !== liveHead || body.expectedHead !== liveHead) {
    return await refuse(
      'HEAD_MOVED',
      `head moved: capability ${cap.head}, expected ${body.expectedHead}, live ${liveHead}`
    );
  }

  // ---- 7. halt ---------------------------------------------------------------
  if (await isHalted(env)) {
    return await refuse(
      'HALT_ACTIVE',
      'global engine halt is active; write operations are blocked'
    );
  }

  // ---- 8. plan record + compare-and-swap ------------------------------------
  if (!planRecord) {
    return await refuse('BAD_ENCODING', `plan not found: ${body.plan}`);
  }
  if (!taskRecord) {
    return await refuse('BAD_ENCODING', `task not found: ${body.task}`);
  }
  const tasks = (planRecord.tasks ?? []).map((t) =>
    t.id === body.task ? { ...t, status: 'in_progress' as const } : t
  );
  if (!planRecord.etag) {
    // Fail closed: without a revision to compare against there is no
    // compare-and-swap, and the write would not be race-safe.
    return await refuse(
      'RECONCILE_REQUIRED',
      'the plan record carries no revision to compare against'
    );
  }
  const put = await putPlanRecord(
    env,
    body.plan,
    { tasks } as Partial<PlanRecord>,
    planRecord.etag
  );
  if (put.status === 409) {
    return await refuse(
      'RECONCILE_REQUIRED',
      'the plan record changed before the write; reconcile and retry'
    );
  }
  if (!put.body) {
    return await refuse('NETWORK', 'failed to update the plan record');
  }

  // ---- 9. audit + response ---------------------------------------------------
  const audit = emitAudit({
    requestId,
    op: 'set_in_progress',
    outcome: 'accepted',
    branch: body.branch,
    head: liveHead,
    plan: body.plan,
    task: body.task,
    detail: { newStatus: SUPPORTED_TARGET_STATUS },
  });

  return await finishFromResponse(
    env,
    _idemCred,
    _idemKey,
    json(
      {
        accepted: true,
        operation: 'SET_IN_PROGRESS',
        plan: body.plan,
        task: body.task,
        newStatus: SUPPORTED_TARGET_STATUS,
        headSha: liveHead,
        auditRef: audit.auditRef,
      },
      200
    ),
    {
      branch: body.branch,
      headSha: liveHead,
      auditRef: audit.auditRef,
    }
  );
};

export function registerPlanRoutes(): void {
  register('GET', '/plans', listPlansHandler, {
    requiresOperatorToken: true,
  });
  register('GET', '/plans/:id/status', planStatusHandler, {
    requiresOperatorToken: true,
  });
  register('POST', '/plans/:id/status', planStatusTransitionHandler, {
    requiresOperatorToken: true,
    requiresCapability: true,
  });
  register('POST', '/plans/:id/validate', validateHandler, {
    requiresOperatorToken: true,
  });
  register('GET', '/plans/:id/render-check', renderCheckHandler, {
    requiresOperatorToken: true,
  });
  // Bootstrap / CAS upsert (operator token). Not a privileged capability write.
  register('PUT', '/plans/:id', upsertPlanHandler, {
    requiresOperatorToken: true,
  });
}
