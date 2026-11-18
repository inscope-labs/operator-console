/**
 * Codec unit tests — LF framing, field order, validation.
 */

import { describe, it, expect } from 'vitest';
import {
  encodeCapability,
  decodeCapability,
  splitSignedCapability,
} from '../../../src/capability/codec';
import type { OperatorCapability } from '../../../src/capability/types';
import { CAPABILITY_VERSION } from '../../../src/capability/types';

const valid: OperatorCapability = {
  version: CAPABILITY_VERSION,
  kid: 'device_1',
  exp: 1_800_000_000,
  order: 'ord-1',
  plan: 'plan-a',
  task: 'task-42',
  op: 'publish',
  branch: 'task-42',
  head: 'a'.repeat(40),
  nonce: '7f3c9b1e5d2a7046',
};

describe('encodeCapability', () => {
  it('produces exactly 10 LF-separated fields', () => {
    const msg = encodeCapability(valid);
    const lines = msg.split('\n');
    expect(lines).toHaveLength(10);
    expect(lines[0]).toBe('ctxop-v1');
    expect(lines[1]).toBe('device_1');
    expect(lines[6]).toBe('PUBLISH');
    expect(lines[7]).toBe('task-42');
    expect(lines[8]).toBe('a'.repeat(40));
    expect(lines[9]).toBe('7f3c9b1e5d2a7046');
  });

  it('round-trips with decodeCapability', () => {
    const msg = encodeCapability(valid);
    const decoded = decodeCapability(msg);
    expect(decoded).toEqual(valid);
  });
});

describe('decodeCapability', () => {
  it('rejects wrong field count', () => {
    expect(() => decodeCapability('ctxop-v1\nonly-two')).toThrow(/BAD_FIELD_COUNT/);
  });

  it('rejects unknown version', () => {
    const lines = encodeCapability(valid).split('\n');
    lines[0] = 'ctxop-v0';
    expect(() => decodeCapability(lines.join('\n'))).toThrow(/version/);
  });

  it('rejects invalid kid', () => {
    const lines = encodeCapability(valid).split('\n');
    lines[1] = 'bad kid!';
    expect(() => decodeCapability(lines.join('\n'))).toThrow(/kid/);
  });

  it('rejects non-integer exp', () => {
    const lines = encodeCapability(valid).split('\n');
    lines[2] = 'not-a-number';
    expect(() => decodeCapability(lines.join('\n'))).toThrow(/exp/);
  });

  it('rejects UNKNOWN_OP', () => {
    const lines = encodeCapability(valid).split('\n');
    lines[6] = 'explode';
    expect(() => decodeCapability(lines.join('\n'))).toThrow(/UNKNOWN_OP/);
  });

  it('rejects BRANCH_NOT_TASK (main on a task-scoped op)', () => {
    const lines = encodeCapability(valid).split('\n');
    lines[7] = 'main';
    expect(() => decodeCapability(lines.join('\n'))).toThrow(/BRANCH_NOT_TASK/);
  });

  it('rejects BRANCH_NOT_TASK (engine branch on a task-scoped op)', () => {
    const lines = encodeCapability(valid).split('\n');
    lines[7] = 'engine';
    expect(() => decodeCapability(lines.join('\n'))).toThrow(/BRANCH_NOT_TASK/);
  });

  it('rejects BRANCH_MISMATCH (task branch on an engine-scoped op)', () => {
    const lines = encodeCapability({ ...valid, op: 'halt' }).split('\n');
    lines[6] = 'HALT';
    expect(() => decodeCapability(lines.join('\n'))).toThrow(/BRANCH_MISMATCH/);
  });

  it('rejects an unsafe engine branch', () => {
    const lines = encodeCapability({ ...valid, op: 'halt', branch: 'engine' }).split('\n');
    lines[6] = 'HALT';
    lines[7] = 'a..b';
    expect(() => decodeCapability(lines.join('\n'))).toThrow(/BAD_ENCODING/);
  });

  it('rejects short head SHA', () => {
    const lines = encodeCapability(valid).split('\n');
    lines[8] = 'abc';
    expect(() => decodeCapability(lines.join('\n'))).toThrow(/head SHA/);
  });

  it('rejects missing nonce', () => {
    const lines = encodeCapability(valid).split('\n');
    lines[9] = '';
    // An empty tenth field is wire-identical to a trailing line feed, so the
    // refusal is the encoding fault rather than the nonce rule.
    expect(() => decodeCapability(lines.join('\n'))).toThrow(/BAD_ENCODING/);
  });

  it('rejects a nonce that is not 16-64 lower-case hex', () => {
    const lines = encodeCapability(valid).split('\n');
    lines[9] = '7f3c9b1e5d2a704'; // 15 characters
    expect(() => decodeCapability(lines.join('\n'))).toThrow(/BAD_ENCODING/);
    lines[9] = '7F3C9B1E5D2A7046'; // upper case
    expect(() => decodeCapability(lines.join('\n'))).toThrow(/BAD_ENCODING/);
    lines[9] = '7f3c9b1e5d2a7046'.repeat(3); // 48 characters is fine
    expect(() => decodeCapability(lines.join('\n'))).not.toThrow();
  });

  it('rejects an upper-case head SHA', () => {
    const lines = encodeCapability(valid).split('\n');
    lines[8] = 'A'.repeat(40);
    expect(() => decodeCapability(lines.join('\n'))).toThrow(/BAD_ENCODING/);
  });

  it('rejects a non-canonical exp', () => {
    for (const exp of ['+1800000000', '01800000000', '99999999999999999999']) {
      const lines = encodeCapability(valid).split('\n');
      lines[2] = exp;
      expect(() => decodeCapability(lines.join('\n'))).toThrow(/BAD_ENCODING/);
    }
  });

  it('decodes the largest canonical exp', () => {
    const lines = encodeCapability(valid).split('\n');
    lines[2] = '9223372036854775807';
    const decoded = decodeCapability(lines.join('\n'));
    expect(decoded.exp).toBeGreaterThanOrEqual(Number.MAX_SAFE_INTEGER);
  });

  it('rejects an empty order', () => {
    const lines = encodeCapability(valid).split('\n');
    lines[3] = '';
    expect(() => decodeCapability(lines.join('\n'))).toThrow(/BAD_ENCODING/);
  });

  it('rejects a leading or trailing line feed', () => {
    const msg = encodeCapability(valid);
    expect(() => decodeCapability(`\n${msg}`)).toThrow(/BAD_ENCODING/);
    expect(() => decodeCapability(`${msg}\n`)).toThrow(/BAD_ENCODING/);
  });
});

describe('splitSignedCapability', () => {
  it('splits message and signature on last LF', () => {
    const msg = encodeCapability(valid);
    const raw = msg + '\n' + 'sigbase64url';
    const { message, signature } = splitSignedCapability(raw);
    expect(message).toBe(msg);
    expect(signature).toBe('sigbase64url');
  });

  it('rejects input with no separator', () => {
    expect(() => splitSignedCapability('nosep')).toThrow(/signature separator/);
  });
});
