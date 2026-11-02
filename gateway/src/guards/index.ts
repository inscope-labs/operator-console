/**
 * Ordered guard runner G1–G16.
 * Spec §10. Every write path must pass the full applicable set.
 * Guards are pure with respect to the request; they receive a typed context
 * and return a result. Fail closed — any throw or non-ok result aborts.
 */

import type { OperatorCapability } from '../capability/types';
import type { ErrorCode } from '../errors/catalogue';
import type { ConsumerHandle } from '../adapter/handles/consumer';

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

/**
 * Ordered list of guards that apply to publish (the strongest path).
 * Registration order is insertion order; modules call registerPublishGuard
 * in the sequence G1 → G3 → G4 → G13 (and later G5–G16).
 */
const PUBLISH_GUARDS: { name: string; run: Guard }[] = [];

/**
 * Run the ordered guard set. Returns the first failure or { ok: true }.
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

/**
 * Register a guard into the publish set.
 * Call from individual g*.ts modules at load time.
 */
export function registerPublishGuard(name: string, run: Guard): void {
  // Avoid duplicate names if the module is evaluated twice.
  if (PUBLISH_GUARDS.some((g) => g.name === name)) return;
  PUBLISH_GUARDS.push({ name, run });
}

/** Test helper. */
export function listPublishGuards(): string[] {
  return PUBLISH_GUARDS.map((g) => g.name);
}

/** Test helper. */
export function clearPublishGuards(): void {
  PUBLISH_GUARDS.length = 0;
}

/**
 * Load the core publish guards (G1, G3, G4, G13).
 * Side-effect import — call once from the Worker entry or publish route module.
 */
export function loadCorePublishGuards(): void {
  // Dynamic-style static imports so registration runs.
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  void import('./g1-private');
  void import('./g3-branch-shape');
  void import('./g4-branch-exists');
  void import('./g13-exact-sha');
}
