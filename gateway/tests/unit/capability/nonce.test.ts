import { describe, it, expect } from 'vitest';
import { checkAndStoreNonce } from '../../../src/capability/nonce';
import { CAPABILITY_VERSION, type OperatorCapability } from '../../../src/capability/types';
import type { Env } from '../../../src/index';

const capability: OperatorCapability = {
  version: CAPABILITY_VERSION,
  kid: 'k1',
  exp: Math.floor(Date.now() / 1000) + 120,
  order: 'o',
  plan: 'p',
  task: '7',
  op: 'publish',
  branch: 'task-7',
  head: 'a'.repeat(40),
  nonce: 'nonce-unique-1',
};

function mockEnv(handler: (req: Request) => Promise<Response> | Response): Env {
  const stub = {
    fetch: (req: Request | string) =>
      Promise.resolve(handler(typeof req === 'string' ? new Request(req) : req)),
  };
  return {
    NONCE_STORE: {
      idFromName: () => ({ toString: () => 'id' }),
      get: () => stub,
    },
  } as unknown as Env;
}

describe('checkAndStoreNonce', () => {
  it('returns ok on 200', async () => {
    const env = mockEnv(() => new Response(JSON.stringify({ ok: true }), { status: 200 }));
    const result = await checkAndStoreNonce(env, capability);
    expect(result.ok).toBe(true);
  });

  it('returns NONCE_REPLAYED on 409', async () => {
    const env = mockEnv(
      () =>
        new Response(JSON.stringify({ error: 'NONCE_REPLAYED', message: 'used' }), {
          status: 409,
        })
    );
    const result = await checkAndStoreNonce(env, capability);
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.code).toBe('NONCE_REPLAYED');
  });

  it('fails closed when binding missing', async () => {
    const result = await checkAndStoreNonce({} as Env, capability);
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.code).toBe('NETWORK');
  });
});
