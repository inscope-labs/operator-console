/**
 * Stage 1.1 — `POST /tasks/:id/publish` is a fail-closed stub.
 *
 * The route used to answer `200 {ok:true}` once the capability and the core
 * guards passed, with a message that still admitted the transaction had not
 * happened. A client that trusted the status code reported a publication that
 * never occurred. The route must now answer `501 NOT_IMPLEMENTED`, never 2xx.
 *
 * These tests also pin the properties that make the refusal safe:
 *   - the operator-token and configuration gates still run before the stub;
 *   - the stub consumes no capability nonce and touches no GitHub state.
 */

import { describe, it, expect, beforeEach } from 'vitest';
import {
  dispatch,
  clearRoutes,
  listRoutes,
} from '../../../src/routes/router';
import { registerTaskRoutes } from '../../../src/routes/tasks';
import type { Env } from '../../../src/index';

const ctx = {} as ExecutionContext;

interface NonceSpy {
  namespace: DurableObjectNamespace;
  calls: string[];
}

/** A nonce store that records every access and fails loudly if it is reached. */
function nonceSpy(): NonceSpy {
  const calls: string[] = [];
  const namespace = {
    idFromName(name: string): unknown {
      calls.push(`idFromName:${name}`);
      throw new Error('the publish stub must not reach the nonce store');
    },
    get(): unknown {
      calls.push('get');
      throw new Error('the publish stub must not reach the nonce store');
    },
  } as unknown as DurableObjectNamespace;
  return { namespace, calls };
}

/**
 * The GitHub App credentials are deliberately empty: if the stub ever tried to
 * build an adapter it would fail with NETWORK instead of the 501 under test.
 */
function envWith(over: Record<string, unknown> = {}): Env {
  return {
    ENVIRONMENT: 'test',
    OPERATOR_TOKEN: 'tok',
    SIGNING_KEYS: '[]',
    REPOS: '{}',
    GITHUB_APP_ID: '',
    GITHUB_APP_PRIVATE_KEY: '',
    GITHUB_APP_INSTALLATION_ID: '',
    ...over,
  } as unknown as Env;
}

function publishRequest(body: unknown, token = 'tok'): Request {
  return new Request('https://gw.test/tasks/10008/publish', {
    method: 'POST',
    headers: {
      authorization: `Bearer ${token}`,
      'content-type': 'application/json',
    },
    body: JSON.stringify(body),
  });
}

beforeEach(() => {
  clearRoutes();
  registerTaskRoutes();
});

describe('publish stub — Stage 1.1', () => {
  it('answers 501 NOT_IMPLEMENTED and never 2xx', async () => {
    const res = await dispatch(
      publishRequest({ capability: 'ctxop-v1\nk2\n1761840000' }),
      envWith(),
      ctx
    );

    expect(res.status).toBe(501);
    expect(res.ok).toBe(false);

    const body = (await res.json()) as {
      error: string;
      message: string;
      request_id: string;
    };
    expect(body.error).toBe('NOT_IMPLEMENTED');
    expect(body.message.length).toBeGreaterThan(0);
    expect(body.request_id).toBeTruthy();
  });

  it('never claims success in the response body', async () => {
    const res = await dispatch(
      publishRequest({ capability: 'ctxop-v1' }),
      envWith(),
      ctx
    );
    const raw = await res.text();

    expect(raw).not.toMatch(/"ok"\s*:\s*true/i);
    expect(raw).not.toMatch(/"accepted"\s*:\s*true/i);
    expect(raw).not.toMatch(/accepted/i);
    expect(raw).not.toMatch(/"auditRef"/i);

    const body = JSON.parse(raw) as Record<string, unknown>;
    expect(body['ok']).toBeUndefined();
    expect(body['accepted']).toBeUndefined();
    expect(body['auditRef']).toBeUndefined();
    expect(body['operation']).toBeUndefined();
  });

  it('answers 501 even when the body is missing or malformed', async () => {
    const res = await dispatch(
      new Request('https://gw.test/tasks/10008/publish', {
        method: 'POST',
        headers: { authorization: 'Bearer tok' },
      }),
      envWith(),
      ctx
    );

    expect(res.status).toBe(501);
    expect(res.ok).toBe(false);
  });

  it('does not consume a capability nonce', async () => {
    const spy = nonceSpy();
    const res = await dispatch(
      publishRequest({ capability: 'ctxop-v1' }),
      envWith({ NONCE_STORE: spy.namespace }),
      ctx
    );

    expect(res.status).toBe(501);
    expect(spy.calls).toEqual([]);
  });

  it('still requires the operator token', async () => {
    const res = await dispatch(
      publishRequest({ capability: 'ctxop-v1' }, 'wrong-token'),
      envWith(),
      ctx
    );

    expect(res.status).toBe(401);
    const body = (await res.json()) as { error: string };
    expect(body.error).toBe('OPERATOR_AUTH');
  });

  it('still fails closed on invalid configuration', async () => {
    const res = await dispatch(
      publishRequest({ capability: 'ctxop-v1' }),
      envWith({ ENVIRONMENT: 'not-an-environment' }),
      ctx
    );

    expect(res.status).toBe(503);
    const body = (await res.json()) as { error: string };
    expect(body.error).toBe('CONFIG_INVALID');
  });

  it('is registered as an operator-token-protected privileged route', () => {
    const route = listRoutes().find((r) => r.pattern.includes('publish'));
    expect(route).toBeDefined();
    expect(route?.requiresOperatorToken).toBe(true);
    expect(route?.requiresCapability).toBe(true);
  });
});
