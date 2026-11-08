import { describe, it, expect, beforeEach } from 'vitest';
import {
  RateCoordinator,
  resetSharedRateCoordinator,
} from '../../../src/adapter/rate-coordinator';
import { RATE_FLOOR } from '../../../src/config/constants';

describe('RateCoordinator', () => {
  beforeEach(() => {
    resetSharedRateCoordinator();
  });

  it('allows when no snapshot observed yet', () => {
    const c = new RateCoordinator();
    expect(c.check(1_700_000_000).ok).toBe(true);
  });

  it('refuses when remaining below floor before reset', () => {
    const c = new RateCoordinator(RATE_FLOOR);
    const now = 1_700_000_000;
    c.observe(RATE_FLOOR - 1, now + 3600, now);
    const r = c.check(now);
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.code).toBe('RATE_BUDGET_LOW');
  });

  it('allows when remaining at or above floor', () => {
    const c = new RateCoordinator(RATE_FLOOR);
    const now = 1_700_000_000;
    c.observe(RATE_FLOOR, now + 3600, now);
    expect(c.check(now).ok).toBe(true);
  });

  it('allows after reset time even if remaining was low', () => {
    const c = new RateCoordinator(RATE_FLOOR);
    const now = 1_700_000_000;
    c.observe(0, now - 1, now - 100);
    expect(c.check(now).ok).toBe(true);
  });

  it('observeHeaders ingests GitHub rate limit headers', () => {
    const c = new RateCoordinator();
    const h = new Headers({
      'x-ratelimit-remaining': '50',
      'x-ratelimit-reset': '1700001000',
    });
    c.observeHeaders(h, 1_700_000_000);
    const snap = c.getSnapshot();
    expect(snap?.remaining).toBe(50);
    expect(snap?.reset).toBe(1_700_001_000);
  });
});
