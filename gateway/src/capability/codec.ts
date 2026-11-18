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

/**
 * Canonical decimal expiry — no sign, no leading zero (except "0" itself).
 * `Number()` alone accepts "+1761840000", "01761840000" and values that do
 * not fit a signed 64-bit integer, so the raw field is checked as text first.
 */
const EXP_CANONICAL_RE = /^(0|[1-9][0-9]*)$/;

/** Upper bound of a signed 64-bit integer (Long.MAX_VALUE). */
const EXP_MAX_I64 = 9223372036854775807n;

/** 40 lower-case hex characters — the expected branch head SHA. */
const HEAD_RE = /^[0-9a-f]{40}$/;

/** 16–64 lower-case hex characters — the single-use nonce. */
const NONCE_RE = /^[0-9a-f]{16,64}$/;

/** Task branch shape (docs/capability-format.md §2, `branch` row). */
const TASK_BRANCH_RE = /^task-[A-Za-z0-9._-]+$/;

/**
 * Task-scoped operations: the branch must be a `task-*` branch.
 * Source: docs/capability-format.md §2 `branch` row. `SET` is not listed
 * there — it is a task status operation, so it is scoped like its siblings
 * `SET_IN_PROGRESS` / `SET_DONE` (recorded as an open question in the
 * agent report).
 */
const TASK_SCOPED_OPS: readonly OperationType[] = [
  'publish',
  'upload',
  'reconcile',
  'decide',
  'order',
  'set',
  'set_in_progress',
  'set_done',
] as const;

/** Engine-scoped operations: the branch must be the configured engine branch. */
const ENGINE_SCOPED_OPS: readonly OperationType[] = [
  'halt',
  'resume',
  'sync',
  'doctor',
  'new',
] as const;

/**
 * Engine branch shape — the `unsafe-engine-branch` mutation class.
 * A branch that contains ".." or starts with "-" is refused (a ref that the
 * platform would treat as a range or an option). The configured engine branch
 * itself is not known to the codec; equality with it is checked where the
 * configuration is available (src/routes/halt.ts).
 */
function isSafeEngineBranch(branch: string): boolean {
  if (branch.length === 0) return false;
  if (branch.startsWith('-') || branch.startsWith('/')) return false;
  if (branch.endsWith('/') || branch.endsWith('.')) return false;
  if (branch.endsWith('.lock')) return false;
  if (branch.includes('..')) return false;
  if (branch.includes('//')) return false;
  if (branch.includes('@{')) return false;
  // Space, control characters and the characters git refuses in a ref name.
  if (/[\s~^:?*\[\\\x00-\x1f\x7f]/.test(branch)) return false;
  return true;
}

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
 *
 * The decoder is fail-closed: every field is checked against
 * docs/capability-format.md before the capability is handed on, and a
 * malformed field is refused with the catalogue code that names it:
 *
 *   BAD_FIELD_COUNT | BAD_ENCODING | UNKNOWN_OP | BRANCH_NOT_TASK | BRANCH_MISMATCH
 *
 * Field checks follow the order of docs/gateway-contract.md §7 (kid, exp,
 * nonce, op, branch, head) so that the code a caller sees matches the
 * contract's own ordering.
 */
export function decodeCapability(message: string): OperatorCapability {
  if (message.includes('\r')) {
    throw new Error('BAD_ENCODING: CR characters are not permitted');
  }
  // A leading or trailing LF is an encoding fault, not a field-count fault
  // (docs/capability-format.md §5: "no trailing LF").
  if (message.startsWith('\n') || message.endsWith('\n')) {
    throw new Error(
      'BAD_ENCODING: message must not begin or end with a line feed'
    );
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

  // exp — canonical decimal only, and it must fit a signed 64-bit integer.
  if (!EXP_CANONICAL_RE.test(expRaw)) {
    throw new Error(`BAD_ENCODING: invalid exp: ${expRaw}`);
  }
  const expBig = BigInt(expRaw);
  if (expBig <= 0n || expBig > EXP_MAX_I64) {
    throw new Error(`BAD_ENCODING: invalid exp: ${expRaw}`);
  }
  // Above 2^53-1 the exact integer is not representable as a JS number; the
  // capability keeps the nearest representable value (docs/fixtures/vectors/
  // valid_capabilities.json — valid-exp-max-long).
  const exp = Number(expBig);

  // order / plan / task are required fields (docs/fixtures/vectors/README.md
  // — mutation class `empty-field`).
  if (!order) {
    throw new Error('BAD_ENCODING: missing order');
  }
  if (!plan) {
    throw new Error('BAD_ENCODING: missing plan');
  }
  if (!task) {
    throw new Error('BAD_ENCODING: missing task');
  }

  if (!NONCE_RE.test(nonce)) {
    throw new Error(`BAD_ENCODING: invalid nonce`);
  }

  const op = fromWireOp(opRaw);

  // Branch scope — docs/capability-format.md §2, `branch` row.
  if (isTaskScopedOp(op)) {
    if (!TASK_BRANCH_RE.test(branch)) {
      throw new Error(`BRANCH_NOT_TASK: ${branch}`);
    }
  } else {
    if (!isSafeEngineBranch(branch)) {
      throw new Error(`BAD_ENCODING: invalid engine branch: ${branch}`);
    }
    if (TASK_BRANCH_RE.test(branch)) {
      throw new Error(
        `BRANCH_MISMATCH: engine-scoped op ${op} must not carry a task branch: ${branch}`
      );
    }
  }

  if (!HEAD_RE.test(head)) {
    throw new Error(`BAD_ENCODING: invalid head SHA`);
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

/** True when the operation is authorised by a task-scoped `task-*` branch. */
export function isTaskScopedOp(op: OperationType): boolean {
  return (TASK_SCOPED_OPS as readonly string[]).includes(op);
}

/** True when the operation is authorised by the configured engine branch. */
export function isEngineScopedOp(op: OperationType): boolean {
  return (ENGINE_SCOPED_OPS as readonly string[]).includes(op);
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
