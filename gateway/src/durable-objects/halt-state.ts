/**
 * Global engine halt marker — Stage 3.5 / G14 backing store.
 * Single Durable Object instance id "cbridge-halt-state".
 */

interface HaltRecord {
  active: boolean;
  reason?: string;
  at?: number;
  requestId?: string;
}

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

export class HaltState implements DurableObject {
  constructor(private readonly state: DurableObjectState) {}

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    const path = url.pathname;

    if (request.method === 'GET' && path === '/status') {
      const rec = (await this.state.storage.get<HaltRecord>('halt')) ?? {
        active: false,
      };
      return json({ ok: true, ...rec });
    }

    if (request.method === 'POST' && path === '/halt') {
      let body: { reason?: string; requestId?: string; now?: number } = {};
      try {
        body = (await request.json()) as typeof body;
      } catch {
        /* optional body */
      }
      const now = body.now ?? Math.floor(Date.now() / 1000);
      const rec: HaltRecord = {
        active: true,
        reason: body.reason,
        at: now,
        requestId: body.requestId,
      };
      await this.state.storage.put('halt', rec);
      return json({ ok: true, ...rec });
    }

    if (request.method === 'POST' && path === '/resume') {
      let body: { reason?: string; requestId?: string; now?: number } = {};
      try {
        body = (await request.json()) as typeof body;
      } catch {
        /* optional */
      }
      const now = body.now ?? Math.floor(Date.now() / 1000);
      const rec: HaltRecord = {
        active: false,
        reason: body.reason,
        at: now,
        requestId: body.requestId,
      };
      await this.state.storage.put('halt', rec);
      return json({ ok: true, ...rec });
    }

    return json({ error: 'NETWORK', message: `no route ${request.method} ${path}` }, 404);
  }
}
