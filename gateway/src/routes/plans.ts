/**
 * Plan list / status / validate / render-check — Stage 4.1.
 * Spec §3.2–3.5.
 */

import { register, type RouteHandler } from './router';
import { errorResponse } from '../errors/respond';
import {
  listPlans,
  getPlanRecord,
  putPlanRecord,
} from '../plan/client';
import { isHalted } from '../halt/state';

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
  const ifMatch = request.headers.get('if-match')?.replace(/"/g, '');
  const result = await putPlanRecord(env, planId, body as never, ifMatch);
  if (result.status === 409) {
    return errorResponse('NETWORK', 'CAS conflict on plan etag', requestId);
  }
  if (!result.body) {
    return errorResponse('NETWORK', 'failed to upsert plan', requestId);
  }
  return json(result.body, result.status, {
    etag: `"${result.body.etag}"`,
  });
};

export function registerPlanRoutes(): void {
  register('GET', '/plans', listPlansHandler, {
    requiresOperatorToken: true,
  });
  register('GET', '/plans/:id/status', planStatusHandler, {
    requiresOperatorToken: true,
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
