/**
 * Write-ahead idempotency gate for privileged write routes (BD-1.7).
 *
 * Order: Bearer already checked by router → validate key → begin record →
 * caller runs capability/guards/execution → complete record.
 */

import type { Env } from '../runtime';
import { errorResponse } from '../errors/respond';
import { isValidIdempotencyKey } from './key';
import { computeFingerprint } from './fingerprint';
import {
  extractBearerToken,
  hashOperatorCredential,
} from './credential';
import {
  beginOperation,
  completeOperation,
} from './store-client';
import type { OperationRecord, OperationStatus } from './types';

export interface IdempotencyGateOk {
  ok: true;
  key: string;
  credentialHash: string;
  fingerprint: string;
  /** When set, return this response immediately (replay). */
  replay?: Response;
}

export interface IdempotencyGateErr {
  ok: false;
  response: Response;
}

export type IdempotencyGateResult = IdempotencyGateOk | IdempotencyGateErr;

/**
 * Validate idempotencyKey and write-ahead begin.
 * `body` must be the parsed JSON object for the write request.
 * `operation` is the contract operation name (e.g. PUBLISH).
 */
export async function gateIdempotencyWrite(
  request: Request,
  env: Env,
  operation: string,
  body: Record<string, unknown>,
  requestId: string
): Promise<IdempotencyGateResult> {
  const key = body.idempotencyKey;
  if (!isValidIdempotencyKey(key)) {
    return {
      ok: false,
      response: errorResponse(
        'IDEMPOTENCY_KEY_INVALID',
        'idempotencyKey is missing or is not 32 lowercase hex characters',
        requestId
      ),
    };
  }

  const token = extractBearerToken(request);
  if (!token) {
    return {
      ok: false,
      response: errorResponse(
        'OPERATOR_AUTH',
        'Bearer OPERATOR_TOKEN required or invalid',
        requestId
      ),
    };
  }
  const credentialHash = await hashOperatorCredential(token);
  const fingerprint = await computeFingerprint(operation, body);

  let begin;
  try {
    begin = await beginOperation(env, {
      credentialHash,
      id: key,
      operation,
      fingerprint,
    });
  } catch (e) {
    return {
      ok: false,
      response: errorResponse(
        'NETWORK',
        e instanceof Error ? e.message : 'idempotency store unavailable',
        requestId
      ),
    };
  }

  if (begin.kind === 'reused') {
    return {
      ok: false,
      response: errorResponse(
        'IDEMPOTENCY_KEY_REUSED',
        'idempotencyKey is already recorded with a different request fingerprint',
        requestId
      ),
    };
  }

  if (begin.kind === 'pending_inflight') {
    // Still in flight — cannot be mistaken for success; contract maps in-flight to 404.
    return {
      ok: false,
      response: errorResponse(
        'OPERATION_NOT_FOUND',
        'No operation record is visible for this key under the calling credential',
        requestId
      ),
    };
  }

  if (begin.kind === 'replay' && begin.record) {
    const rec = begin.record;
    if (
      rec.responseStatus !== undefined &&
      rec.responseBody !== undefined
    ) {
      return {
        ok: true,
        key,
        credentialHash,
        fingerprint,
        replay: new Response(rec.responseBody, {
          status: rec.responseStatus,
          headers: {
            'content-type': 'application/json',
            'x-request-id': requestId,
            'x-idempotency-replay': '1',
          },
        }),
      };
    }
    // Terminal without stored body — reconstruct minimal honest body
    if (rec.status === 'completed') {
      const bodyObj = {
        accepted: true,
        operation: rec.operation,
        branch: rec.branch,
        headSha: rec.headSha,
        auditRef: rec.auditRef,
      };
      return {
        ok: true,
        key,
        credentialHash,
        fingerprint,
        replay: new Response(JSON.stringify(bodyObj), {
          status: 200,
          headers: {
            'content-type': 'application/json',
            'x-request-id': requestId,
            'x-idempotency-replay': '1',
          },
        }),
      };
    }
    const code = rec.code ?? 'NETWORK';
    return {
      ok: true,
      key,
      credentialHash,
      fingerprint,
      replay: errorResponse(
        code as 'NETWORK',
        `replayed terminal status ${rec.status}`,
        requestId
      ),
    };
  }

  if (begin.kind === 'not_visible') {
    return {
      ok: false,
      response: errorResponse(
        'OPERATION_NOT_FOUND',
        'No operation record is visible for this key under the calling credential',
        requestId
      ),
    };
  }

  // created — proceed
  return { ok: true, key, credentialHash, fingerprint };
}

export async function finishIdempotency(
  env: Env,
  opts: {
    credentialHash: string;
    id: string;
    status: OperationStatus;
    code?: string;
    auditRef?: string;
    branch?: string;
    headSha?: string;
    response: Response;
  }
): Promise<void> {
  const cloned = opts.response.clone();
  let responseBody = '';
  try {
    responseBody = await cloned.text();
  } catch {
    responseBody = '';
  }
  await completeOperation(env, {
    credentialHash: opts.credentialHash,
    id: opts.id,
    status: opts.status,
    code: opts.code,
    auditRef: opts.auditRef,
    branch: opts.branch,
    headSha: opts.headSha,
    responseStatus: opts.response.status,
    responseBody,
  });
}

/** Map HTTP error body to operation status for the record. */
export function outcomeFromErrorCode(
  code: string
): { status: OperationStatus; code: string } {
  if (code === 'NOT_IMPLEMENTED') {
    return { status: 'failed', code };
  }
  // Guard / policy refusals
  const refused = new Set([
    'HEAD_MOVED',
    'NONCE_REPLAYED',
    'SUBMISSION_IMMUTABLE',
    'ANCESTRY_FAIL',
    'RECONCILE_REQUIRED',
    'IDEMPOTENCY_KEY_REUSED',
    'HALT_ACTIVE',
    'BRANCH_MISMATCH',
    'BRANCH_INVALID',
    'BRANCH_MISSING',
    'DELTA_EMPTY',
    'MERGE_COMMITS',
    'DENIED_PATH',
    'SECRET_IN_DIFF',
    'REPO_NOT_PRIVATE',
    'RULESET_REJECTED',
    'AUTH_TIER_NOT_ALLOWED',
    'UNKNOWN_OP',
    'BAD_SIGNATURE',
    'UNKNOWN_KID',
    'CAPABILITY_EXPIRED',
    'TTL_TOO_LONG',
    'BAD_FIELD_COUNT',
    'BAD_ENCODING',
    'OPERATOR_AUTH',
    'DEVICE_NOT_ENROLLED',
  ]);
  if (refused.has(code)) {
    return { status: 'refused', code };
  }
  return { status: 'failed', code };
}

export async function finishFromResponse(
  env: Env,
  credentialHash: string,
  id: string,
  response: Response,
  extras?: { branch?: string; headSha?: string; auditRef?: string }
): Promise<Response> {
  const status = response.status;
  let opStatus: OperationStatus = 'failed';
  let code: string | undefined;
  let auditRef = extras?.auditRef;
  let headSha = extras?.headSha;
  let branch = extras?.branch;

  if (status >= 200 && status < 300) {
    opStatus = 'completed';
    try {
      const body = (await response.clone().json()) as Record<string, unknown>;
      if (typeof body.auditRef === 'string') auditRef = body.auditRef;
      if (typeof body.headSha === 'string') headSha = body.headSha;
      if (typeof body.branch === 'string') branch = body.branch;
    } catch {
      /* ignore */
    }
  } else {
    try {
      const body = (await response.clone().json()) as { error?: string };
      if (body.error) {
        const mapped = outcomeFromErrorCode(body.error);
        opStatus = mapped.status;
        code = mapped.code;
      }
      if (typeof (body as { auditRef?: string }).auditRef === 'string') {
        auditRef = (body as { auditRef: string }).auditRef;
      }
    } catch {
      code = 'NETWORK';
      opStatus = 'failed';
    }
  }

  await finishIdempotency(env, {
    credentialHash,
    id,
    status: opStatus,
    code,
    auditRef,
    branch,
    headSha,
    response,
  });
  return response;
}

export type { OperationRecord };
