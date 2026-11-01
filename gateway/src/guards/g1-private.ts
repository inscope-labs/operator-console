/**
 * G1 — private-repository assertion.
 * Spec §10.1. Adapter construction already fails closed; this guard
 * re-checks at request time when metadata is available.
 */

import type { Guard, GuardResult } from './index';
import { registerPublishGuard } from './index';

export const g1Private: Guard = async (ctx): Promise<GuardResult> => {
  // Prefer explicit flag on context when the adapter has already verified.
  if (ctx.consumerIsPrivate === false) {
    return {
      ok: false,
      code: 'REPO_NOT_PRIVATE',
      message: 'G1: consumer repository is not private',
    };
  }
  if (ctx.consumerIsPrivate === true) {
    return { ok: true };
  }
  if (ctx.consumer) {
    try {
      const meta = await ctx.consumer.getRepoMetadata();
      if (!meta.private) {
        return {
          ok: false,
          code: 'REPO_NOT_PRIVATE',
          message: `G1: ${meta.full_name} is not private`,
        };
      }
      return { ok: true };
    } catch (e) {
      return {
        ok: false,
        code: 'NETWORK',
        message: `G1: privacy check failed: ${e instanceof Error ? e.message : String(e)}`,
      };
    }
  }
  // Fail closed if we cannot establish privacy.
  return {
    ok: false,
    code: 'REPO_NOT_PRIVATE',
    message: 'G1: unable to confirm consumer repository privacy',
  };
};

registerPublishGuard('G1', g1Private);
