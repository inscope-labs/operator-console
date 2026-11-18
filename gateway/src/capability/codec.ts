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
 *   <op>   — UPPER-CASE closed set from docs/capability-format.md
 *   <branch>
 *   <head>
 *   <nonce>
 *
 * Signature is appended after the message; it is not part of the message body.
 *
 * Internal OperationType values remain lower-case. Mapping between wire and
 * internal forms occurs only at the encode / decode boundary. A lower-case or
 * unknown wire op is refused as UNKNOWN_OP.
 */

import {
  CAPABILITY_VERSION,
  type OperatorCapability,
  type OperationType,
  isOperationType,
} from './types';

const FIELD_COUNT = 10;

/** Wire-form (UPPER) → internal OperationType (lower). Closed set from docs/capability-format.md. */
const WIRE_TO_INTERNAL: Readonly<Record<string, OperationType>> = {
  PUBLISH: 'publish',
  UPLOAD: 'upload',
  RECONCILE: 'reconcile',
  HALT: 'halt',
  RESUME: 'resume',
  DECIDE: 'decide',
  ORDER: 'order',
  SET: 'set',
  NEW_PLAN: 'new',
  SYNC: 'sync',
  DOCTOR: 'doctor',
  SET_IN_PROGRESS: 'set_in_progress',
  SET_DONE: 'set_done',
};

/** Internal OperationType → wire-form (UPPER). */
const INTERNAL_TO_WIRE: Readonly<Record<OperationType, string>> = {
  publish: 'PUBLISH',
  upload: 'UPLOAD',
  reconcile: 'RECONCILE',
  halt: 'HALT',
  resume: 'RESUME',
  decide: 'DECIDE',
  order: 'ORDER',
  set: 'SET',
  new: 'NEW_PLAN',
  sync: 'SYNC',
  doctor: 'DOCTOR',
  set_in_progress: 'SET_IN_PROGRESS',
  set_done: 'SET_DONE',
};

function toWireOp(op: OperationType): string {
  const wire = INTERNAL_TO_WIRE[op];
  if (!wire) {
    throw new Error(`UNKNOWN_OP: ${op}`);
  }
  return wire;
}

function fromWireOp(opRaw: string): OperationType {
  const internal = WIRE_TO_INTERNAL[opRaw];
  if (!internal || !isOperationType(internal)) {
    throw new Error(`UNKNOWN_OP: ${opRaw}`);
  }
  return internal;
}

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
    toWireOp(cap.op),
    cap.branch,
    cap.head,
    cap.nonce,
  ].join('\n');
}

/**
 * Decode an LF-separated message into an OperatorCapability.
 * Does not verify the signature — that is the responsibility of verify.ts.
 * Throws on malformed input with a catalogue-aligned prefix when possible:
 *   BAD_FIELD_COUNT | BAD_ENCODING | UNKNOWN_OP | BRANCH_INVALID
 */
export function decodeCapability(message: string): OperatorCapability {
  if (message.includes('\r')) {
    throw new Error('BAD_ENCODING: CR characters are not permitted');
  }

  const lines = message.split('\n');
  if (lines.length !== FIELD_COUNT) {
    throw new Error(
      `BAD_FIELD_COUNT: expected ${FIELD_COUNT}, got ${lines.length}`
    );
  }

  const [version, kid, expRaw, order, plan, task, opRaw, branch, head, nonce] =
    lines;

  if (version !== CAPABILITY_VERSION) {
    throw new Error(`BAD_ENCODING: unsupported capability version: ${version}`);
  }
  if (!kid || !/^[A-Za-z0-9_]{1,16}$/.test(kid)) {
    throw new Error(`BAD_ENCODING: invalid kid`);
  }
  const exp = Number(expRaw);
  if (!Number.isInteger(exp) || exp <= 0) {
    throw new Error(`BAD_ENCODING: invalid exp`);
  }
  const op = fromWireOp(opRaw);
  // Task branches for write ops; engine branch for halt/resume (contract §3.11–3.12).
  if (branch !== 'engine' && !/^task-[A-Za-z0-9._-]+$/.test(branch)) {
    throw new Error(`BRANCH_INVALID: ${branch}`);
  }
  if (!/^[0-9a-f]{40}$/i.test(head)) {
    throw new Error(`BAD_ENCODING: invalid head SHA`);
  }
  if (!nonce) {
    throw new Error('BAD_ENCODING: missing nonce');
  }

  return {
    version: CAPABILITY_VERSION,
    kid,
    exp,
    order,
    plan,
    task,
    op,
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
  if (raw.includes('\r')) {
    throw new Error('BAD_ENCODING: CR characters are not permitted');
  }
  const idx = raw.lastIndexOf('\n');
  if (idx <= 0) {
    throw new Error('BAD_ENCODING: no signature separator');
  }
  return {
    message: raw.slice(0, idx),
    signature: raw.slice(idx + 1).trim(),
  };
}
