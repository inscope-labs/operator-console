/**
 * ctxop-v1 LF-separated encode / decode.
 * Spec §8 — field order is normative and must match exactly.
 *
 * Wire form (LF-separated, no trailing LF before signature):
 *   ctxop-v1
 *   <kid>
 *   <exp>
 *   <order>
 *   <plan>
 *   <task>
 *   <op>
 *   <branch>
 *   <head>
 *   <nonce>
 *
 * Signature is appended after the message; it is not part of the message body.
 */

import {
  CAPABILITY_VERSION,
  type OperatorCapability,
  type OperationType,
  isOperationType,
} from './types';

const FIELD_COUNT = 10;

/** Encode a capability into the canonical LF-separated message (no signature). */
export function encodeCapability(cap: OperatorCapability): string {
  if (cap.version !== CAPABILITY_VERSION) {
    throw new Error(`unsupported capability version: ${cap.version}`);
  }
  return [
    CAPABILITY_VERSION,
    cap.kid,
    String(cap.exp),
    cap.order,
    cap.plan,
    cap.task,
    cap.op,
    cap.branch,
    cap.head,
    cap.nonce,
  ].join('\n');
}

/**
 * Decode an LF-separated message into an OperatorCapability.
 * Does not verify the signature — that is the responsibility of verify.ts.
 * Throws on malformed input (wrong field count, invalid op, non-numeric exp, …).
 */
export function decodeCapability(message: string): OperatorCapability {
  const lines = message.split('\n');
  if (lines.length !== FIELD_COUNT) {
    throw new Error(
      `ctxop-v1 field count mismatch: expected ${FIELD_COUNT}, got ${lines.length}`
    );
  }

  const [version, kid, expRaw, order, plan, task, opRaw, branch, head, nonce] =
    lines;

  if (version !== CAPABILITY_VERSION) {
    throw new Error(`unsupported capability version: ${version}`);
  }
  if (!kid || !/^[A-Za-z0-9_]{1,16}$/.test(kid)) {
    throw new Error(`invalid kid: ${kid}`);
  }
  const exp = Number(expRaw);
  if (!Number.isInteger(exp) || exp <= 0) {
    throw new Error(`invalid exp: ${expRaw}`);
  }
  if (!isOperationType(opRaw)) {
    throw new Error(`UNKNOWN_OP: ${opRaw}`);
  }
  if (!/^task-[A-Za-z0-9._-]+$/.test(branch)) {
    throw new Error(`BRANCH_INVALID: ${branch}`);
  }
  if (!/^[0-9a-f]{40}$/i.test(head)) {
    throw new Error(`invalid head SHA: ${head}`);
  }
  if (!nonce) {
    throw new Error('missing nonce');
  }

  return {
    version: CAPABILITY_VERSION,
    kid,
    exp,
    order,
    plan,
    task,
    op: opRaw as OperationType,
    branch,
    head,
    nonce,
  };
}

/**
 * Split a full capability string (message + signature) into parts.
 * Signature is expected as the final line after the 10-field message.
 */
export function splitSignedCapability(raw: string): {
  message: string;
  signature: string;
} {
  const idx = raw.lastIndexOf('\n');
  if (idx <= 0) {
    throw new Error('malformed signed capability: no signature separator');
  }
  return {
    message: raw.slice(0, idx),
    signature: raw.slice(idx + 1).trim(),
  };
}
