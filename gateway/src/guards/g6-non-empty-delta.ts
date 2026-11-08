/**
 * G6 — non-empty delta.
 * Spec §10.6 — task branch must contain at least one commit / non-empty tree delta.
 * Failure: DELTA_EMPTY (400).
 */

import type { Guard, GuardResult } from './index';
import { registerPublishGuard } from './index';

export const g6NonEmptyDelta: Guard = async (ctx): Promise<GuardResult> => {
  const meta = ctx.branchMeta;
  if (!meta || meta.deltaEmpty === undefined) {
    return {
      ok: false,
      code: 'DELTA_EMPTY',
      message: 'G6: branch delta could not be established',
    };
  }
  if (meta.deltaEmpty === true) {
    return {
      ok: false,
      code: 'DELTA_EMPTY',
      message: 'G6: task branch delta is empty',
    };
  }
  return { ok: true };
};

registerPublishGuard('G6', g6NonEmptyDelta);
