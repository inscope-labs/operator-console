/**
 * G4 — branch existence in the consumer repository.
 * Spec §10.4.
 */

import type { Guard, GuardResult } from './index';
import { registerPublishGuard } from './index';

export const g4BranchExists: Guard = async (ctx): Promise<GuardResult> => {
  if (ctx.branchMeta) {
    if (!ctx.branchMeta.exists) {
      return {
        ok: false,
        code: 'BRANCH_MISSING',
        message: `G4: branch ${ctx.capability.branch} does not exist`,
      };
    }
    return { ok: true };
  }
  // Without pre-loaded metadata, attempt live check if consumer handle present.
  if (ctx.consumer) {
    try {
      await ctx.consumer.getRef(ctx.capability.branch);
      return { ok: true };
    } catch {
      return {
        ok: false,
        code: 'BRANCH_MISSING',
        message: `G4: branch ${ctx.capability.branch} does not exist`,
      };
    }
  }
  return {
    ok: false,
    code: 'NETWORK',
    message: 'G4: no branch metadata and no consumer handle',
  };
};

registerPublishGuard('G4', g4BranchExists);
