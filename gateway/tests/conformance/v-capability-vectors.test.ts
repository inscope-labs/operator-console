/**
 * Conformance: docs/fixtures/vectors — capability codec round-trip and mutations.
 *
 * Path from gateway/: ../docs/fixtures/vectors/
 * Decodes every entry of valid_capabilities.json and checks each
 * canonicalMessage round-trips.
 * Every case in codec_mutations.json is refused **with the code it names** —
 * the assertion is on the catalogue code, not on "some refusal".
 *
 * Scope note: the decoder decides every mutation vector. The one check the
 * decoder cannot make is equality with the *configured* engine branch — that
 * value is configuration, not wire format, so it is enforced where the
 * configuration is available (src/routes/halt.ts, `cap.branch !== 'engine'`).
 * The vector `valid-engine-branch-with-slash` is therefore accepted by the
 * decoder as a well-formed engine branch and refused later, at the route.
 */

import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  decodeCapability,
  encodeCapability,
} from '../../src/capability/codec';

const __dirname = dirname(fileURLToPath(import.meta.url));
const VECTORS_DIR = resolve(__dirname, '../../../docs/fixtures/vectors');

interface ValidVector {
  id: string;
  description: string;
  scope: string;
  fields: {
    domain: string;
    kid: string;
    exp: number;
    order: string;
    plan: string;
    task: string;
    op: string;
    branch: string;
    head: string;
    nonce: string;
  };
  canonicalMessage: string;
}

interface MutationVector {
  id: string;
  mutationClass: string;
  description: string;
  rawMessage: string;
  expectedErrorCode: string;
}

function loadValid(): ValidVector[] {
  const raw = readFileSync(resolve(VECTORS_DIR, 'valid_capabilities.json'), 'utf8');
  const doc = JSON.parse(raw) as { vectors: ValidVector[] };
  return doc.vectors;
}

function loadMutations(): MutationVector[] {
  const raw = readFileSync(resolve(VECTORS_DIR, 'codec_mutations.json'), 'utf8');
  const doc = JSON.parse(raw) as { vectors: MutationVector[] };
  return doc.vectors;
}

/** Map wire UPPER op to the internal lower-case form used by OperatorCapability. */
function expectedInternalOp(wireOp: string): string {
  if (wireOp === 'NEW_PLAN') return 'new';
  return wireOp.toLowerCase();
}

describe('v-capability-vectors (valid)', () => {
  const vectors = loadValid();

  for (const v of vectors) {
    it(`${v.id}: decode(canonicalMessage) and re-encode`, () => {
      if (v.id === 'valid-exp-max-long') {
        // Long.MAX_VALUE is not exactly representable as a JS number, so an
        // exact round-trip is not required — decoding must succeed and the
        // expiry must still be at or above the safe-integer floor.
        const decoded = decodeCapability(v.canonicalMessage);
        expect(decoded.exp).toBeGreaterThanOrEqual(Number.MAX_SAFE_INTEGER);
        return;
      }

      const decoded = decodeCapability(v.canonicalMessage);
      expect(decoded.version).toBe('ctxop-v1');
      expect(decoded.kid).toBe(v.fields.kid);
      expect(decoded.exp).toBe(v.fields.exp);
      expect(decoded.order).toBe(v.fields.order);
      expect(decoded.plan).toBe(v.fields.plan);
      expect(decoded.task).toBe(v.fields.task);
      expect(decoded.op).toBe(expectedInternalOp(v.fields.op));
      expect(decoded.branch).toBe(v.fields.branch);
      expect(decoded.head).toBe(v.fields.head);
      expect(decoded.nonce).toBe(v.fields.nonce);

      const reencoded = encodeCapability(decoded);
      expect(reencoded).toBe(v.canonicalMessage);
    });
  }
});

describe('v-capability-vectors (mutations)', () => {
  const vectors = loadMutations();

  for (const v of vectors) {
    it(`${v.id}: refused (expected ${v.expectedErrorCode})`, () => {
      let thrown: unknown;
      try {
        decodeCapability(v.rawMessage);
      } catch (e) {
        thrown = e;
      }
      expect(thrown).toBeDefined();
      const msg = thrown instanceof Error ? thrown.message : String(thrown);
      // The refusal must carry the code the vector names — no allowance.
      expect(msg.startsWith(v.expectedErrorCode)).toBe(true);
    });
  }
});
