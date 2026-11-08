/**
 * G11 — secret patterns in diff.
 * Spec §10.11 — refuse when the diff contains token or private-key material.
 * Failure: SECRET_IN_DIFF (403).
 */

import type { Guard, GuardResult } from './index';
import { registerPublishGuard } from './index';

/** Patterns aligned with redaction gate / contract fixtures (ghp_, github_pat_, PEM keys). */
export const SECRET_PATTERNS: RegExp[] = [
  /\bghp_[A-Za-z0-9_]{20,}\b/,
  /\bgithub_pat_[A-Za-z0-9_]{20,}\b/,
  /\bgho_[A-Za-z0-9_]{20,}\b/,
  /\bghu_[A-Za-z0-9_]{20,}\b/,
  /\bghs_[A-Za-z0-9_]{20,}\b/,
  /\bghr_[A-Za-z0-9_]{20,}\b/,
  /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/,
];

export function diffContainsSecret(text: string): boolean {
  return SECRET_PATTERNS.some((re) => re.test(text));
}

export const g11SecretPatterns: Guard = async (ctx): Promise<GuardResult> => {
  // Prefer explicit diff text; also scan changed path names for obvious leaks.
  const parts: string[] = [];
  if (ctx.diffText) parts.push(ctx.diffText);
  if (ctx.branchMeta?.changedPaths) {
    parts.push(ctx.branchMeta.changedPaths.join('\n'));
  }
  if (parts.length === 0) {
    return {
      ok: false,
      code: 'SECRET_IN_DIFF',
      message: 'G11: diff text could not be established for secret scanning',
    };
  }
  const blob = parts.join('\n');
  if (diffContainsSecret(blob)) {
    return {
      ok: false,
      code: 'SECRET_IN_DIFF',
      message: 'G11: diff contains a detected secret pattern',
    };
  }
  return { ok: true };
};

registerPublishGuard('G11', g11SecretPatterns);
