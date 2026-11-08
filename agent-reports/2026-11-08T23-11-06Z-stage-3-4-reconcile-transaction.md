# Agent Report: Stage 3.4 — Reconcile transaction

- **Stage**: GATEWAY-A-COMPLETION-PLAN — Phase 3, Stage 3.4
- **Slug**: stage-3-4-reconcile-transaction
- **Branch**: `stage/3-4-reconcile-transaction`

## What changed

### ConsumerHandle
- `findOpenPullForBranch(branch)`
- `mergePullRequest({ number, commitTitle, mergeMethod })`

### `POST /tasks/:id/reconcile`
- Capability gate (`op` must be `reconcile`) → rate → guards → merge → audit
- Outcome field: `MERGED:pr-N` per contract
- No open PR / merge conflict → `RECONCILE_REQUIRED`

## Exit criteria

| Criterion | Status |
|---|---|
| Happy path and refusal paths covered | ✅ |
| Audit reference returned | ✅ |
