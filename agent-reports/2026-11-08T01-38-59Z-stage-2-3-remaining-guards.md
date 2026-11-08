# Agent Report: Stage 2.3 — Remaining guards (G5–G8, G11, G14, G15)

- **Stage**: GATEWAY-A-COMPLETION-PLAN — Phase 2, Stage 2.3
- **Slug**: stage-2-3-remaining-guards
- **Branch**: `stage/2-3-remaining-guards`

## What changed

Implemented and registered every remaining publish guard:

| Guard | Code | File |
|-------|------|------|
| G5 Ancestry | `ANCESTRY_FAIL` | `g5-ancestry.ts` |
| G6 Non-empty delta | `DELTA_EMPTY` | `g6-non-empty-delta.ts` |
| G7 No merge commits | `MERGE_COMMITS` | `g7-no-merge-commits.ts` |
| G8 Denied paths | `DENIED_PATH` | `g8-denied-paths.ts` |
| G11 Secret patterns | `SECRET_IN_DIFF` | `g11-secret-patterns.ts` |
| G14 Halt | `HALT_ACTIVE` | `g14-halt.ts` |
| G15 Submission immutable | `SUBMISSION_IMMUTABLE` | `g15-submission-immutable.ts` |

Pipeline order (via `register-core.ts`):  
**G1 → G3 → G4 → G5 → G6 → G7 → G8 → G11 → G13 → G14 → G15**

- `docs/guard-set.md` rewritten — no longer a placeholder
- Unit tests for pass and every distinct failure mode (`remaining.test.ts`)

## Exit criteria

| Criterion | Status |
|---|---|
| `docs/guard-set.md` documents every guard G1–G15 with status | ✅ |
| No guard remains a no-op or TODO | ✅ |
| Tests prove each guard can independently refuse a request | ✅ |
