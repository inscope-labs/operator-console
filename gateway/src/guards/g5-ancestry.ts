/**
 * G5 — ancestry of main / upstream base.
 * Spec §10.5 — task branch must be a descendant of the upstream base.
 * Failure: ANCESTRY_FAIL (409).
 */

import type { Guard, GuardResult } from './index';
import { registerPublishGuard } from './index';

export const g5Ancestry: Guard = async (ctx): Promise<GuardResult> => {
  const meta = ctx.branchMeta;
  if (!meta || meta.isAncestryOfMain === undefined) {
    return {
      ok: false,
      code: 'ANCESTRY_FAIL',
      message: 'G5: ancestry relative to upstream base could not be established',
    };
  }
  if (meta.isAncestryOfMain === false) {
    return {
      ok: false,
      code: 'ANCESTRY_FAIL',
      message: 'G5: task branch is not a descendant of the upstream base',
    };
  }
  return { ok: true };
};

registerPublishGuard('G5', g5Ancestry);
