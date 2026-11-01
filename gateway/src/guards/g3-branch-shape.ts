/**
 * G3 — branch name shape.
 * Spec §10.3 — ^task-[A-Za-z0-9._-]+$ ; never main.
 */

import type { Guard, GuardResult } from './index';
import { registerPublishGuard } from './index';

const BRANCH_RE = /^task-[A-Za-z0-9._-]+$/;

export const g3BranchShape: Guard = async (ctx): Promise<GuardResult> => {
  const branch = ctx.capability.branch;
  if (!BRANCH_RE.test(branch) || branch === 'main') {
    return {
      ok: false,
      code: 'BRANCH_INVALID',
      message: `G3: branch name rejected: ${branch}`,
    };
  }
  return { ok: true };
};

registerPublishGuard('G3', g3BranchShape);
