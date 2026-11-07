import { describe, it, expect, beforeEach } from 'vitest';
import {
  register,
  dispatch,
  clearRoutes,
} from '../../../src/routes/router';
import { registerHealthRoutes } from '../../../src/routes/health';
import type { Env } from '../../../src/index';

const ctx = {} as ExecutionContext;

function env(over: Record<string, string> = {}): Env {
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

beforeEach(() => {
  clearRoutes();
  registerHealthRoutes();
  register(
    'GET',
    '/status',
    async () =>
      new Response(JSON.stringify({ ok: true }), {
        headers: { 'content-type': 'application/json' },
      }),
    { requiresOperatorToken: true }
  );
});

describe('config gate', () => {
  it('GET /health succeeds with ENVIRONMENT=staging and reports environment', async () => {
    const res = await dispatch(
      new Request('https://gw.test/health'),
      env({ ENVIRONMENT: 'staging' }),
      ctx
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      status: string;
      environment: string | null;
      config?: { valid: boolean; problems: string[] };
    };
    expect(body.status).toBe('ok');
    expect(body.environment).toBe('staging');
    expect(body.config).toBeUndefined();
  });

  it('GET /health reports all three valid environments', async () => {
    for (const name of ['test', 'staging', 'production'] as const) {
      const res = await dispatch(
        new Request('https://gw.test/health'),
        env({ ENVIRONMENT: name }),
        ctx
      );
      expect(res.status).toBe(200);
      const body = (await res.json()) as {
        status: string;
        environment: string | null;
      };
      expect(body.status).toBe('ok');
      expect(body.environment).toBe(name);
    }
  });

  it('GET /health succeeds with invalid ENVIRONMENT and reports problems', async () => {
    const res = await dispatch(
      new Request('https://gw.test/health'),
      env({ ENVIRONMENT: 'lab' }),
      ctx
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      status: string;
      environment: string | null;
      config: { valid: boolean; problems: string[] };
    };
    expect(body.status).toBe('degraded');
    expect(body.environment).toBeNull();
    expect(body.config.valid).toBe(false);
    expect(body.config.problems).toContain('ENVIRONMENT_INVALID');
  });

  it('non-health route returns 503 CONFIG_INVALID when config bad', async () => {
    const res = await dispatch(
      new Request('https://gw.test/status', {
        headers: { authorization: 'Bearer tok' },
      }),
      env({ ENVIRONMENT: 'lab' }),
      ctx
    );
    expect(res.status).toBe(503);
    const body = (await res.json()) as { error: string };
    expect(body.error).toBe('CONFIG_INVALID');
  });

  it('non-health route proceeds when config valid (including staging)', async () => {
    for (const name of ['test', 'staging', 'production'] as const) {
      const res = await dispatch(
        new Request('https://gw.test/status', {
          headers: { authorization: 'Bearer tok' },
        }),
        env({ ENVIRONMENT: name }),
        ctx
      );
      expect(res.status).toBe(200);
    }
  });
});
