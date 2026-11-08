/**
 * G7 — no merge commits.
 * Spec §10.7 — task branch must not contain merge commits.
 * Failure: MERGE_COMMITS (400).
 */

import type { Guard, GuardResult } from './index';
import { registerPublishGuard } from './index';

export const g7NoMergeCommits: Guard = async (ctx): Promise<GuardResult> => {
  const meta = ctx.branchMeta;
  if (!meta || meta.hasMergeCommits === undefined) {
    return {
      ok: false,
      code: 'MERGE_COMMITS',
      message: 'G7: merge-commit status could not be established',
    };
  }
  if (meta.hasMergeCommits === true) {
    return {
      ok: false,
      code: 'MERGE_COMMITS',
      message: 'G7: task branch contains merge commits',
    };
  }
  return { ok: true };
};

registerPublishGuard('G7', g7NoMergeCommits);
