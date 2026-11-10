import { describe, it, expect, beforeEach } from 'vitest';
import { AuditStream } from '../../../src/durable-objects/audit-stream';
import type { AuditRecord } from '../../../src/audit/emit';

class MemoryStorage {
  private map = new Map<string, unknown>();
  async get<T>(key: string): Promise<T | undefined> {
    return this.map.get(key) as T | undefined;
  }
  async put(key: string, value: unknown): Promise<void> {
    this.map.set(key, value);
  }
  async list<T>(opts?: { prefix?: string }): Promise<Map<string, T>> {
    const out = new Map<string, T>();
    for (const [k, v] of this.map) {
      if (!opts?.prefix || k.startsWith(opts.prefix)) out.set(k, v as T);
    }
    return out;
  }
}

function makeStream(): AuditStream {
  return new AuditStream({
    storage: new MemoryStorage(),
  } as unknown as DurableObjectState);
}

function sample(ref: string): AuditRecord {
  return {
    auditRef: ref,
    requestId: 'r1',
    op: 'publish',
    outcome: 'accepted',
    at: 1_700_000_000,
    branch: 'task-1',
    head: 'a'.repeat(40),
  };
}

describe('AuditStream', () => {
  let stream: AuditStream;
  beforeEach(() => {
    stream = makeStream();
  });

  it('appends and resolves by ref', async () => {
    const rec = sample('audit-1');
    const app = await stream.fetch(
      new Request('https://audit/append', {
        method: 'POST',
        body: JSON.stringify(rec),
      })
    );
    expect(app.status).toBe(200);
    const get = await stream.fetch(
      new Request('https://audit/ref/audit-1')
    );
    expect(get.status).toBe(200);
    const body = (await get.json()) as { record: AuditRecord };
    expect(body.record.op).toBe('publish');
  });

  it('lists in reverse insertion order', async () => {
    await stream.fetch(
      new Request('https://audit/append', {
        method: 'POST',
        body: JSON.stringify(sample('audit-a')),
      })
    );
    await stream.fetch(
      new Request('https://audit/append', {
        method: 'POST',
        body: JSON.stringify(sample('audit-b')),
      })
    );
    const list = await stream.fetch(new Request('https://audit/list?limit=10'));
    const body = (await list.json()) as { records: AuditRecord[] };
    expect(body.records.map((r) => r.auditRef)).toEqual([
      'audit-b',
      'audit-a',
    ]);
  });
});
