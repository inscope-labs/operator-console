/**
 * Client for OperationStore DO (+ memory fallback for unit tests).
 */

import type { Env } from '../runtime';
import type { OperationRecord, OperationStatus } from './types';
import { PENDING_TIMEOUT_SECONDS } from './types';

/** Process-local map: credentialHash -> id -> record */
const memory = new Map<string, Map<string, OperationRecord>>();

function memBucket(hash: string): Map<string, OperationRecord> {
  let b = memory.get(hash);
  if (!b) {
    b = new Map();
    memory.set(hash, b);
  }
  return b;
}

export function resetOperationMemory(): void {
  memory.clear();
}

async function doFetch(
  env: Env,
  credentialHash: string,
  path: string,
  init?: RequestInit
): Promise<Response> {
  if (env.OPERATION_STORE) {
    const id = env.OPERATION_STORE.idFromName(`cred:${credentialHash}`);
    const stub = env.OPERATION_STORE.get(id);
    return stub.fetch(new Request(`https://operation-store${path}`, init));
  }
  // Memory fallback
  return memoryFetch(credentialHash, path, init);
}

async function memoryFetch(
  credentialHash: string,
  path: string,
  init?: RequestInit
): Promise<Response> {
  const method = (init?.method ?? 'GET').toUpperCase();
  const bucket = memBucket(credentialHash);

  if (method === 'POST' && path === '/begin') {
    const body = JSON.parse(String(init?.body ?? '{}')) as {
      id: string;
      operation: string;
      fingerprint: string;
      now?: number;
    };
    const now = body.now ?? Math.floor(Date.now() / 1000);
    const existing = bucket.get(body.id);
    if (existing) {
      if (existing.fingerprint !== body.fingerprint) {
        return new Response(JSON.stringify({ kind: 'reused', record: existing }), {
          status: 200,
        });
      }
      if (existing.status === 'pending') {
        // Match DO: stale pending is marked failed so client can recover
        if (now - existing.timestamp > PENDING_TIMEOUT_SECONDS) {
          existing.status = 'failed';
          existing.code = 'NETWORK';
          bucket.set(body.id, existing);
          return new Response(
            JSON.stringify({ kind: 'replay', record: existing }),
            { status: 200 }
          );
        }
        return new Response(
          JSON.stringify({ kind: 'pending_inflight', record: existing }),
          { status: 200 }
        );
      }
      return new Response(JSON.stringify({ kind: 'replay', record: existing }), {
        status: 200,
      });
    }
    const record: OperationRecord = {
      id: body.id,
      credentialHash,
      operation: body.operation,
      fingerprint: body.fingerprint,
      status: 'pending',
      timestamp: now,
    };
    bucket.set(body.id, record);
    return new Response(JSON.stringify({ kind: 'created', record }), {
      status: 200,
    });
  }

  if (method === 'POST' && path === '/complete') {
    const body = JSON.parse(String(init?.body ?? '{}')) as {
      id: string;
      status: OperationStatus;
      code?: string;
      auditRef?: string;
      branch?: string;
      headSha?: string;
      responseStatus?: number;
      responseBody?: string;
      now?: number;
    };
    const existing = bucket.get(body.id);
    if (!existing) {
      return new Response(JSON.stringify({ error: 'OPERATION_NOT_FOUND' }), {
        status: 404,
      });
    }
    existing.status = body.status;
    if (body.code !== undefined) existing.code = body.code;
    if (body.auditRef !== undefined) existing.auditRef = body.auditRef;
    if (body.branch !== undefined) existing.branch = body.branch;
    if (body.headSha !== undefined) existing.headSha = body.headSha;
    if (body.responseStatus !== undefined)
      existing.responseStatus = body.responseStatus;
    if (body.responseBody !== undefined) existing.responseBody = body.responseBody;
    if (body.now !== undefined) existing.timestamp = body.now;
    bucket.set(body.id, existing);
    return new Response(JSON.stringify({ ok: true, record: existing }), {
      status: 200,
    });
  }

  if (method === 'GET' && path.startsWith('/get/')) {
    const id = decodeURIComponent(path.slice('/get/'.length));
    const rec = bucket.get(id);
    if (!rec) {
      return new Response(JSON.stringify({ error: 'OPERATION_NOT_FOUND' }), {
        status: 404,
      });
    }
    return new Response(JSON.stringify({ ok: true, record: rec }), {
      status: 200,
    });
  }

  return new Response('nope', { status: 404 });
}

export type BeginKind =
  | 'created'
  | 'replay'
  | 'reused'
  | 'pending_inflight'
  | 'not_visible';

export async function beginOperation(
  env: Env,
  opts: {
    credentialHash: string;
    id: string;
    operation: string;
    fingerprint: string;
    now?: number;
  }
): Promise<{ kind: BeginKind; record?: OperationRecord }> {
  const res = await doFetch(env, opts.credentialHash, '/begin', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      credentialHash: opts.credentialHash,
      id: opts.id,
      operation: opts.operation,
      fingerprint: opts.fingerprint,
      now: opts.now,
    }),
  });
  if (res.status === 404) {
    return { kind: 'not_visible' };
  }
  const body = (await res.json()) as {
    kind: BeginKind;
    record?: OperationRecord;
  };
  return body;
}

export async function completeOperation(
  env: Env,
  opts: {
    credentialHash: string;
    id: string;
    status: OperationStatus;
    code?: string;
    auditRef?: string;
    branch?: string;
    headSha?: string;
    responseStatus?: number;
    responseBody?: string;
    now?: number;
  }
): Promise<OperationRecord | null> {
  const res = await doFetch(env, opts.credentialHash, '/complete', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(opts),
  });
  if (!res.ok) return null;
  const body = (await res.json()) as { record: OperationRecord };
  return body.record;
}

export async function getOperation(
  env: Env,
  credentialHash: string,
  id: string
): Promise<OperationRecord | null> {
  const res = await doFetch(
    env,
    credentialHash,
    `/get/${encodeURIComponent(id)}`
  );
  if (!res.ok) return null;
  const body = (await res.json()) as { record: OperationRecord };
  return body.record;
}
