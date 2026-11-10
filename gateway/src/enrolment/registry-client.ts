/**
 * Thin client for the EnrolmentRegistry Durable Object (internal fetch routes).
 */

import type { Env } from '../index';
import type { AuthTier } from '../config/constants';

function stub(env: Env) {
  if (!env.ENROLMENT_REGISTRY) {
    throw new Error('ENROLMENT_REGISTRY binding missing');
  }
  const id = env.ENROLMENT_REGISTRY.idFromName('cbridge-enrolment-registry');
  return env.ENROLMENT_REGISTRY.get(id);
}

async function doFetch(
  env: Env,
  path: string,
  init?: RequestInit
): Promise<Response> {
  const s = stub(env);
  return s.fetch(new Request(`https://enrolment-registry${path}`, init));
}

export async function createChallenge(
  env: Env,
  challenge: string,
  now: number
): Promise<{ expiresAt: number }> {
  const res = await doFetch(env, '/challenge/create', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ challenge, now }),
  });
  if (!res.ok) {
    throw new Error(`challenge create failed: ${res.status}`);
  }
  return (await res.json()) as { expiresAt: number };
}

export async function consumeChallenge(
  env: Env,
  challenge: string,
  now: number
): Promise<{ ok: true } | { ok: false; code: string; message: string }> {
  const res = await doFetch(env, '/challenge/consume', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ challenge, now }),
  });
  if (res.status === 400) {
    const body = (await res.json().catch(() => ({}))) as {
      error?: string;
      message?: string;
    };
    return {
      ok: false,
      code: body.error ?? 'ENROLL_CHALLENGE_INVALID',
      message: body.message ?? 'challenge invalid',
    };
  }
  if (!res.ok) {
    return { ok: false, code: 'NETWORK', message: `consume failed: ${res.status}` };
  }
  return { ok: true };
}

export async function enrollDevice(
  env: Env,
  body: {
    alias: string;
    publicKey: string;
    authTier: AuthTier;
    leafCertSha256: string;
    now: number;
  }
): Promise<{ kid: string; authTier: AuthTier }> {
  const res = await doFetch(env, '/enroll', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const t = await res.text().catch(() => '');
    throw new Error(`enroll failed: ${res.status} ${t.slice(0, 120)}`);
  }
  return (await res.json()) as { kid: string; authTier: AuthTier };
}

export async function rotateDevice(
  env: Env,
  body: {
    oldKid: string;
    alias?: string;
    publicKey: string;
    authTier: AuthTier;
    leafCertSha256: string;
    now: number;
  }
): Promise<{ kid: string; retiredKid: string; authTier: AuthTier }> {
  const res = await doFetch(env, '/rotate', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const t = await res.text().catch(() => '');
    throw new Error(`rotate failed: ${res.status} ${t.slice(0, 120)}`);
  }
  return (await res.json()) as {
    kid: string;
    retiredKid: string;
    authTier: AuthTier;
  };
}

export async function lookupKid(
  env: Env,
  kid: string
): Promise<{
  kid: string;
  publicKey: string;
  authTier: AuthTier;
  status: string;
} | null> {
  const res = await doFetch(env, `/lookup/${encodeURIComponent(kid)}`);
  if (res.status === 404) return null;
  if (!res.ok) return null;
  const body = (await res.json()) as {
    record: {
      kid: string;
      publicKey: string;
      authTier: AuthTier;
      status: string;
    };
  };
  return body.record;
}

export async function listActiveEnrolments(env: Env): Promise<
  Array<{
    kid: string;
    alias: string;
    authTier: AuthTier;
    status: string;
    enrolledAt: number;
  }>
> {
  const res = await doFetch(env, '/active');
  if (!res.ok) {
    throw new Error(`list active failed: ${res.status}`);
  }
  const body = (await res.json()) as {
    records?: Array<{
      kid: string;
      alias: string;
      authTier: AuthTier;
      status: string;
      enrolledAt: number;
      publicKey?: string;
    }>;
  };
  return (body.records ?? []).map((r) => ({
    kid: r.kid,
    alias: r.alias,
    authTier: r.authTier,
    status: r.status,
    enrolledAt: r.enrolledAt,
  }));
}

export async function revokeEnrolment(
  env: Env,
  kid: string,
  reason?: string,
  now?: number
): Promise<{ revoked: true; kid: string; revokedAt: number } | { error: string; message: string; status: number }> {
  const res = await doFetch(env, '/revoke', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ kid, reason, now }),
  });
  if (res.status === 404) {
    return { error: 'UNKNOWN_KID', message: 'enrolment not found', status: 404 };
  }
  if (!res.ok) {
    const t = await res.text().catch(() => '');
    return {
      error: 'NETWORK',
      message: `revoke failed: ${res.status} ${t.slice(0, 80)}`,
      status: res.status,
    };
  }
  const body = (await res.json()) as {
    kid: string;
    revokedAt: number;
  };
  return { revoked: true, kid: body.kid, revokedAt: body.revokedAt };
}
