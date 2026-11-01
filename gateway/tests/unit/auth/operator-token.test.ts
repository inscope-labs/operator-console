import { describe, it, expect } from 'vitest';
import {
  extractBearer,
  safeEqual,
  verifyOperatorToken,
} from '../../../src/auth/operator-token';

describe('extractBearer', () => {
  it('extracts token from Bearer header', () => {
    expect(extractBearer('Bearer secret-value')).toBe('secret-value');
    expect(extractBearer('bearer secret-value')).toBe('secret-value');
  });

  it('returns null for missing or non-Bearer', () => {
    expect(extractBearer(null)).toBeNull();
    expect(extractBearer('')).toBeNull();
    expect(extractBearer('Basic abc')).toBeNull();
    expect(extractBearer('Bearer ')).toBeNull();
  });
});

describe('safeEqual', () => {
  it('returns true for identical strings', () => {
    expect(safeEqual('abc', 'abc')).toBe(true);
  });

  it('returns false for different content or length', () => {
    expect(safeEqual('abc', 'abd')).toBe(false);
    expect(safeEqual('abc', 'ab')).toBe(false);
    expect(safeEqual('', 'a')).toBe(false);
  });
});

describe('verifyOperatorToken', () => {
  it('accepts matching Bearer token', () => {
    const req = new Request('https://example.test/status', {
      headers: { authorization: 'Bearer correct-token' },
    });
    expect(verifyOperatorToken(req, 'correct-token')).toBe(true);
  });

  it('rejects wrong or missing token', () => {
    const wrong = new Request('https://example.test/status', {
      headers: { authorization: 'Bearer wrong' },
    });
    expect(verifyOperatorToken(wrong, 'correct-token')).toBe(false);

    const missing = new Request('https://example.test/status');
    expect(verifyOperatorToken(missing, 'correct-token')).toBe(false);
  });

  it('rejects when expected token is empty (fail closed)', () => {
    const req = new Request('https://example.test/status', {
      headers: { authorization: 'Bearer anything' },
    });
    expect(verifyOperatorToken(req, '')).toBe(false);
  });
});
