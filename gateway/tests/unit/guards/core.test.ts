import { describe, it, expect, beforeEach } from 'vitest';
import {
  clearPublishGuards,
  listPublishGuards,
  runGuards,
  type GuardContext,
} from '../../../src/guards/index';
import { CAPABILITY_VERSION, type OperatorCapability } from '../../../src/capability/types';

// Importing register-core after clear requires re-import; register directly.
import { g1Private } from '../../../src/guards/g1-private';
import { g3BranchShape } from '../../../src/guards/g3-branch-shape';
import { g4BranchExists } from '../../../src/guards/g4-branch-exists';
import { g13ExactSha } from '../../../src/guards/g13-exact-sha';

function cap(over: Partial<OperatorCapability> = {}): OperatorCapability {
  return {
    version: CAPABILITY_VERSION,
    kid: 'k1',
    exp: Math.floor(Date.now() / 1000) + 120,
    order: 'o',
    plan: 'p',
    task: '7',
    op: 'publish',
    branch: 'task-7',
    head: 'a'.repeat(40),
    nonce: 'n1',
    ...over,
  };
}

beforeEach(() => {
  clearPublishGuards();
});

describe('core guards', () => {
  it('G3 accepts task-* and rejects main', async () => {
    const ok = await g3BranchShape({
      capability: cap({ branch: 'task-7' }),
    });
    expect(ok.ok).toBe(true);
    const bad = await g3BranchShape({
      capability: cap({ branch: 'main' }),
    });
    expect(bad.ok).toBe(false);
    if (!bad.ok) expect(bad.code).toBe('BRANCH_INVALID');
  });

  it('G4 uses branchMeta.exists', async () => {
    const missing = await g4BranchExists({
      capability: cap(),
      branchMeta: { exists: false, head: '' },
    });
    expect(missing.ok).toBe(false);
    if (!missing.ok) expect(missing.code).toBe('BRANCH_MISSING');

    const present = await g4BranchExists({
      capability: cap(),
      branchMeta: { exists: true, head: 'a'.repeat(40) },
    });
    expect(present.ok).toBe(true);
  });

  it('G13 rejects head drift', async () => {
    const drifted = await g13ExactSha({
      capability: cap({ head: 'a'.repeat(40) }),
      branchMeta: { exists: true, head: 'b'.repeat(40) },
    });
    expect(drifted.ok).toBe(false);
    if (!drifted.ok) expect(drifted.code).toBe('HEAD_MOVED');

    const match = await g13ExactSha({
      capability: cap({ head: 'a'.repeat(40) }),
      branchMeta: { exists: true, head: 'a'.repeat(40) },
    });
    expect(match.ok).toBe(true);
  });

  it('G1 fails closed without privacy evidence', async () => {
    const result = await g1Private({ capability: cap() });
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.code).toBe('REPO_NOT_PRIVATE');
  });

  it('G1 passes when consumerIsPrivate is true', async () => {
    const result = await g1Private({
      capability: cap(),
      consumerIsPrivate: true,
    });
    expect(result.ok).toBe(true);
  });

  it('runGuards executes ordered set', async () => {
    // Manually register for this test
    const { registerPublishGuard } = await import('../../../src/guards/index');
    registerPublishGuard('G1', g1Private);
    registerPublishGuard('G3', g3BranchShape);
    registerPublishGuard('G4', g4BranchExists);
    registerPublishGuard('G13', g13ExactSha);
    expect(listPublishGuards()).toEqual(['G1', 'G3', 'G4', 'G13']);

    const ctx: GuardContext = {
      capability: cap(),
      consumerIsPrivate: true,
      branchMeta: { exists: true, head: 'a'.repeat(40) },
    };
    const result = await runGuards(ctx);
    expect(result.ok).toBe(true);
  });
});
