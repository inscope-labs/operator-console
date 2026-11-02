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
  nonce: 'nonce-abc',
};

describe('encodeCapability', () => {
  it('produces exactly 10 LF-separated fields', () => {
    const msg = encodeCapability(valid);
    const lines = msg.split('\n');
    expect(lines).toHaveLength(10);
    expect(lines[0]).toBe('ctxop-v1');
    expect(lines[1]).toBe('device_1');
    expect(lines[6]).toBe('publish');
    expect(lines[7]).toBe('task-42');
    expect(lines[8]).toBe('a'.repeat(40));
    expect(lines[9]).toBe('nonce-abc');
  });

  it('round-trips with decodeCapability', () => {
    const msg = encodeCapability(valid);
    const decoded = decodeCapability(msg);
    expect(decoded).toEqual(valid);
  });
});

describe('decodeCapability', () => {
  it('rejects wrong field count', () => {
    expect(() => decodeCapability('ctxop-v1\nonly-two')).toThrow(/field count/);
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

  it('rejects BRANCH_INVALID (main)', () => {
    const lines = encodeCapability(valid).split('\n');
    lines[7] = 'main';
    expect(() => decodeCapability(lines.join('\n'))).toThrow(/BRANCH_INVALID/);
  });

  it('rejects short head SHA', () => {
    const lines = encodeCapability(valid).split('\n');
    lines[8] = 'abc';
    expect(() => decodeCapability(lines.join('\n'))).toThrow(/head SHA/);
  });

  it('rejects missing nonce', () => {
    const lines = encodeCapability(valid).split('\n');
    lines[9] = '';
    expect(() => decodeCapability(lines.join('\n'))).toThrow(/nonce/);
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
