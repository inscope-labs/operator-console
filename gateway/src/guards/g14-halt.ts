/**
 * G14 — global halt marker.
 * Spec §10.14 — write operations refuse while the engine halt marker is active.
 * Failure: HALT_ACTIVE (409 in frozen catalogue).
 */

import type { Guard, GuardResult } from './index';
import { registerPublishGuard } from './index';

export const g14Halt: Guard = async (ctx): Promise<GuardResult> => {
  if (ctx.haltActive === true) {
    return {
      ok: false,
      code: 'HALT_ACTIVE',
      message: 'G14: global engine halt is active; write operations are blocked',
    };
  }
  // haltActive undefined/false → not engaged; allow.
  return { ok: true };
};

registerPublishGuard('G14', g14Halt);
