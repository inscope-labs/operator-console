import { describe, it, expect } from 'vitest';
import {
  ALL_ERROR_CODES,
  HTTP_STATUS,
  ERROR_MESSAGE,
  type ErrorCode,
} from '../../../src/errors/catalogue';

describe('error catalogue freeze (Stage 1.3)', () => {
  it('HTTP_STATUS and ERROR_MESSAGE cover every ErrorCode', () => {
    for (const code of ALL_ERROR_CODES) {
      expect(HTTP_STATUS[code as ErrorCode]).toBeTypeOf('number');
      expect(ERROR_MESSAGE[code as ErrorCode].length).toBeGreaterThan(0);
    }
  });

  it('contains codes required by the contract and Stage 1.3 exit criteria', () => {
    const required: ErrorCode[] = [
      'OPERATOR_AUTH',
      'DEVICE_NOT_ENROLLED',
      'UNKNOWN_KID',
      'BAD_SIGNATURE',
      'TTL_TOO_LONG',
      'UNKNOWN_OP',
      'BRANCH_MISMATCH',
      'HEAD_MOVED',
      'NONCE_REPLAYED',
      'CAPABILITY_EXPIRED',
      'BAD_FIELD_COUNT',
      'BAD_ENCODING',
      'HALT_ACTIVE',
      'CONFIG_INVALID',
      'ENVIRONMENT_MISMATCH',
      'ENROLL_CHALLENGE_INVALID',
      'ENROLL_ATTESTATION_INVALID',
      'AUTH_TIER_NOT_ALLOWED',
      'NOT_IMPLEMENTED',
    ];
    for (const code of required) {
      expect(ALL_ERROR_CODES).toContain(code);
    }
  });

  it('NOT_IMPLEMENTED maps to HTTP 501', () => {
    expect(HTTP_STATUS.NOT_IMPLEMENTED).toBe(501);
  });

  it('does not use 2xx for any catalogue code', () => {
    for (const code of ALL_ERROR_CODES) {
      const status = HTTP_STATUS[code as ErrorCode];
      expect(status).toBeGreaterThanOrEqual(400);
      expect(status).toBeLessThan(600);
    }
  });
});
