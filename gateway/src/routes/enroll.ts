/**
 * Enrolment routes — contract 3.15 / 3.16 + addendum (challenge + authTier).
 * POST /enroll/challenge, POST /enroll, POST /enroll/rotate
 */

import type { Env } from '../index';
import { register, type RouteHandler } from './router';
import { errorResponse } from '../errors/respond';
import { parseGatewayConfig } from '../config/env';
import { now as clockNow } from '../util/clock';
import { verifyAttestation } from '../attestation/verify';
import type { AuthTier } from '../config/constants';
import {
  createChallenge,
  consumeChallenge,
  enrollDevice,
  rotateDevice,
} from '../enrolment/registry-client';

const json = (body: unknown, status = 200): Response =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });

function b64urlChallenge(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  let s = '';
  for (let i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function decodeChallengeBytes(challenge: string): Uint8Array {
  const b64 = challenge.replace(/-/g, '+').replace(/_/g, '/');
  const pad = b64.length % 4 === 0 ? '' : '='.repeat(4 - (b64.length % 4));
  const bin = atob(b64 + pad);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function isAuthTier(v: unknown): v is AuthTier {
  return v === 'hardware_confirm' || v === 'in_app_confirm';
}

const postChallenge: RouteHandler = async (_request, env) => {
  const challenge = b64urlChallenge();
  const t = clockNow();
  try {
    const { expiresAt } = await createChallenge(env, challenge, t);
    return json({ challenge, expiresAt });
  } catch (e) {
    return errorResponse(
      'NETWORK',
      e instanceof Error ? e.message : 'challenge create failed'
    );
  }
};

const postEnroll: RouteHandler = async (request, env) => {
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  let body: {
    alias?: string;
    publicKey?: string;
    attestationChain?: string[];
    challenge?: string;
    authTier?: string;
  };
  try {
    body = (await request.json()) as typeof body;
  } catch {
    return errorResponse('NETWORK', 'invalid JSON body', requestId);
  }

  if (!body.alias || !body.publicKey || !body.attestationChain || !body.challenge) {
    return errorResponse(
      'NETWORK',
      'alias, publicKey, attestationChain, challenge, authTier required',
      requestId
    );
  }
  if (!isAuthTier(body.authTier)) {
    return errorResponse(
      'ENROLL_ATTESTATION_INVALID',
      'authTier must be hardware_confirm or in_app_confirm',
      requestId
    );
  }

  const cfg = parseGatewayConfig(env as unknown as Record<string, unknown>);
  if (!cfg.ok) {
    return errorResponse('CONFIG_INVALID', 'Gateway configuration is invalid', requestId);
  }

  const t = clockNow();
  const consumed = await consumeChallenge(env, body.challenge, t);
  if (!consumed.ok) {
    return errorResponse(
      consumed.code === 'ENROLL_CHALLENGE_INVALID'
        ? 'ENROLL_CHALLENGE_INVALID'
        : 'NETWORK',
      consumed.message,
      requestId
    );
  }

  const challengeBytes = decodeChallengeBytes(body.challenge);
  const att = await verifyAttestation({
    chain: body.attestationChain,
    challenge: challengeBytes,
    publicKey: body.publicKey,
    claimedAuthTier: body.authTier,
    mode: cfg.config.attestationMode,
    trustedRootSha256: cfg.config.attestationRootSha256,
    allowedAuthTiers: cfg.config.allowedAuthTiers,
    now: t,
  });

  if (!att.ok) {
    return errorResponse(att.code, att.message, requestId);
  }

  try {
    const result = await enrollDevice(env, {
      alias: body.alias,
      publicKey: body.publicKey,
      authTier: att.authTier,
      leafCertSha256: att.leafCertSha256,
      now: t,
    });
    return json({
      kid: result.kid,
      status: 'active',
      enrolledAt: t,
      authTier: result.authTier,
    });
  } catch (e) {
    return errorResponse(
      'NETWORK',
      e instanceof Error ? e.message : 'enroll failed',
      requestId
    );
  }
};

const postRotate: RouteHandler = async (request, env) => {
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  let body: {
    oldKid?: string;
    newPublicKey?: string;
    publicKey?: string;
    attestationChain?: string[];
    challenge?: string;
    authTier?: string;
  };
  try {
    body = (await request.json()) as typeof body;
  } catch {
    return errorResponse('NETWORK', 'invalid JSON body', requestId);
  }

  const publicKey = body.newPublicKey ?? body.publicKey;
  if (!body.oldKid || !publicKey || !body.attestationChain || !body.challenge) {
    return errorResponse(
      'NETWORK',
      'oldKid, newPublicKey, attestationChain, challenge, authTier required',
      requestId
    );
  }
  if (!isAuthTier(body.authTier)) {
    return errorResponse(
      'ENROLL_ATTESTATION_INVALID',
      'authTier must be hardware_confirm or in_app_confirm',
      requestId
    );
  }

  const cfg = parseGatewayConfig(env as unknown as Record<string, unknown>);
  if (!cfg.ok) {
    return errorResponse('CONFIG_INVALID', 'Gateway configuration is invalid', requestId);
  }

  const t = clockNow();
  const consumed = await consumeChallenge(env, body.challenge, t);
  if (!consumed.ok) {
    return errorResponse(
      consumed.code === 'ENROLL_CHALLENGE_INVALID'
        ? 'ENROLL_CHALLENGE_INVALID'
        : 'NETWORK',
      consumed.message,
      requestId
    );
  }

  const challengeBytes = decodeChallengeBytes(body.challenge);
  const att = await verifyAttestation({
    chain: body.attestationChain,
    challenge: challengeBytes,
    publicKey,
    claimedAuthTier: body.authTier,
    mode: cfg.config.attestationMode,
    trustedRootSha256: cfg.config.attestationRootSha256,
    allowedAuthTiers: cfg.config.allowedAuthTiers,
    now: t,
  });

  if (!att.ok) {
    return errorResponse(att.code, att.message, requestId);
  }

  try {
    const result = await rotateDevice(env, {
      oldKid: body.oldKid,
      publicKey,
      authTier: att.authTier,
      leafCertSha256: att.leafCertSha256,
      now: t,
    });
    return json({
      kid: result.kid,
      status: 'active',
      rotatedAt: t,
      authTier: result.authTier,
    });
  } catch (e) {
    return errorResponse(
      'NETWORK',
      e instanceof Error ? e.message : 'rotate failed',
      requestId
    );
  }
};

export function registerEnrollRoutes(): void {
  const auth = { requiresOperatorToken: true, requiresCapability: false };
  register('POST', '/enroll/challenge', postChallenge, auth);
  register('POST', '/enroll', postEnroll, auth);
  register('POST', '/enroll/rotate', postRotate, auth);
}
