/**
 * CI gate: refuse forbidden secret-like substrings in gateway source
 * (excluding this script, tests fixtures, and intentional redaction modules).
 */
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

const ROOT = join(process.cwd(), 'src');

const FORBIDDEN = [
  /ghp_[A-Za-z0-9_]{20,}/,
  /github_pat_[A-Za-z0-9_]{20,}/,
  /ghs_[A-Za-z0-9_]{20,}/,
  /-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----/,
];

const SKIP_DIR = new Set(['node_modules', 'dist', '.wrangler']);
const SKIP_FILE = (rel: string) =>
  rel.includes('redaction.ts') ||
  rel.includes('g11-secret-patterns.ts') ||
  // PEM header string used to *detect* keys for stripping — not a secret.
  rel.includes('github-app-auth.ts') ||
  rel.includes('attestation/') ||
  rel.endsWith('.md');

function walk(dir: string, out: string[] = []): string[] {
  for (const name of readdirSync(dir)) {
    if (SKIP_DIR.has(name)) continue;
    const p = join(dir, name);
    const st = statSync(p);
    if (st.isDirectory()) walk(p, out);
    else if (/\.(ts|js|mjs|cjs)$/.test(name)) out.push(p);
  }
  return out;
}

const files = walk(ROOT);
const hits: string[] = [];
for (const file of files) {
  const rel = relative(process.cwd(), file);
  if (SKIP_FILE(rel)) continue;
  const text = readFileSync(file, 'utf8');
  for (const re of FORBIDDEN) {
    if (re.test(text)) {
      hits.push(`${rel} matches ${re}`);
    }
  }
}

if (hits.length) {
  console.error('verify-log-hygiene FAILED:');
  for (const h of hits) console.error('  ', h);
  process.exit(1);
}
console.log(`verify-log-hygiene OK (${files.length} files scanned)`);
