import { describe, it, expect, beforeEach } from 'vitest';
import { clearRoutes, dispatch } from '../../../src/routes/router';
import { registerOperationRoutes } from '../../../src/routes/operations';
import { registerTaskRoutes } from '../../../src/routes/tasks';
import {
  resetOperationMemory,
  beginOperation,
  completeOperation,
} from '../../../src/idempotency/store-client';
import { hashOperatorCredential } from '../../../src/idempotency/credential';
import type { Env } from '../../../src/runtime';

const KEY = '3b1f0c7a9e5d4a28b6c1d0e9f8a7b6c5';

function baseEnv(): Env {
  return {
    ENVIRONMENT: 'test',
    OPERATOR_TOKEN: 'tok',
    SIGNING_KEYS: '[]',
    REPOS: '{}',
    GITHUB_APP_ID: '1',
    GITHUB_APP_PRIVATE_KEY: 'k',
    GITHUB_APP_INSTALLATION_ID: '1',
  } as unknown as Env;
}

const ctx = {
  waitUntil: () => undefined,
  passThroughOnException: () => undefined,
} as unknown as ExecutionContext;

beforeEach(() => {
  clearRoutes();
  resetOperationMemory();
  registerOperationRoutes();
  registerTaskRoutes();
});

describe('GET /operations/:id', () => {
  it('401 without bearer', async () => {
    const res = await dispatch(
      new Request(`https://gw.test/operations/${KEY}`),
      baseEnv(),
      ctx
    );
    expect(res.status).toBe(401);
  });

  it('400 for malformed id', async () => {
    const res = await dispatch(
      new Request('https://gw.test/operations/not-hex', {
        headers: { authorization: 'Bearer tok' },
      }),
      baseEnv(),
      ctx
    );
    expect(res.status).toBe(400);
    const b = (await res.json()) as { error: string };
    expect(b.error).toBe('IDEMPOTENCY_KEY_INVALID');
  });

  it('404 for unknown key', async () => {
    const res = await dispatch(
      new Request(`https://gw.test/operations/${KEY}`, {
        headers: { authorization: 'Bearer tok' },
      }),
      baseEnv(),
      ctx
    );
    expect(res.status).toBe(404);
  });

  it('404 while pending; 200 completed', async () => {
    const e = baseEnv();
    const hash = await hashOperatorCredential('tok');
    await beginOperation(e, {
      credentialHash: hash,
      id: KEY,
      operation: 'PUBLISH',
      fingerprint: 'fp',
    });
    let res = await dispatch(
      new Request(`https://gw.test/operations/${KEY}`, {
        headers: { authorization: 'Bearer tok' },
      }),
      e,
      ctx
    );
    expect(res.status).toBe(404);

    await completeOperation(e, {
      credentialHash: hash,
      id: KEY,
      status: 'completed',
      auditRef: 'audit-1',
      headSha: 'c'.repeat(40),
      branch: 'task-1',
      responseStatus: 200,
      responseBody: '{}',
    });
    res = await dispatch(
      new Request(`https://gw.test/operations/${KEY}`, {
        headers: { authorization: 'Bearer tok' },
      }),
      e,
      ctx
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      status: string;
      auditRef: string;
      headSha: string;
    };
    expect(body.status).toBe('completed');
    expect(body.auditRef).toBe('audit-1');
    expect(body.headSha).toHaveLength(40);
  });

  it('refused carries code', async () => {
    const e = baseEnv();
    const hash = await hashOperatorCredential('tok');
    await beginOperation(e, {
      credentialHash: hash,
      id: KEY,
      operation: 'PUBLISH',
      fingerprint: 'fp',
    });
    await completeOperation(e, {
      credentialHash: hash,
      id: KEY,
      status: 'refused',
      code: 'HALT_ACTIVE',
    });
    const res = await dispatch(
      new Request(`https://gw.test/operations/${KEY}`, {
        headers: { authorization: 'Bearer tok' },
      }),
      e,
      ctx
    );
    const body = (await res.json()) as { status: string; code: string };
    expect(body.status).toBe('refused');
    expect(body.code).toBe('HALT_ACTIVE');
  });
});

describe('write endpoints require idempotencyKey', () => {
  it('publish without key → 400', async () => {
    const res = await dispatch(
      new Request('https://gw.test/tasks/7/publish', {
        method: 'POST',
        headers: {
          authorization: 'Bearer tok',
          'content-type': 'application/json',
        },
        body: JSON.stringify({ capability: 'x' }),
      }),
      baseEnv(),
      ctx
    );
    expect(res.status).toBe(400);
    const b = (await res.json()) as { error: string };
    expect(b.error).toBe('IDEMPOTENCY_KEY_INVALID');
  });
});
