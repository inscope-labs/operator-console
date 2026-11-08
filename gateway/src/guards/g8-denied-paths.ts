/**
 * G8 — denied paths.
 * Spec §10.8 — diff must not touch engine/, plans/, or .github/.
 * Failure: DENIED_PATH (403).
 */

import type { Guard, GuardResult } from './index';
import { registerPublishGuard } from './index';

const DENIED_PREFIXES = ['engine/', 'plans/', '.github/'] as const;

export function pathIsDenied(path: string): boolean {
  const normalised = path.replace(/^\/+/, '').replace(/\\/g, '/');
  return DENIED_PREFIXES.some(
    (p) => normalised === p.slice(0, -1) || normalised.startsWith(p)
  );
}

export const g8DeniedPaths: Guard = async (ctx): Promise<GuardResult> => {
  const paths = ctx.branchMeta?.changedPaths;
  if (!paths) {
    return {
      ok: false,
      code: 'DENIED_PATH',
      message: 'G8: changed paths could not be established',
    };
  }
  const hit = paths.find(pathIsDenied);
  if (hit) {
    return {
      ok: false,
      code: 'DENIED_PATH',
      message: `G8: diff touches denied path (${hit})`,
    };
  }
  return { ok: true };
};

registerPublishGuard('G8', g8DeniedPaths);
