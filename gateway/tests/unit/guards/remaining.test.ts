import { describe, it, expect, beforeEach } from 'vitest';
import {
  clearPublishGuards,
  listPublishGuards,
  runGuards,
  type GuardContext,
} from '../../../src/guards/index';
import { g1Private } from '../../../src/guards/g1-private';
import { g3BranchShape } from '../../../src/guards/g3-branch-shape';
import { g4BranchExists } from '../../../src/guards/g4-branch-exists';
import { g5Ancestry } from '../../../src/guards/g5-ancestry';
import { g6NonEmptyDelta } from '../../../src/guards/g6-non-empty-delta';
import { g7NoMergeCommits } from '../../../src/guards/g7-no-merge-commits';
import { g8DeniedPaths, pathIsDenied } from '../../../src/guards/g8-denied-paths';
import {
  g11SecretPatterns,
  diffContainsSecret,
} from '../../../src/guards/g11-secret-patterns';
import { g13ExactSha } from '../../../src/guards/g13-exact-sha';
import { g14Halt } from '../../../src/guards/g14-halt';
import { g15SubmissionImmutable } from '../../../src/guards/g15-submission-immutable';
import { registerPublishGuard } from '../../../src/guards/index';
import { CAPABILITY_VERSION, type OperatorCapability } from '../../../src/capability/types';

function cap(over: Partial<OperatorCapability> = {}): OperatorCapability {
  return {
    version: CAPABILITY_VERSION,
    kid: 'k1',
    exp: Math.floor(Date.now() / 1000) + 120,
    order: 'o',
    plan: 'p',
    task: '7',
    op: 'publish',
    branch: 'task-7-work',
    head: 'a'.repeat(40),
    nonce: 'n1',
    ...over,
  };
}

function baseCtx(over: Partial<GuardContext> = {}): GuardContext {
  return {
    capability: cap(),
    consumerIsPrivate: true,
    branchMeta: {
      exists: true,
      head: 'a'.repeat(40),
      isAncestryOfMain: true,
      hasMergeCommits: false,
      changedPaths: ['src/app.ts'],
      deltaEmpty: false,
    },
    haltActive: false,
    diffText: 'diff --git a/src/app.ts\n+console.log(1)\n',
    ...over,
  };
}

beforeEach(() => {
  clearPublishGuards();
});

describe('G5 ancestry', () => {
  it('passes when isAncestryOfMain is true', async () => {
    const r = await g5Ancestry(baseCtx());
    expect(r.ok).toBe(true);
  });
  it('refuses when not ancestral', async () => {
    const r = await g5Ancestry(
      baseCtx({ branchMeta: { exists: true, head: 'a'.repeat(40), isAncestryOfMain: false } })
    );
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.code).toBe('ANCESTRY_FAIL');
  });
  it('fails closed when evidence missing', async () => {
    const r = await g5Ancestry(baseCtx({ branchMeta: { exists: true, head: 'a'.repeat(40) } }));
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.code).toBe('ANCESTRY_FAIL');
  });
});

describe('G6 non-empty delta', () => {
  it('passes when deltaEmpty is false', async () => {
    expect((await g6NonEmptyDelta(baseCtx())).ok).toBe(true);
  });
  it('refuses empty delta', async () => {
    const r = await g6NonEmptyDelta(
      baseCtx({
        branchMeta: {
          exists: true,
          head: 'a'.repeat(40),
          deltaEmpty: true,
        },
      })
    );
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.code).toBe('DELTA_EMPTY');
  });
});

describe('G7 no merge commits', () => {
  it('passes when hasMergeCommits is false', async () => {
    expect((await g7NoMergeCommits(baseCtx())).ok).toBe(true);
  });
  it('refuses merge commits', async () => {
    const r = await g7NoMergeCommits(
      baseCtx({
        branchMeta: {
          exists: true,
          head: 'a'.repeat(40),
          hasMergeCommits: true,
        },
      })
    );
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.code).toBe('MERGE_COMMITS');
  });
});

describe('G8 denied paths', () => {
  it('detects denied prefixes', () => {
    expect(pathIsDenied('engine/foo.ts')).toBe(true);
    expect(pathIsDenied('plans/x.md')).toBe(true);
    expect(pathIsDenied('.github/workflows/ci.yml')).toBe(true);
    expect(pathIsDenied('src/app.ts')).toBe(false);
  });
  it('refuses denied path in changedPaths', async () => {
    const r = await g8DeniedPaths(
      baseCtx({
        branchMeta: {
          exists: true,
          head: 'a'.repeat(40),
          changedPaths: ['src/ok.ts', 'engine/core.ts'],
        },
      })
    );
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.code).toBe('DENIED_PATH');
  });
  it('passes clean paths', async () => {
    expect((await g8DeniedPaths(baseCtx())).ok).toBe(true);
  });
});

describe('G11 secret patterns', () => {
  it('detects ghp_ and PEM keys', () => {
    expect(diffContainsSecret('token ghp_abcdefghijklmnopqrstuvwxyz012345')).toBe(true);
    expect(
      diffContainsSecret('github_pat_abcdefghijklmnopqrstuvwxyz0123456789')
    ).toBe(true);
    expect(
      diffContainsSecret('-----BEGIN RSA PRIVATE KEY-----\nMIIE')
    ).toBe(true);
    expect(diffContainsSecret('+console.log(1)')).toBe(false);
  });
  it('refuses secret in diffText', async () => {
    const r = await g11SecretPatterns(
      baseCtx({
        diffText: '+const t = "ghp_abcdefghijklmnopqrstuvwxyz012345";\n',
      })
    );
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.code).toBe('SECRET_IN_DIFF');
  });
  it('passes clean diff', async () => {
    expect((await g11SecretPatterns(baseCtx())).ok).toBe(true);
  });
});

describe('G14 halt', () => {
  it('refuses when haltActive', async () => {
    const r = await g14Halt(baseCtx({ haltActive: true }));
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.code).toBe('HALT_ACTIVE');
  });
  it('passes when halt inactive or unset', async () => {
    expect((await g14Halt(baseCtx({ haltActive: false }))).ok).toBe(true);
    expect((await g14Halt(baseCtx({ haltActive: undefined }))).ok).toBe(true);
  });
});

describe('G15 submission immutable', () => {
  it('no-ops when submissionId absent (publish path)', async () => {
    expect((await g15SubmissionImmutable(baseCtx())).ok).toBe(true);
  });
  it('refuses when submission already exists', async () => {
    const r = await g15SubmissionImmutable(
      baseCtx({ submissionId: 'sub-1', submissionExists: true })
    );
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.code).toBe('SUBMISSION_IMMUTABLE');
  });
  it('passes new submission id', async () => {
    const r = await g15SubmissionImmutable(
      baseCtx({ submissionId: 'sub-2', submissionExists: false })
    );
    expect(r.ok).toBe(true);
  });
});

describe('full remaining pipeline registration', () => {
  it('wires G1..G15 in order and each can refuse independently', async () => {
    // Re-register in pipeline order after clearPublishGuards (module side-effects already ran once).
    registerPublishGuard('G1', g1Private);
    registerPublishGuard('G3', g3BranchShape);
    registerPublishGuard('G4', g4BranchExists);
    registerPublishGuard('G5', g5Ancestry);
    registerPublishGuard('G6', g6NonEmptyDelta);
    registerPublishGuard('G7', g7NoMergeCommits);
    registerPublishGuard('G8', g8DeniedPaths);
    registerPublishGuard('G11', g11SecretPatterns);
    registerPublishGuard('G13', g13ExactSha);
    registerPublishGuard('G14', g14Halt);
    registerPublishGuard('G15', g15SubmissionImmutable);

    expect(listPublishGuards()).toEqual([
      'G1',
      'G3',
      'G4',
      'G5',
      'G6',
      'G7',
      'G8',
      'G11',
      'G13',
      'G14',
      'G15',
    ]);

    const ok = await runGuards(baseCtx());
    expect(ok.ok).toBe(true);

    // Independent refusal: G14
    const halted = await runGuards(baseCtx({ haltActive: true }));
    expect(halted.ok).toBe(false);
    if (!halted.ok) expect(halted.code).toBe('HALT_ACTIVE');

    // Independent refusal: G8
    const denied = await runGuards(
      baseCtx({
        branchMeta: {
          exists: true,
          head: 'a'.repeat(40),
          isAncestryOfMain: true,
          hasMergeCommits: false,
          changedPaths: ['.github/workflows/x.yml'],
          deltaEmpty: false,
        },
      })
    );
    expect(denied.ok).toBe(false);
    if (!denied.ok) expect(denied.code).toBe('DENIED_PATH');
  });
});
