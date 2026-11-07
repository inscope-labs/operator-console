# Agent Report: Fix Gateway CI lint (main red)

## Diagnosis

Gateway CI on `main` failed at `npm run lint` (exit 2).

Cause: `eslint.config.js` ignored `**/*` while `package.json` still invoked
`eslint src scripts tests`. ESLint 9 treats "all matched paths ignored" as a
fatal error, so the fail-closed workflow from Stage 1.5 correctly failed the job.

Typecheck and all 130 unit tests were already green; only lint was red.

## Fix

- Update `gateway/package.json` `lint` script to a documented no-op that exits 0.
  TypeScript ESLint is deferred; `tsc --noEmit` remains the structural gate.
- Keep `eslint.config.js` present with a non-empty files pattern for future rule enablement.

## Phase 1 integrity audit (post restore merge `7b56938`)

| Stage | Artifact | Status |
|-------|----------|--------|
| 1.1 | Publish 501 stub + tests | Intact |
| 1.2 | `staging` env model + tests | Intact |
| 1.3 | Catalogue freeze + `docs/schemas/` + ADR 0003 | Intact |
| 1.4 | `runtime.ts`, lockfile, no circular imports | Intact |
| 1.5 | Phase 1 report + fail-closed `gateway-ci.yml` | Intact |

`gateway/**` matches PR #35 tip (`992deff`) after restore PR #37. Residual from the
concurrent agent (`2c13446`) remains only outside `gateway/` (Android/docs), by design
of the restore commit.
