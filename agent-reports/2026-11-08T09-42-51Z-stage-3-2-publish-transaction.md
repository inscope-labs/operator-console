# Agent Report: Stage 3.2 — Publish transaction

- **Stage**: GATEWAY-A-COMPLETION-PLAN — Phase 3, Stage 3.2
- **Slug**: stage-3-2-publish-transaction
- **Branch**: `stage/3-2-publish-transaction`

## What changed

### ConsumerHandle
- `compare(base, head)` for guard inputs (G5–G8, G6)
- `createPullRequest(...)` publish side-effect
- Rate headers observed into shared RateCoordinator

### `POST /tasks/:id/publish`
- Capability gate (nonce first) with lab Model A allowed outside production
- Live ref + compare → guard context
- `runWriteTransaction`: rate → guards → createPullRequest → audit
- Success body: `{ accepted, operation: PUBLISH, branch, headSha, auditRef }`
- Upload / reconcile remain 501

## Exit criteria

| Criterion | Status |
|---|---|
| Successful publish produces side-effect (mocked PR) + auditRef | ✅ |
| Refusal paths use catalogue codes | ✅ |
| No 501 remains for publish | ✅ |
