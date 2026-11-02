/**
 * G13 — exact-SHA publish.
 * Spec §10.13 — capability head must match the live branch tip.
 */

import type { Guard, GuardResult } from './index';
import { registerPublishGuard } from './index';

export const g13ExactSha: Guard = async (ctx): Promise<GuardResult> => {
  const expected = ctx.capability.head.toLowerCase();
  if (!/^[0-9a-f]{40}$/.test(expected)) {
    return {
      ok: false,
      code: 'HEAD_MOVED',
      message: 'G13: capability head is not a 40-char hex SHA',
    };
  }

  let live: string | undefined;
  if (ctx.branchMeta?.head) {
    live = ctx.branchMeta.head.toLowerCase();
  } else if (ctx.consumer) {
    try {
      const ref = await ctx.consumer.getRef(ctx.capability.branch);
      live = ref.sha.toLowerCase();
    } catch {
      return {
        ok: false,
        code: 'BRANCH_MISSING',
        message: `G13: cannot resolve tip for ${ctx.capability.branch}`,
      };
    }
  } else {
    return {
      ok: false,
      code: 'NETWORK',
      message: 'G13: no branch metadata and no consumer handle',
    };
  }

  if (live !== expected) {
    return {
      ok: false,
      code: 'HEAD_MOVED',
      message: `G13: capability head ${expected} != live ${live}`,
    };
  }
  return { ok: true };
};

registerPublishGuard('G13', g13ExactSha);
