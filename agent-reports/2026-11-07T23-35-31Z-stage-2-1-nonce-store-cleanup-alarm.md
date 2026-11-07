# Agent Report: Stage 2.1 — NonceStore completion & cleanup alarm

- **Stage**: GATEWAY-A-COMPLETION-PLAN — Phase 2, Stage 2.1
- **Slug**: stage-2-1-nonce-store-cleanup-alarm
- **Branch**: `stage/2-1-nonce-store-cleanup-alarm`

## What changed

### `gateway/src/durable-objects/nonce-store.ts`
- Transactional `check-and-store` (storage.transaction when available).
- Refuse already-expired nonces (`CAPABILITY_EXPIRED`) without writing.
- Cleanup **alarm**: `alarm()` purges expired records and reschedules.
- `scheduleNextAlarm()` targets earliest live exp (clamped to 1h horizon).
- Manual `POST /purge-expired` retained for ops / tests.
- `GET /stats` reports live / expired counts and next alarm.

### Tests (`gateway/tests/unit/durable-objects/nonce-store.test.ts`)
- Fresh store → 200
- Replay → 409 `NONCE_REPLAYED`
- Concurrent identical nonces → exactly one 200 and one 409
- Expired nonce refused, not stored
- Alarm scheduled on store
- Purge removes expired, keeps live
- Alarm handler clears 20 expired records; does not accumulate unbounded
- Stats live/expired counts

## Exit criteria

| Criterion | Status |
|---|---|
| Concurrent identical capabilities → one success, one `NONCE_REPLAYED` | ✅ |
| Expired nonces removed by alarm; no unbounded accumulation | ✅ |
| Tests green | ✅ (8 new + existing suite) |
