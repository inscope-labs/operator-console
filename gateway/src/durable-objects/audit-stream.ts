/**
 * AuditStream Durable Object — Stage 5.1.
 * Ordered append log; retrieval by auditRef and recent list.
 */

import type { AuditRecord } from '../audit/emit';

const SEQ_KEY = 'meta:seq';
const INDEX_PREFIX = 'ref:';
const ORDER_PREFIX = 'ord:';

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

export class AuditStream implements DurableObject {
  constructor(private readonly state: DurableObjectState) {}

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    const path = url.pathname;

    if (request.method === 'POST' && path === '/append') {
      return this.append(request);
    }
    if (request.method === 'GET' && path.startsWith('/ref/')) {
      const ref = decodeURIComponent(path.slice('/ref/'.length));
      return this.getByRef(ref);
    }
    if (request.method === 'GET' && path === '/list') {
      const limit = Number(url.searchParams.get('limit') ?? '50');
      return this.list(Math.min(Math.max(limit, 1), 200));
    }
    return json(
      { error: 'NETWORK', message: `AuditStream: no route ${request.method} ${path}` },
      404
    );
  }

  private async append(request: Request): Promise<Response> {
    let record: AuditRecord;
    try {
      record = (await request.json()) as AuditRecord;
    } catch {
      return json({ error: 'NETWORK', message: 'invalid JSON' }, 400);
    }
    if (!record?.auditRef) {
      return json({ error: 'NETWORK', message: 'auditRef required' }, 400);
    }
    const seq = ((await this.state.storage.get<number>(SEQ_KEY)) ?? 0) + 1;
    await this.state.storage.put(SEQ_KEY, seq);
    await this.state.storage.put(`${INDEX_PREFIX}${record.auditRef}`, record);
    await this.state.storage.put(
      `${ORDER_PREFIX}${String(seq).padStart(12, '0')}`,
      record.auditRef
    );
    return json({ ok: true, auditRef: record.auditRef, seq });
  }

  private async getByRef(ref: string): Promise<Response> {
    const rec = await this.state.storage.get<AuditRecord>(`${INDEX_PREFIX}${ref}`);
    if (!rec) {
      return json({ error: 'NETWORK', message: 'audit ref not found' }, 404);
    }
    return json({ ok: true, record: rec });
  }

  private async list(limit: number): Promise<Response> {
    const ordered = await this.state.storage.list<string>({
      prefix: ORDER_PREFIX,
    });
    const refs = Array.from(ordered.values()).reverse().slice(0, limit);
    const records: AuditRecord[] = [];
    for (const ref of refs) {
      const rec = await this.state.storage.get<AuditRecord>(`${INDEX_PREFIX}${ref}`);
      if (rec) records.push(rec);
    }
    return json({ ok: true, records });
  }
}
