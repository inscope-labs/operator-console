/**
 * PlanDO client + memory fallback for tests (Stage 4.1).
 */

import type { Env } from '../runtime';
import type { PlanRecord, PlanTaskRecord } from '../durable-objects/plan-do';

export type { PlanRecord, PlanTaskRecord };

/** Process-local store when PLAN_DO is unbound. */
const memory = new Map<string, PlanRecord>();

function newEtag(): string {
  return crypto.randomUUID().replace(/-/g, '').slice(0, 16);
}

function memoryCatalogue(): PlanRecord[] {
  return Array.from(memory.values());
}

function getStub(env: Env): DurableObjectStub {
  if (env.PLAN_DO) {
    return env.PLAN_DO.get(env.PLAN_DO.idFromName('cbridge-plan-catalogue'));
  }
  return {
    fetch: async (input: RequestInfo | URL, init?: RequestInit) => {
      const req =
        typeof input === 'string' || input instanceof URL
          ? new Request(input, init)
          : input;
      const url = new URL(req.url);
      const path = url.pathname;

      if (req.method === 'GET' && path === '/catalogue') {
        const body = memoryCatalogue().map((p) => ({
          id: p.id,
          title: p.title,
          status: p.status,
          taskCount: p.taskCount,
          completedTaskCount: p.completedTaskCount,
          updatedAt: p.updatedAt,
        }));
        const etag = newEtag();
        const inm = req.headers.get('if-none-match');
        if (inm && inm.replace(/"/g, '') === etag) {
          return new Response(null, { status: 304, headers: { etag: `"${etag}"` } });
        }
        return new Response(JSON.stringify(body), {
          status: 200,
          headers: {
            'content-type': 'application/json',
            etag: `"${etag}"`,
          },
        });
      }
      if (req.method === 'GET' && path === '/tasks/next') {
        const out: unknown[] = [];
        for (const p of memoryCatalogue()) {
          for (const t of p.tasks ?? []) {
            if (t.status === 'ready' || t.status === 'unassigned') {
              out.push({
                id: t.id,
                planId: p.id,
                title: t.title,
                branch: t.branch,
                status: t.status === 'unassigned' ? 'ready' : t.status,
                headSha: t.headSha,
              });
            }
          }
        }
        return new Response(JSON.stringify(out), {
          status: 200,
          headers: { 'content-type': 'application/json' },
        });
      }
      const m = path.match(/^\/plans\/([^/]+)$/);
      if (m) {
        const id = decodeURIComponent(m[1]);
        if (req.method === 'GET') {
          const rec = memory.get(id);
          if (!rec) {
            return new Response(JSON.stringify({ error: 'not found' }), {
              status: 404,
            });
          }
          const inm = req.headers.get('if-none-match');
          if (inm && inm.replace(/"/g, '') === rec.etag) {
            return new Response(null, {
              status: 304,
              headers: { etag: `"${rec.etag}"` },
            });
          }
          return new Response(JSON.stringify(rec), {
            status: 200,
            headers: {
              'content-type': 'application/json',
              etag: `"${rec.etag}"`,
            },
          });
        }
        if (req.method === 'PUT') {
          const body = (await req.json()) as Partial<PlanRecord>;
          const existing = memory.get(id);
          const ifMatch = req.headers.get('if-match');
          if (existing && ifMatch) {
            if (ifMatch.replace(/"/g, '') !== existing.etag) {
              return new Response(
                JSON.stringify({ error: 'CAS conflict', etag: existing.etag }),
                { status: 409 }
              );
            }
          }
          const now = Math.floor(Date.now() / 1000);
          const tasks = body.tasks ?? existing?.tasks ?? [];
          const rec: PlanRecord = {
            id,
            title: body.title ?? existing?.title ?? id,
            status: (body.status as PlanRecord['status']) ?? existing?.status ?? 'draft',
            taskCount: body.taskCount ?? tasks.length,
            completedTaskCount:
              body.completedTaskCount ??
              tasks.filter((t) => t.status === 'done').length,
            updatedAt: now,
            halted: body.halted ?? existing?.halted ?? false,
            openBranches: body.openBranches ?? existing?.openBranches ?? [],
            blockedTasks: body.blockedTasks ?? existing?.blockedTasks ?? [],
            currentMilestone:
              body.currentMilestone ?? existing?.currentMilestone,
            tasks,
            etag: newEtag(),
          };
          memory.set(id, rec);
          return new Response(JSON.stringify(rec), {
            status: existing ? 200 : 201,
            headers: {
              'content-type': 'application/json',
              etag: `"${rec.etag}"`,
            },
          });
        }
      }
      return new Response('nope', { status: 404 });
    },
  } as unknown as DurableObjectStub;
}

export async function listPlans(
  env: Env,
  headers?: HeadersInit
): Promise<{ status: number; body: unknown; etag?: string }> {
  const stub = getStub(env);
  const res = await stub.fetch('https://plan/catalogue', { headers });
  const etag = res.headers.get('etag')?.replace(/"/g, '') ?? undefined;
  if (res.status === 304) return { status: 304, body: null, etag };
  const body = await res.json();
  return { status: res.status, body, etag };
}

export async function getPlanRecord(
  env: Env,
  id: string,
  headers?: HeadersInit
): Promise<{ status: number; body: PlanRecord | null; etag?: string }> {
  const stub = getStub(env);
  const res = await stub.fetch(
    `https://plan/plans/${encodeURIComponent(id)}`,
    { headers }
  );
  const etag = res.headers.get('etag')?.replace(/"/g, '') ?? undefined;
  if (res.status === 304) return { status: 304, body: null, etag };
  if (res.status === 404) return { status: 404, body: null };
  const body = (await res.json()) as PlanRecord;
  return { status: res.status, body, etag };
}

export async function putPlanRecord(
  env: Env,
  id: string,
  record: Partial<PlanRecord>,
  ifMatch?: string
): Promise<{ status: number; body: PlanRecord | null }> {
  const stub = getStub(env);
  const headers: Record<string, string> = {
    'content-type': 'application/json',
  };
  if (ifMatch) headers['if-match'] = `"${ifMatch}"`;
  const res = await stub.fetch(
    `https://plan/plans/${encodeURIComponent(id)}`,
    {
      method: 'PUT',
      headers,
      body: JSON.stringify(record),
    }
  );
  if (!res.ok) {
    return { status: res.status, body: null };
  }
  const body = (await res.json()) as PlanRecord;
  return { status: res.status, body };
}

export async function nextTasks(env: Env): Promise<unknown[]> {
  const stub = getStub(env);
  const res = await stub.fetch('https://plan/tasks/next');
  if (!res.ok) return [];
  return (await res.json()) as unknown[];
}

/** Test helper: clear memory catalogue. */
export function resetPlanMemory(): void {
  memory.clear();
}
