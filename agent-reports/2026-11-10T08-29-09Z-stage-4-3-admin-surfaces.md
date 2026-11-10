# Agent Report: Stage 4.3 — Admin / internal surfaces

- **Stage**: GATEWAY-A-COMPLETION-PLAN — Phase 4, Stage 4.3
- **Slug**: stage-4-3-admin-surfaces
- **Branch**: `stage/4-3-admin-surfaces`

## What changed

- `GET /admin/enrolments` — active enrolments without public keys
- `POST /admin/revoke` — confirmation `REVOKE <kid>`, audit on success
- EnrolmentRegistry `/revoke` + client helpers
- Docs: `gateway/docs/admin-surface.md`

## Exit criteria

| Criterion | Status |
|---|---|
| Admin routes require operator credential | ✅ |
| Documentation lists every admin path | ✅ |
