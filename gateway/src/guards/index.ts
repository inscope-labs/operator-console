/**
 * Ordered guard runner G1–G15 (authoritative set for Gateway A).
 * Spec §10 + GATEWAY-A-COMPLETION-PLAN Stage 2.3.
 *
 * Registration order is insertion order. Modules call registerPublishGuard
 * in the sequence: G1 → G3 → G4 → G5 → G6 → G7 → G8 → G11 → G13 → G14 → G15.
 */

import type { OperatorCapability } from '../capability/types';
import type { ConsumerHandle } from '../adapter/handles/consumer';
import type { ErrorCode } from '../errors/catalogue';

export interface GuardContext {
  capability: OperatorCapability;
  /** Consumer repository handle (read surface). */
  consumer?: ConsumerHandle;
  /** True when G1 has already been established for this isolate/request. */
  consumerIsPrivate?: boolean;
  /** Optional pre-loaded branch tip / commit metadata. */
  branchMeta?: {
    exists: boolean;
    head: string;
    isAncestryOfMain?: boolean;
    hasMergeCommits?: boolean;
    changedPaths?: string[];
    deltaEmpty?: boolean;
  };
  /** Halt marker present? */
  haltActive?: boolean;
  /** Upload path: submission id and whether it already exists. */
  submissionId?: string;
  submissionExists?: boolean;
  /** Raw unified diff or concatenated blob text for secret scanning (G11). */
  diffText?: string;
}

export interface GuardOk {
  ok: true;
}

export interface GuardErr {
  ok: false;
  code: ErrorCode;
  message: string;
}

export type GuardResult = GuardOk | GuardErr;

export type Guard = (ctx: GuardContext) => Promise<GuardResult>;

const PUBLISH_GUARDS: { name: string; run: Guard }[] = [];

/**
 * Run the ordered guard set. Returns the first failure or { ok: true }.
 * Empty set fails closed.
 */
export async function runGuards(
  ctx: GuardContext,
  guards: { name: string; run: Guard }[] = PUBLISH_GUARDS
): Promise<GuardResult> {
  if (guards.length === 0) {
    return {
      ok: false,
      code: 'NETWORK',
      message: 'guard set not yet wired — refusing write',
    };
  }
  for (const g of guards) {
    const result = await g.run(ctx);
    if (!result.ok) {
      return result;
    }
  }
  return { ok: true };
}

export function registerPublishGuard(name: string, run: Guard): void {
  if (PUBLISH_GUARDS.some((g) => g.name === name)) return;
  PUBLISH_GUARDS.push({ name, run });
}

export function listPublishGuards(): string[] {
  return PUBLISH_GUARDS.map((g) => g.name);
}

export function clearPublishGuards(): void {
  PUBLISH_GUARDS.length = 0;
}
