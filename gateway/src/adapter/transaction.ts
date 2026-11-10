/**
 * Privileged write-transaction helper (Stage 3.1).
 *
 * Ordered pipeline for every subsequent write stage (publish / upload / …):
 *   1. Rate-budget check (RATE_BUDGET_LOW)
 *   2. Ordered guard set (first failure wins)
 *   3. GitHub mutation callback
 *   4. Audit emission (success and refused-after-guards)
 *
 * Nonce policy: the capability gate records the nonce *before* this helper
 * runs (single-use consumed on the way in). Guard refusal therefore leaves
 * the nonce consumed and an audit record with outcome=refused — consistent
 * and fail-closed. This helper never returns a bare success without auditRef.
 *
 * Adapter-level multi-step `transaction(steps)` remains available for
 * sequential cross-repo idempotent steps used inside mutate().
 */

import type { ErrorCode } from '../errors/catalogue';
import type { GuardContext, GuardResult } from '../guards/index';
import { runGuards } from '../guards/index';
import { emitAudit, type AuditRecord } from '../audit/emit';
import {
  getSharedRateCoordinator,
  type RateCoordinator,
} from './rate-coordinator';
import type { TransactionResult, TransactionStep } from './multi-repo-adapter';
import type { Env } from '../runtime';

export type WritePhase = 'rate' | 'guards' | 'mutate' | 'audit';

export interface WriteMutationOk {
  ok: true;
  /** Free-form mutation result (PR URL, SHA, …). */
  result?: unknown;
}

export interface WriteMutationErr {
  ok: false;
  code: ErrorCode;
  message: string;
}

export type WriteMutationResult = WriteMutationOk | WriteMutationErr;

export interface WriteTransactionInput {
  requestId: string;
  op: string;
  /** Capability fields for audit (already verified + nonce stored). */
  plan?: string;
  task?: string;
  branch?: string;
  head?: string;
  guardCtx: GuardContext;
  /** Perform the GitHub side-effect. Only called if guards pass. */
  mutate: () => Promise<WriteMutationResult>;
  now?: number;
  rate?: RateCoordinator;
  /** When set, audit records are also appended to AuditStream. */
  env?: Env;
}

export interface WriteTransactionSuccess {
  ok: true;
  auditRef: string;
  audit: AuditRecord;
  mutation?: unknown;
}

export interface WriteTransactionFailure {
  ok: false;
  code: ErrorCode;
  message: string;
  phase: WritePhase;
  /** Present when refusal happened after guards started (or later). */
  auditRef?: string;
  audit?: AuditRecord;
}

export type WriteTransactionOutcome =
  | WriteTransactionSuccess
  | WriteTransactionFailure;

/**
 * Run the privileged write pipeline. Never returns success without auditRef.
 */
export async function runWriteTransaction(
  input: WriteTransactionInput
): Promise<WriteTransactionOutcome> {
  const now = input.now ?? Math.floor(Date.now() / 1000);
  const rate = input.rate ?? getSharedRateCoordinator();

  // 1. Rate budget
  const rateCheck = rate.check(now);
  if (!rateCheck.ok) {
    const audit = emitAudit({
      env: input.env,
      requestId: input.requestId,
      op: input.op,
      outcome: 'refused',
      code: rateCheck.code,
      branch: input.branch,
      head: input.head,
      plan: input.plan,
      task: input.task,
      now,
    });
    return {
      ok: false,
      code: rateCheck.code,
      message: rateCheck.message,
      phase: 'rate',
      auditRef: audit.auditRef,
      audit,
    };
  }

  // 2. Guards
  const guardResult: GuardResult = await runGuards(input.guardCtx);
  if (!guardResult.ok) {
    const audit = emitAudit({
      env: input.env,
      requestId: input.requestId,
      op: input.op,
      outcome: 'refused',
      code: guardResult.code,
      branch: input.branch,
      head: input.head,
      plan: input.plan,
      task: input.task,
      now,
      detail: { phase: 'guards' },
    });
    return {
      ok: false,
      code: guardResult.code,
      message: guardResult.message,
      phase: 'guards',
      auditRef: audit.auditRef,
      audit,
    };
  }

  // 3. Mutation
  let mutation: WriteMutationResult;
  try {
    mutation = await input.mutate();
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e);
    const audit = emitAudit({
      env: input.env,
      requestId: input.requestId,
      op: input.op,
      outcome: 'error',
      code: 'NETWORK',
      branch: input.branch,
      head: input.head,
      plan: input.plan,
      task: input.task,
      now,
      detail: { phase: 'mutate', error: message },
    });
    return {
      ok: false,
      code: 'NETWORK',
      message,
      phase: 'mutate',
      auditRef: audit.auditRef,
      audit,
    };
  }

  if (!mutation.ok) {
    const audit = emitAudit({
      env: input.env,
      requestId: input.requestId,
      op: input.op,
      outcome: 'refused',
      code: mutation.code,
      branch: input.branch,
      head: input.head,
      plan: input.plan,
      task: input.task,
      now,
      detail: { phase: 'mutate' },
    });
    return {
      ok: false,
      code: mutation.code,
      message: mutation.message,
      phase: 'mutate',
      auditRef: audit.auditRef,
      audit,
    };
  }

  // 4. Audit success
  const audit = emitAudit({
    env: input.env,
    requestId: input.requestId,
    op: input.op,
    outcome: 'accepted',
    branch: input.branch,
    head: input.head,
    plan: input.plan,
    task: input.task,
    now,
  });

  return {
    ok: true,
    auditRef: audit.auditRef,
    audit,
    mutation: mutation.result,
  };
}

/**
 * Sequential, idempotent multi-step executor for adapter-level work.
 * Steps already completed (by idempotencyKey) are skipped on retry within
 * the same invocation map supplied by the caller.
 */
export async function runSequentialSteps(
  steps: TransactionStep[],
  completedKeys: Set<string> = new Set()
): Promise<TransactionResult> {
  const completed: string[] = [];
  for (const step of steps) {
    if (completedKeys.has(step.idempotencyKey)) {
      completed.push(step.idempotencyKey);
      continue;
    }
    try {
      await step.run();
      completedKeys.add(step.idempotencyKey);
      completed.push(step.idempotencyKey);
    } catch (e) {
      const reason = e instanceof Error ? e.message : String(e);
      return {
        ok: false,
        completed,
        failedAt: step.idempotencyKey,
        reason,
      };
    }
  }
  return { ok: true, completed };
}
