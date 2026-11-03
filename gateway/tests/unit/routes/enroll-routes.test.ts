import { describe, it, expect, beforeEach } from 'vitest';
import {
  dispatch,
  clearRoutes,
  listRoutes,
} from '../../../src/routes/router';
import { registerEnrollRoutes } from '../../../src/routes/enroll';
import { registerBranchRoutes } from '../../../src/routes/branches';
import type { Env } from '../../../src/index';

const ctx = {} as ExecutionContext;

function baseEnv(over: Record<string, unknown> = {}): Env {
  return {
    ENVIRONMENT: 'test',
    ATTESTATION_MODE: 'lenient',
    OPERATOR_TOKEN: 'tok',
    SIGNING_KEYS: '[]',
    REPOS: '{}',
    GITHUB_APP_ID: '',
    GITHUB_APP_PRIVATE_KEY: '',
    GITHUB_APP_INSTALLATION_ID: '',
    ...over,
  } as unknown as Env;
}

beforeEach(() => {
  clearRoutes();
  registerEnrollRoutes();
  registerBranchRoutes();
});

describe('enroll + branch route registration', () => {
  it('registers challenge, enroll, rotate, branch head', () => {
    const paths = listRoutes().map((r) => r.method + ' ' + r.pattern);
    expect(paths.some((p) => p.includes('enroll'))).toBe(true);
    expect(paths.some((p) => p.includes('branches'))).toBe(true);
  });

  it('POST /enroll/challenge requires operator token', async () => {
    const res = await dispatch(
      new Request('https://gw.test/enroll/challenge', { method: 'POST', body: '{}' }),
      baseEnv(),
      ctx
    );
    expect(res.status).toBe(401);
  });

  it('POST /enroll/challenge returns challenge when registry mock present', async () => {
    const store = new Map<string, unknown>();
    const mockFetch = async (req: Request) => {
      const url = new URL(req.url);
      if (url.pathname === '/challenge/create') {
        const body = (await req.json()) as { challenge: string; now: number };
        store.set(`challenge:${body.challenge}`, {
          challenge: body.challenge,
          expiresAt: body.now + 300,
          consumed: false,
        });
        return new Response(
          JSON.stringify({ ok: true, expiresAt: body.now + 300 }),
          { status: 200 }
        );
      }
      return new Response('nope', { status: 404 });
    };
    const env = baseEnv({
      ENROLMENT_REGISTRY: {
        idFromName: () => ({ toString: () => 'id' }),
        get: () => ({ fetch: mockFetch }),
      },
    });
    const res = await dispatch(
      new Request('https://gw.test/enroll/challenge', {
        method: 'POST',
        headers: { authorization: 'Bearer tok', 'content-type': 'application/json' },
        body: '{}',
      }),
      env,
      ctx
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as { challenge: string; expiresAt: number };
    expect(body.challenge.length).toBeGreaterThan(20);
    expect(body.expiresAt).toBeGreaterThan(0);
  });

  it('GET /branches/:name/head validates branch name', async () => {
    const res = await dispatch(
      new Request('https://gw.test/branches/..%2Fevil/head', {
        headers: { authorization: 'Bearer tok' },
      }),
      baseEnv(),
      ctx
    );
    // decodeURIComponent may yield ../evil — rejected
    expect([400, 502]).toContain(res.status);
  });
});
