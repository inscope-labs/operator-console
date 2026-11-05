import { describe, it, expect } from 'vitest';
import {
  resolveDeviceKey,
  createResolveKey,
} from '../../../src/auth/device';
import type { Env } from '../../../src/index';
import {
  encodeCapability,
  // codec only
} from '../../../src/capability/codec';
import { verifyCapability } from '../../../src/capability/verify';
import {
  CAPABILITY_VERSION,
  type OperatorCapability,
} from '../../../src/capability/types';
import { bytesToBase64Url } from '../../../src/capability/verify';

function envWith(over: Record<string, unknown>): Env {
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

describe('resolveDeviceKey — SIGNING_KEYS HMAC', () => {
  it('resolves lab HMAC kid to raw secret buffer', async () => {
    const secret = 'lab-secret-key-32-chars-minimum!!';
    const env = envWith({
      SIGNING_KEYS: JSON.stringify([
        { kid: 'lab-hmac', algorithm: 'HMAC', secret, encoding: 'utf8' },
      ]),
    });
    const resolved = await resolveDeviceKey(env, 'lab-hmac');
    expect(resolved).not.toBeNull();
    expect(resolved!.algorithm).toBe('HMAC');
    expect(resolved!.key).toBeInstanceOf(ArrayBuffer);
  });

  it('returns null for unknown kid', async () => {
    const env = envWith({ SIGNING_KEYS: '[]' });
    // no registry binding → null
    const resolved = await resolveDeviceKey(env, 'k99');
    expect(resolved).toBeNull();
  });
});

describe('resolveDeviceKey — enrolment registry', () => {
  it('imports active ECDSA public key from registry', async () => {
    const pair = (await crypto.subtle.generateKey(
      { name: 'ECDSA', namedCurve: 'P-256' },
      true,
      ['sign', 'verify']
    )) as CryptoKeyPair;
    const spki = new Uint8Array(
      (await crypto.subtle.exportKey('spki', pair.publicKey)) as ArrayBuffer
    );
    let b64 = btoa(String.fromCharCode(...spki));
    b64 = b64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

    const mockFetch = async (req: Request) => {
      const url = new URL(req.url);
      if (url.pathname === '/lookup/k1') {
        return new Response(
          JSON.stringify({
            ok: true,
            record: {
              kid: 'k1',
              publicKey: b64,
              authTier: 'hardware_confirm',
              status: 'active',
            },
          }),
          { status: 200 }
        );
      }
      if (url.pathname === '/lookup/k2') {
        return new Response(
          JSON.stringify({
            ok: true,
            record: {
              kid: 'k2',
              publicKey: b64,
              authTier: 'hardware_confirm',
              status: 'retired',
            },
          }),
          { status: 200 }
        );
      }
      return new Response('{}', { status: 404 });
    };

    const env = envWith({
      ENROLMENT_REGISTRY: {
        idFromName: () => ({ toString: () => 'id' }),
        get: () => ({ fetch: mockFetch }),
      },
    });

    const active = await resolveDeviceKey(env, 'k1');
    expect(active).not.toBeNull();
    expect(active!.algorithm).toBe('ECDSA');
    expect(active!.key).toBeTruthy();

    const retired = await resolveDeviceKey(env, 'k2');
    expect(retired).toBeNull();
  });
});

describe('registry kid end-to-end capability verify', () => {
  it('verifies ECDSA-signed capability with registry-resolved key', async () => {
    const pair = (await crypto.subtle.generateKey(
      { name: 'ECDSA', namedCurve: 'P-256' },
      true,
      ['sign', 'verify']
    )) as CryptoKeyPair;
    const spki = new Uint8Array(
      (await crypto.subtle.exportKey('spki', pair.publicKey)) as ArrayBuffer
    );
    let b64 = btoa(String.fromCharCode(...spki));
    b64 = b64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

    const mockFetch = async (req: Request) => {
      if (new URL(req.url).pathname === '/lookup/k7') {
        return new Response(
          JSON.stringify({
            ok: true,
            record: {
              kid: 'k7',
              publicKey: b64,
              authTier: 'hardware_confirm',
              status: 'active',
            },
          }),
          { status: 200 }
        );
      }
      return new Response('{}', { status: 404 });
    };

    const env = envWith({
      ENROLMENT_REGISTRY: {
        idFromName: () => ({ toString: () => 'id' }),
        get: () => ({ fetch: mockFetch }),
      },
    });

    const now = 1_700_000_000;
    const cap: OperatorCapability = {
      version: CAPABILITY_VERSION,
      kid: 'k7',
      exp: now + 120,
      order: 'ord-1',
      plan: 'plan-a',
      task: '7',
      op: 'publish',
      branch: 'task-7',
      head: 'a'.repeat(40),
      nonce: 'n-reg-1',
    };
    const message = encodeCapability(cap);
    const sigBuf = await crypto.subtle.sign(
      { name: 'ECDSA', hash: 'SHA-256' },
      pair.privateKey,
      new TextEncoder().encode(message)
    );
    // raw P1363 from WebCrypto
    const signature = bytesToBase64Url(new Uint8Array(sigBuf));
    const raw = message + '\n' + signature;

    const result = await verifyCapability(raw, {
      now,
      resolveKey: createResolveKey(env),
    });
    expect(result.ok).toBe(true);
  });
});
