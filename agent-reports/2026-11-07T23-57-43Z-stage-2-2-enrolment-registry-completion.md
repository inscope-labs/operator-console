# Agent Report: Stage 2.2 — EnrolmentRegistry completion

- **Stage**: GATEWAY-A-COMPLETION-PLAN — Phase 2, Stage 2.2
- **Slug**: stage-2-2-enrolment-registry-completion
- **Branch**: `stage/2-2-enrolment-registry-completion`

## What changed

### `gateway/src/durable-objects/enrolment-registry.ts`
- Transactional `consumeChallenge` / `enroll` / `rotate` via storage.transaction
- Challenge record **deleted** on successful consume (bytes not retained)
- Rotate atomically retires old kid and activates new kid
- Internal-only DO routes unchanged (no public admin surface)

### Tests (`enrolment-registry.test.ts` — 9 cases)
- Create + single consume; second consume → `ENROLL_CHALLENGE_INVALID`
- Expired challenge (TTL 300s) → `ENROLL_CHALLENGE_INVALID`
- Concurrent consume → one 200 + one `ENROLL_CHALLENGE_INVALID`
- Sequential enroll kids k1, k2
- Rotate retires old, activates new
- Failed rotate leaves old active
- listActive returns only active records
- **Restart survival**: new DO instance sharing the same storage map still sees enrolments and kid sequence

## Exit criteria

| Criterion | Status |
|---|---|
| Challenge reuse after success or expiry → `ENROLL_CHALLENGE_INVALID` | ✅ |
| Rotate correctly retires the previous kid | ✅ |
| Registry survives Worker restarts (DO storage semantics verified by test) | ✅ |
