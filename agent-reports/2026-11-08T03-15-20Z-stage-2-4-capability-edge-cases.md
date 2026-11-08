# Agent Report: Stage 2.4 — Capability verification edge cases

- **Stage**: GATEWAY-A-COMPLETION-PLAN — Phase 2, Stage 2.4
- **Slug**: stage-2-4-capability-edge-cases
- **Branch**: `stage/2-4-capability-edge-cases`

## What changed

### codec / verify
- `decodeCapability` throws `BAD_FIELD_COUNT` / `BAD_ENCODING` prefixes (CR rejected).
- `verifyCapability` maps those to catalogue codes; injectable `ctx.now` for clock skew.
- **Model B default:** `ArrayBuffer` HMAC keys refused unless `allowModelA === true`.

### gate
- Maps `BAD_FIELD_COUNT` and `BAD_ENCODING` into the public error catalogue.

### Tests (`edge-cases.test.ts`)
Every capability-section catalogue code exercised:
`UNKNOWN_KID`, `BAD_SIGNATURE`, `TTL_TOO_LONG`, `CAPABILITY_EXPIRED`, `UNKNOWN_OP`,
`BRANCH_MISMATCH`, `BRANCH_INVALID`, `BAD_FIELD_COUNT`, `BAD_ENCODING`,
`HEAD_MOVED` (via G13), `NONCE_REPLAYED` (via nonce helper).

Clock-skew boundaries (±10 s) use injectable `now`.

## Exit criteria

| Criterion | Status |
|---|---|
| Every capability-section catalogue code exercised by a test | ✅ |
| Clock-skew tests use injectable clock | ✅ |
