import { describe, it, expect } from 'vitest';
import {
  assertOperationType,
  isPublishGrade,
  isRepoWide,
  isDecision,
  allOperations,
} from '../../../src/capability/ops';

describe('ops helpers', () => {
  it('assertOperationType accepts known ops', () => {
    expect(assertOperationType('publish')).toBe('publish');
    expect(assertOperationType('halt')).toBe('halt');
  });

  it('assertOperationType rejects unknown', () => {
    expect(() => assertOperationType('explode')).toThrow(/UNKNOWN_OP/);
  });

  it('classifies publish-grade / repo-wide / decision', () => {
    expect(isPublishGrade('publish')).toBe(true);
    expect(isPublishGrade('upload')).toBe(false);
    expect(isRepoWide('halt')).toBe(true);
    expect(isRepoWide('resume')).toBe(true);
    expect(isDecision('decide')).toBe(true);
    expect(isDecision('publish')).toBe(false);
  });

  it('allOperations returns the closed set', () => {
    const ops = allOperations();
    expect(ops).toContain('publish');
    expect(ops).toContain('decide');
    expect(ops.length).toBeGreaterThanOrEqual(10);
  });
});
