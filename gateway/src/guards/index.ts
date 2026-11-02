/**
 * Ordered guard runner G1–G16.
 * Spec §10. Every write path must pass the full applicable set.
 * Guards are pure with respect to the request; they receive a typed context
 * and return a result. Fail closed — any throw or non-ok result aborts.
 */

import type { OperatorCapability } from '../capability/types';
import type { ErrorCode } from '../errors/catalogue';

export interface GuardContext {
  capability: OperatorCapability;
  /** Consumer repository handle (read surface). */
  // consumer: ConsumerHandle;  // filled once adapter is wired
  /** Product repository handle when the operation crosses to product. */
  // product?: ProductHandle;
  /** Optional pre-loaded branch tip / commit metadata to avoid extra round-trips. */
  branchMeta?: {
    exists: boolean;
    head: string;
    isAncestryOfMain: boolean;
    hasMergeCommits: boolean;
    changedPaths: string[];
    deltaEmpty: boolean;
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
 * Individual operations may use a subset; the runner never reorders.
 */
const PUBLISH_GUARDS: { name: string; run: Guard }[] = [
  // Concrete implementations live in g*.ts and are wired here once written.
  // Until then the runner fails closed.
];

/**
 * Run the ordered guard set. Returns the first failure or { ok: true }.
 */
export async function runGuards(
  ctx: GuardContext,
  guards: { name: string; run: Guard }[] = PUBLISH_GUARDS
): Promise<GuardResult> {
  if (guards.length === 0) {
    // Fail closed while the set is empty — prevents accidental bypass during scaffold.
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
  PUBLISH_GUARDS.push({ name, run });
}
