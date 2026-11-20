# Open operator decisions

Items from `OPERATOR-DECISIONS.md` (18 Nov 2026) that the operator left blank, plus conflicts and actions the
recorded answers raised. Decided items are in ADR 0011 and ADR 0017. Nothing here is decided. Delete a row in the
pull request that records its answer.

## A. Needs the operator's confirmation (a recorded answer conflicts with something)

| ID | Question | Why it is open | Blocks |
|---|---|---|---|
| B3 | Is the credential model really **(b), a fine-grained token**, and does "gateways hold no plan-specific information" mean only "no static repository list"? | The answer conflicts with `docs/gateway-credential-scope.md` (authoritative, GitHub App, "never PATs") and the adapter, and `PlanDO` stores plan records. See ADR 0011 Flags 1 and 2. | EXT-3, adapter work, `gateway-credential-scope.md` |
| B6 | Create the GitHub environments `staging` and `production` (operator as required reviewer on production). | Requested now, but Claude's token returns 403 (no Administration permission). The repository has no environments. Create them in repository settings. | Row 47's reviewer rule, any deploy |
| B6 | Worker names `ops-test` / `ops-stage` / `ops-production`. | `gateway/wrangler.toml.template` still says `name = "cbridge"`; the deploy must set the name per environment. | EXT-5, deploy |
| F6 | Revoke every PAT pasted in chat, including Claude's session token. | Answered "Today". The token was still valid when this file was written, so it is **not** recorded as done. | EXT-1 |

## B. Left blank

| ID | Question (short) | Blocks |
|---|---|---|
| B7 | Acceptance device model and Android version; window for the device run; emulator only for the first PoC run? | EXT-7, task 61 |
| C5.4 | Which wire status is `planned` (`ready`, `draft`, either)? | Gateway PR 2 source-state rule |
| C5.5 | Contract revision 1.4 with the new transition error codes, or reuse existing codes? | Gateway PR 2 refusal codes |
| C6 | Is `done` alone sufficient handoff evidence, or does Gateway A expose a seal indicator? (proposed: `done` alone) | ADR 0017 |
| D1 | Where does Plan Management Authoring sit relative to `RELEASE-PLAN.md`? (proposed: a new stage after 0.6.0) | Task 51, ADR 0018 |
| D2 | Draft limits and retention: max size, max count, deletion rule. | Task 51 |
| D3 | Authorisation model for authoring and submission per environment. | Task 51 |
| D4 | Is submission part of 0.6.0? (proposed: no) | Task 51 |
| D5 | Mapping from build-engine statuses to gateway wire statuses (six statuses). | Task 51, C5.4 |
| D6 | Confirm the three kinds of plan record (draft, repository plan, `PlanDO` projection). | Task 51 |
| E1 | Where do Lab and the HMAC signer live: debug source set, deleted, or kept behind a flag? | Task 37, the 10-entry architecture baseline |
| E2 | Permissions added by libraries at manifest merge: keep, strip `USE_FINGERPRINT` only, or strip all? | Manifest comment, `ArchitectureManifestTest` baseline |
| E3 | Who supplies colour, typography and theme? | Tasks 27 to 29 |
| E4 | Limit `PUT /plans/{id}` to `ENVIRONMENT=test`, leave it, or remove it later? | Gateway hardening |
| E5 | Nonce order: amend the contract to match the gate, or change the gate to match the contract? | Contract §3.17 wording |
| F1 | Name of the disposable test consumer repository; create it in `inscope-labs`? | EXT-2 |
| F2 | GitHub App name and scope. | Probably moot if B3 stands as (b); decide with B3 |

## C. Conflict between two answers

- B3 = (b) (token) and F2 (a GitHub App) cannot both stand. Resolve with B3.
