/**
 * Global rate-budget coordination (§11.7 / Stage 3.1).
 *
 * Tracks the most recent GitHub primary rate-limit remaining/reset observed
 * on adapter responses. Before privileged mutations the write transaction
 * refuses with RATE_BUDGET_LOW when remaining < RATE_FLOOR.
 *
 * This is process-local state on the Worker isolate; it is intentionally
 * simple. A Durable Object coordinator is not required for single-installation
 * gateways at 0.6.0 scale (documented below).
 */

import { RATE_FLOOR } from '../config/constants';

export interface RateSnapshot {
  remaining: number;
  /** Unix seconds when the budget resets. */
  reset: number;
  /** Unix seconds when this snapshot was observed. */
  observedAt: number;
}

export interface RateCheckOk {
  ok: true;
  snapshot: RateSnapshot | null;
}

export interface RateCheckErr {
  ok: false;
  code: 'RATE_BUDGET_LOW';
  message: string;
  snapshot: RateSnapshot;
}

export type RateCheckResult = RateCheckOk | RateCheckErr;

/**
 * Why a Durable Object is unnecessary for 0.6.0:
 * - One GitHub App installation shares one primary rate bucket.
 * - Worker isolates that serve writes are few; the last-seen remaining
 *   value is a soft gate before the hard 403/429 from GitHub.
 * - Promoting to a DO is deferred until multi-isolate contention is measured.
 */
export class RateCoordinator {
  private snapshot: RateSnapshot | null = null;
  private readonly floor: number;

  constructor(floor: number = RATE_FLOOR) {
    this.floor = floor;
  }

  /** Ingest rate-limit headers from a GitHub API response. */
  observeHeaders(headers: Headers, nowSec: number = Math.floor(Date.now() / 1000)): void {
    const remainingRaw = headers.get('x-ratelimit-remaining');
    const resetRaw = headers.get('x-ratelimit-reset');
    if (remainingRaw === null || resetRaw === null) return;
    const remaining = Number(remainingRaw);
    const reset = Number(resetRaw);
    if (!Number.isFinite(remaining) || !Number.isFinite(reset)) return;
    this.snapshot = { remaining, reset, observedAt: nowSec };
  }

  /** Observe a numeric budget directly (tests / manual inject). */
  observe(remaining: number, reset: number, nowSec: number = Math.floor(Date.now() / 1000)): void {
    this.snapshot = { remaining, reset, observedAt: nowSec };
  }

  getSnapshot(): RateSnapshot | null {
    return this.snapshot;
  }

  /**
   * Pre-mutation check. If we have no snapshot yet, allow (fail open on
   * unknown budget only for the first call; GitHub will still enforce).
   * After a snapshot exists, remaining < floor → RATE_BUDGET_LOW.
   */
  check(nowSec: number = Math.floor(Date.now() / 1000)): RateCheckResult {
    if (!this.snapshot) {
      return { ok: true, snapshot: null };
    }
    // Budget fully restored after reset time.
    if (nowSec >= this.snapshot.reset) {
      return { ok: true, snapshot: this.snapshot };
    }
    if (this.snapshot.remaining < this.floor) {
      return {
        ok: false,
        code: 'RATE_BUDGET_LOW',
        message: `GitHub rate budget remaining ${this.snapshot.remaining} is below floor ${this.floor}`,
        snapshot: this.snapshot,
      };
    }
    return { ok: true, snapshot: this.snapshot };
  }
}

/** Process-local singleton used by the adapter / write pipeline. */
let shared: RateCoordinator | null = null;

export function getSharedRateCoordinator(): RateCoordinator {
  if (!shared) shared = new RateCoordinator();
  return shared;
}

/** Test helper. */
export function resetSharedRateCoordinator(floor?: number): RateCoordinator {
  shared = new RateCoordinator(floor);
  return shared;
}
