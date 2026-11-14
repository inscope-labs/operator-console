import { describe, it, expect, beforeEach } from 'vitest';
import {
  gateIdempotencyWrite,
  finishFromResponse,
} from '../../../src/idempotency/write-gate';
import { resetOperationMemory, getOperation } from '../../../src/idempotency/store-client';
import { hashOperatorCredential } from '../../../src/idempotency/credential';
import type { Env } from '../../../src/runtime';

const KEY = '3b1f0c7a9e5d4a28b6c1d0e9f8a7b6c5';
const KEY2 = 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa';

function env(): Env {
  return { ENVIRONMENT: 'test', OPERATOR_TOKEN: 'tok' } as unknown as Env;
}

function req(body: unknown): Request {
  return new Request('https://gw.test/tasks/1/publish', {
    method: 'POST',
    headers: {
      authorization: 'Bearer tok',
      'content-type': 'application/json',
    },
    body: JSON.stringify(body),
  });
}

beforeEach(() => {
  resetOperationMemory();
});

describe('gateIdempotencyWrite', () => {
  it('rejects missing key', async () => {
    const r = await gateIdempotencyWrite(
      req({ capability: 'x' }),
      env(),
      'PUBLISH',
      { capability: 'x' },
      'rid'
    );
    expect(r.ok).toBe(false);
    if (!r.ok) {
      expect(r.response.status).toBe(400);
      const b = (await r.response.json()) as { error: string };
      expect(b.error).toBe('IDEMPOTENCY_KEY_INVALID');
    }
  });

  it('creates pending before capability would run', async () => {
    const r = await gateIdempotencyWrite(
      req({ idempotencyKey: KEY, branch: 'task-1' }),
      env(),
      'PUBLISH',
      { idempotencyKey: KEY, branch: 'task-1' },
      'rid'
    );
    expect(r.ok).toBe(true);
    if (!r.ok) return;
    const hash = await hashOperatorCredential('tok');
    const rec = await getOperation(env(), hash, KEY);
    expect(rec?.status).toBe('pending');
  });

  it('same key different fingerprint → REUSED', async () => {
    const e = env();
    const first = await gateIdempotencyWrite(
      req({ idempotencyKey: KEY, branch: 'task-1' }),
      e,
      'PUBLISH',
      { idempotencyKey: KEY, branch: 'task-1' },
      'rid'
    );
    expect(first.ok).toBe(true);
    if (!first.ok) return;
    await finishFromResponse(
      e,
      first.credentialHash,
      first.key,
      new Response(
        JSON.stringify({
          accepted: true,
          auditRef: 'audit-x',
          headSha: 'a'.repeat(40),
          branch: 'task-1',
        }),
        { status: 200 }
      )
    );
    const second = await gateIdempotencyWrite(
      req({ idempotencyKey: KEY, branch: 'task-OTHER' }),
      e,
      'PUBLISH',
      { idempotencyKey: KEY, branch: 'task-OTHER' },
      'rid2'
    );
    expect(second.ok).toBe(false);
    if (!second.ok) {
      expect(second.response.status).toBe(409);
      const b = (await second.response.json()) as { error: string };
      expect(b.error).toBe('IDEMPOTENCY_KEY_REUSED');
    }
  });

  it('same key same fingerprint replays recorded response', async () => {
    const e = env();
    const first = await gateIdempotencyWrite(
      req({ idempotencyKey: KEY2, branch: 'task-1' }),
      e,
      'PUBLISH',
      { idempotencyKey: KEY2, branch: 'task-1' },
      'rid'
    );
    expect(first.ok).toBe(true);
    if (!first.ok) return;
    await finishFromResponse(
      e,
      first.credentialHash,
      first.key,
      new Response(
        JSON.stringify({
          accepted: true,
          operation: 'PUBLISH',
          auditRef: 'audit-y',
          headSha: 'b'.repeat(40),
          branch: 'task-1',
        }),
        { status: 200 }
      )
    );
    const second = await gateIdempotencyWrite(
      req({ idempotencyKey: KEY2, branch: 'task-1' }),
      e,
      'PUBLISH',
      { idempotencyKey: KEY2, branch: 'task-1' },
      'rid2'
    );
    expect(second.ok).toBe(true);
    if (!second.ok) return;
    expect(second.replay).toBeDefined();
    expect(second.replay!.status).toBe(200);
    const body = (await second.replay!.json()) as { auditRef: string };
    expect(body.auditRef).toBe('audit-y');
  });

  it('records refused outcome with code', async () => {
    const e = env();
    const first = await gateIdempotencyWrite(
      req({ idempotencyKey: KEY, branch: 'task-1' }),
      e,
      'PUBLISH',
      { idempotencyKey: KEY, branch: 'task-1' },
      'rid'
    );
    expect(first.ok).toBe(true);
    if (!first.ok) return;
    await finishFromResponse(
      e,
      first.credentialHash,
      first.key,
      new Response(
        JSON.stringify({ error: 'HEAD_MOVED', message: 'drift', request_id: 'r' }),
        { status: 409 }
      )
    );
    const rec = await getOperation(e, first.credentialHash, KEY);
    expect(rec?.status).toBe('refused');
    expect(rec?.code).toBe('HEAD_MOVED');
  });

  it('rejects uppercase key', async () => {
    const r = await gateIdempotencyWrite(
      req({ idempotencyKey: 'A'.repeat(32), capability: 'x' }),
      env(),
      'PUBLISH',
      { idempotencyKey: 'A'.repeat(32), capability: 'x' },
      'rid'
    );
    expect(r.ok).toBe(false);
    if (!r.ok) {
      expect(r.response.status).toBe(400);
      const b = (await r.response.json()) as { error: string };
      expect(b.error).toBe('IDEMPOTENCY_KEY_INVALID');
    }
  });

  it('rejects 31-char and 33-char keys', async () => {
    for (const bad of ['a'.repeat(31), 'a'.repeat(33)]) {
      const r = await gateIdempotencyWrite(
        req({ idempotencyKey: bad }),
        env(),
        'PUBLISH',
        { idempotencyKey: bad },
        'rid'
      );
      expect(r.ok).toBe(false);
      if (!r.ok) {
        expect(r.response.status).toBe(400);
        const b = (await r.response.json()) as { error: string };
        expect(b.error).toBe('IDEMPOTENCY_KEY_INVALID');
      }
    }
  });

  it('records failed with NOT_IMPLEMENTED when that code is returned', async () => {
    const e = env();
    const first = await gateIdempotencyWrite(
      req({ idempotencyKey: KEY, branch: 'task-1' }),
      e,
      'PUBLISH',
      { idempotencyKey: KEY, branch: 'task-1' },
      'rid'
    );
    expect(first.ok).toBe(true);
    if (!first.ok) return;
    await finishFromResponse(
      e,
      first.credentialHash,
      first.key,
      new Response(
        JSON.stringify({ error: 'NOT_IMPLEMENTED', message: 'stub', request_id: 'r' }),
        { status: 501 }
      )
    );
    const rec = await getOperation(e, first.credentialHash, KEY);
    expect(rec?.status).toBe('failed');
    expect(rec?.code).toBe('NOT_IMPLEMENTED');
  });

  it('pending timeout marks record failed with NETWORK', async () => {
    const e = env();
    const past = Math.floor(Date.now() / 1000) - (16 * 60);
    const { beginOperation, getOperation: getOp } = await import(
      '../../../src/idempotency/store-client'
    );
    const { computeFingerprint } = await import(
      '../../../src/idempotency/fingerprint'
    );
    const hash = await hashOperatorCredential('tok');
    const body = { idempotencyKey: KEY, branch: 'task-1' };
    const fp = await computeFingerprint('PUBLISH', body);
    await beginOperation(e, {
      credentialHash: hash,
      id: KEY,
      operation: 'PUBLISH',
      fingerprint: fp,
      now: past,
    });
    const second = await gateIdempotencyWrite(
      req(body),
      e,
      'PUBLISH',
      body,
      'rid2'
    );
    // Same fingerprint after timeout → replay of the now-failed record
    expect(second.ok).toBe(true);
    if (second.ok) {
      expect(second.replay).toBeDefined();
    }
    const rec = await getOp(e, hash, KEY);
    expect(rec).toBeTruthy();
    expect(rec!.status).toBe('failed');
    expect(rec!.code).toBe('NETWORK');
  });
});
