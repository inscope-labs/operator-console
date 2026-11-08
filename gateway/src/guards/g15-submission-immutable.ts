/**
 * G15 — submission immutability.
 * Spec §10.15 — a submission id that already exists cannot be overwritten.
 * Primarily applies to upload; safe no-op when submissionId is absent (publish).
 * Failure: SUBMISSION_IMMUTABLE (409).
 */

import type { Guard, GuardResult } from './index';
import { registerPublishGuard } from './index';

export const g15SubmissionImmutable: Guard = async (ctx): Promise<GuardResult> => {
  if (ctx.submissionId === undefined || ctx.submissionId === '') {
    // Not an upload-shaped request; guard does not apply.
    return { ok: true };
  }
  if (ctx.submissionExists === true) {
    return {
      ok: false,
      code: 'SUBMISSION_IMMUTABLE',
      message: 'G15: submission id already recorded and cannot be overwritten',
    };
  }
  if (ctx.submissionExists === undefined) {
    return {
      ok: false,
      code: 'SUBMISSION_IMMUTABLE',
      message: 'G15: submission existence could not be established',
    };
  }
  return { ok: true };
};

registerPublishGuard('G15', g15SubmissionImmutable);
