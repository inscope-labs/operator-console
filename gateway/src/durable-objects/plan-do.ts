/**
 * PlanDO — single-writer plan state store (Stage 4.1).
 * Spec §12.2 / §12.3: one DO instance per plan id (or aggregate catalogue).
 *
 * Routes (internal fetch only):
 *   GET  /catalogue
 *   GET  /plans/:id
 *   PUT  /plans/:id          body: PlanRecord (CAS via If-Match etag)
 *   GET  /tasks/next
 */

export type PlanStatusValue =
  | 'draft'
  | 'ready'
  | 'in_progress'
  | 'blocked'
  | 'done'
  | 'archived';

export interface PlanTaskRecord {
  id: string;
  title: string;
  branch?: string;
  status: 'ready' | 'in_progress' | 'blocked' | 'done' | 'unassigned';
  headSha?: string;
}

export interface PlanRecord {
  id: string;
  title: string;
  status: PlanStatusValue;
  taskCount: number;
  completedTaskCount: number;
  updatedAt: number;
  halted?: boolean;
  openBranches?: string[];
  blockedTasks?: string[];
  currentMilestone?: string;
  tasks?: PlanTaskRecord[];
  /** Opaque revision for ETag / CAS. */
  etag: string;
}

const CATALOGUE_KEY = 'catalogue:ids';
const planKey = (id: string) => `plan:${id}`;

function json(data: unknown, status = 200, etag?: string): Response {
  const headers: Record<string, string> = {
    'content-type': 'application/json',
  };
  if (etag) {
    headers['etag'] = `"${etag}"`;
    headers['cache-control'] = 'private, must-revalidate';
  }
  return new Response(JSON.stringify(data), { status, headers });
}

function newEtag(): string {
  return crypto.randomUUID().replace(/-/g, '').slice(0, 16);
}

export class PlanDO implements DurableObject {
  constructor(private readonly state: DurableObjectState) {}

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    const path = url.pathname;

    if (request.method === 'GET' && path === '/catalogue') {
      return this.listCatalogue(request);
    }
    if (request.method === 'GET' && path === '/tasks/next') {
      return this.nextTasks();
    }
    const planMatch = path.match(/^\/plans\/([^/]+)$/);
    if (planMatch) {
      const id = decodeURIComponent(planMatch[1]);
      if (request.method === 'GET') return this.getPlan(id, request);
      if (request.method === 'PUT') return this.putPlan(id, request);
    }
    return json(
      { error: 'NETWORK', message: `PlanDO: no route ${request.method} ${path}` },
      404
    );
  }

  private async listCatalogue(request: Request): Promise<Response> {
    const ids =
      (await this.state.storage.get<string[]>(CATALOGUE_KEY)) ?? [];
    const plans: PlanRecord[] = [];
    for (const id of ids) {
      const rec = await this.state.storage.get<PlanRecord>(planKey(id));
      if (rec) plans.push(rec);
    }
    const body = plans.map((p) => ({
      id: p.id,
      title: p.title,
      status: p.status,
      taskCount: p.taskCount,
      completedTaskCount: p.completedTaskCount,
      updatedAt: p.updatedAt,
    }));
    // Conditional: If-None-Match on aggregate uses content fingerprint.
    const fingerprint = await contentFingerprint(body);
    const inm = request.headers.get('if-none-match');
    if (inm && inm.replace(/"/g, '') === fingerprint) {
      return new Response(null, {
        status: 304,
        headers: { etag: `"${fingerprint}"` },
      });
    }
    return json(body, 200, fingerprint);
  }

  private async getPlan(id: string, request: Request): Promise<Response> {
    const rec = await this.state.storage.get<PlanRecord>(planKey(id));
    if (!rec) {
      return json({ error: 'NETWORK', message: `plan not found: ${id}` }, 404);
    }
    const inm = request.headers.get('if-none-match');
    if (inm && inm.replace(/"/g, '') === rec.etag) {
      return new Response(null, {
        status: 304,
        headers: { etag: `"${rec.etag}"` },
      });
    }
    return json(rec, 200, rec.etag);
  }

  private async putPlan(id: string, request: Request): Promise<Response> {
    let body: Partial<PlanRecord>;
    try {
      body = (await request.json()) as Partial<PlanRecord>;
    } catch {
      return json({ error: 'NETWORK', message: 'invalid JSON' }, 400);
    }
    const existing = await this.state.storage.get<PlanRecord>(planKey(id));
    const ifMatch = request.headers.get('if-match');
    if (existing && ifMatch) {
      const expected = ifMatch.replace(/"/g, '');
      if (expected !== existing.etag) {
        return json(
          {
            error: 'NETWORK',
            message: 'CAS conflict: If-Match does not match current etag',
            etag: existing.etag,
          },
          409
        );
      }
    }
    const now = Math.floor(Date.now() / 1000);
    const tasks = body.tasks ?? existing?.tasks ?? [];
    const completedTaskCount = tasks.filter((t) => t.status === 'done').length;
    const rec: PlanRecord = {
      id,
      title: body.title ?? existing?.title ?? id,
      status: (body.status as PlanStatusValue) ?? existing?.status ?? 'draft',
      taskCount: body.taskCount ?? tasks.length,
      completedTaskCount:
        body.completedTaskCount ?? completedTaskCount,
      updatedAt: now,
      halted: body.halted ?? existing?.halted ?? false,
      openBranches: body.openBranches ?? existing?.openBranches ?? [],
      blockedTasks: body.blockedTasks ?? existing?.blockedTasks ?? [],
      currentMilestone:
        body.currentMilestone ?? existing?.currentMilestone,
      tasks,
      etag: newEtag(),
    };
    await this.state.storage.put(planKey(id), rec);
    const ids =
      (await this.state.storage.get<string[]>(CATALOGUE_KEY)) ?? [];
    if (!ids.includes(id)) {
      ids.push(id);
      await this.state.storage.put(CATALOGUE_KEY, ids);
    }
    return json(rec, existing ? 200 : 201, rec.etag);
  }

  private async nextTasks(): Promise<Response> {
    const ids =
      (await this.state.storage.get<string[]>(CATALOGUE_KEY)) ?? [];
    const out: Array<{
      id: string;
      planId: string;
      title: string;
      branch?: string;
      status: string;
      headSha?: string;
    }> = [];
    for (const id of ids) {
      const rec = await this.state.storage.get<PlanRecord>(planKey(id));
      if (!rec?.tasks) continue;
      for (const t of rec.tasks) {
        if (t.status === 'ready' || t.status === 'unassigned') {
          out.push({
            id: t.id,
            planId: rec.id,
            title: t.title,
            branch: t.branch,
            status: t.status === 'unassigned' ? 'ready' : t.status,
            headSha: t.headSha,
          });
        }
      }
    }
    return json(out, 200);
  }
}

async function contentFingerprint(body: unknown): Promise<string> {
  const enc = new TextEncoder().encode(JSON.stringify(body));
  const dig = await crypto.subtle.digest('SHA-256', enc);
  return Array.from(new Uint8Array(dig))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
    .slice(0, 16);
}
