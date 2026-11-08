# Agent Report: Stage 3.3 — Upload transaction

- **Stage**: GATEWAY-A-COMPLETION-PLAN — Phase 3, Stage 3.3
- **Slug**: stage-3-3-upload-transaction
- **Branch**: `stage/3-3-upload-transaction`

## What changed

### Validation (`upload-validate.ts`)
- Decode base64 zip, enforce `MAX_UPLOAD_BYTES` (25 MiB)
- SHA-256 integrity vs `zipSha256` (mismatch → `BAD_ENCODING`)
- Safe `submissions/{id}.zip` object path

### ConsumerHandle
- `putFile` / `pathExists` for submission storage via Contents API

### `POST /tasks/:id/upload`
- Capability gate (op must be `upload`) → rate → guards (incl. G15) → putFile → audit
- Success: `{ accepted, operation: UPLOAD, branch, headSha, auditRef }`
- Reconcile remains 501

## Exit criteria

| Criterion | Status |
|---|---|
| Upload succeeds only with valid capability + matching head | ✅ |
| Oversized / mismatched SHA refused with stable codes | ✅ `BAD_ENCODING` |
| Audit record emitted | ✅ |
