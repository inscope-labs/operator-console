import { describe, it, expect, beforeEach } from 'vitest';
import {
  runWriteTransaction,
  runSequentialSteps,
} from '../../../src/adapter/transaction';
import {
  RateCoordinator,
  resetSharedRateCoordinator,
} from '../../../src/adapter/rate-coordinator';
import {
  clearPublishGuards,
  registerPublishGuard,
  type GuardContext,
} from '../../../src/guards/index';
import { clearAuditRecords, listAuditRecords } from '../../../src/audit/emit';
import {
  CAPABILITY_VERSION,
  type OperatorCapability,
} from '../../../src/capability/types';
import type { TransactionStep } from '../../../src/adapter/multi-repo-adapter';

function cap(): OperatorCapability {
  return {
    version: CAPABILITY_VERSION,
    kid: 'k1',
    exp: Math.floor(Date.now() / 1000) + 120,
    order: 'o',
    plan: 'atlas',
    task: '7',
    op: 'publish',
    branch: 'task-7-work',
    head: 'a'.repeat(40),
    nonce: 'n1',
  };
}

function baseGuardCtx(over: Partial<GuardContext> = {}): GuardContext {
  return {
    capability: cap(),
    consumerIsPrivate: true,
    branchMeta: {
      exists: true,
      head: 'a'.repeat(40),
      isAncestryOfMain: true,
      hasMergeCommits: false,
      changedPaths: ['src/x.ts'],
      deltaEmpty: false,
    },
    haltActive: false,
    diffText: '+ok\n',
    ...over,
  };
}

beforeEach(() => {
  clearPublishGuards();
  clearAuditRecords();
  resetSharedRateCoordinator();
  // Pass-through guard so pipeline can succeed without loading full set
  registerPublishGuard('TEST_PASS', async () => ({ ok: true }));
});

describe('runWriteTransaction', () => {
  it('returns success with auditRef when guards and mutate pass', async () => {
    const outcome = await runWriteTransaction({
      requestId: 'req-1',
      op: 'publish',
      plan: 'atlas',
      task: '7',
      branch: 'task-7-work',
      head: 'a'.repeat(40),
      guardCtx: baseGuardCtx(),
      mutate: async () => ({ ok: true, result: { sha: 'a'.repeat(40) } }),
    });
    expect(outcome.ok).toBe(true);
    if (outcome.ok) {
      expect(outcome.auditRef).toMatch(/^audit-/);
      expect(outcome.audit.outcome).toBe('accepted');
    }
    expect(listAuditRecords()).toHaveLength(1);
  });

  it('refuses on rate budget and still emits audit', async () => {
    const rate = new RateCoordinator(100);
    rate.observe(10, Math.floor(Date.now() / 1000) + 3600);
    const outcome = await runWriteTransaction({
      requestId: 'req-2',
      op: 'publish',
      guardCtx: baseGuardCtx(),
      rate,
      mutate: async () => ({ ok: true }),
    });
    expect(outcome.ok).toBe(false);
    if (!outcome.ok) {
      expect(outcome.phase).toBe('rate');
      expect(outcome.code).toBe('RATE_BUDGET_LOW');
      expect(outcome.auditRef).toMatch(/^audit-/);
    }
  });

  it('refuses on guard failure, emits refused audit, does not mutate', async () => {
    clearPublishGuards();
    registerPublishGuard('TEST_FAIL', async () => ({
      ok: false,
      code: 'HALT_ACTIVE',
      message: 'halted',
    }));
    let mutated = false;
    const outcome = await runWriteTransaction({
      requestId: 'req-3',
      op: 'publish',
      branch: 'task-7-work',
      guardCtx: baseGuardCtx({ haltActive: true }),
      mutate: async () => {
        mutated = true;
        return { ok: true };
      },
    });
    expect(mutated).toBe(false);
    expect(outcome.ok).toBe(false);
    if (!outcome.ok) {
      expect(outcome.phase).toBe('guards');
      expect(outcome.code).toBe('HALT_ACTIVE');
      expect(outcome.auditRef).toBeDefined();
    }
  });

  it('mutation failure emits error audit with auditRef', async () => {
    const outcome = await runWriteTransaction({
      requestId: 'req-4',
      op: 'publish',
      guardCtx: baseGuardCtx(),
      mutate: async () => {
        throw new Error('github down');
      },
    });
    expect(outcome.ok).toBe(false);
    if (!outcome.ok) {
      expect(outcome.phase).toBe('mutate');
      expect(outcome.code).toBe('NETWORK');
      expect(outcome.auditRef).toMatch(/^audit-/);
    }
  });

  it('never returns success without auditRef', async () => {
    const outcome = await runWriteTransaction({
      requestId: 'req-5',
      op: 'publish',
      guardCtx: baseGuardCtx(),
      mutate: async () => ({ ok: true }),
    });
    expect(outcome.ok).toBe(true);
    if (outcome.ok) expect(outcome.auditRef.length).toBeGreaterThan(0);
  });
});

describe('runSequentialSteps', () => {
  it('runs all steps and records completed keys', async () => {
    const log: string[] = [];
    const steps: TransactionStep[] = [
      {
        repo: 'consumer',
        idempotencyKey: 'a',
        run: async () => {
          log.push('a');
        },
      },
      {
        repo: 'product',
        idempotencyKey: 'b',
        run: async () => {
          log.push('b');
        },
      },
    ];
    const r = await runSequentialSteps(steps);
    expect(r.ok).toBe(true);
    expect(r.completed).toEqual(['a', 'b']);
    expect(log).toEqual(['a', 'b']);
  });

  it('stops on failure and reports failedAt', async () => {
    const steps: TransactionStep[] = [
      {
        repo: 'consumer',
        idempotencyKey: 'ok',
        run: async () => undefined,
      },
      {
        repo: 'consumer',
        idempotencyKey: 'bad',
        run: async () => {
          throw new Error('boom');
        },
      },
    ];
    const r = await runSequentialSteps(steps);
    expect(r.ok).toBe(false);
    expect(r.completed).toEqual(['ok']);
    expect(r.failedAt).toBe('bad');
  });

  it('skips already completed idempotency keys', async () => {
    let runs = 0;
    const steps: TransactionStep[] = [
      {
        repo: 'consumer',
        idempotencyKey: 'x',
        run: async () => {
          runs += 1;
        },
      },
    ];
    const done = new Set(['x']);
    const r = await runSequentialSteps(steps, done);
    expect(r.ok).toBe(true);
    expect(runs).toBe(0);
  });
});
