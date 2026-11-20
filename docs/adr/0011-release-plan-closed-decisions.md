# ADR 0011 — Release plan closed decisions

| | |
|---|---|
| **Status** | Accepted (takes effect when this PR is merged), with the flags below |
| **Date** | 2026-11-18 |
| **Decision IDs** | BD-0.1 to BD-0.6, BD-3.1 to BD-3.3, BD-7.1, BD-7.2, BD-7.3 (partly); PoC boundary; operational decisions O-1 to O-5 |
| **Related** | `RELEASE-PLAN.md`, `docs/OPEN-OPERATOR-DECISIONS.md`, ADR 0010, ADR 0012, ADR 0014, ADR 0016, `docs/ENVIRONMENTS.md` |

---

## Context

`RELEASE-PLAN.md` left a set of decisions to the operator. The operator (John Wendell) answered them in
`OPERATOR-DECISIONS.md` on 2026-11-18, and this ADR records those answers as given. Where an answer
conflicts with an existing document or with the code, the conflict is stated under **Flags** and nothing is
changed here. Decisions the operator left blank are not recorded as decided; they are in
`docs/OPEN-OPERATOR-DECISIONS.md`.

## Decisions

**PoC boundary (A1).** The PoC runs *read next task, mark in progress, publish, look up an uncertain
operation* on the `test` environment only (local gateway, emulator or one device, a disposable consumer
repository). No `staging` or `production` deployment is in scope. `SET_DONE`, work-order creation, Plan
Management Authoring submission and mTLS stay deferred as listed in `docs/DEFERRED.md`.

**BD-0.1 — Repository visibility (A2).** The repository is intentionally public. No secret and no
account-identifying value (Worker account subdomain, tokens, App credentials) is committed or published in docs.

**BD-0.2 — Authoritative plan (A3).** `RELEASE-PLAN.md` at the repository root is the authoritative plan.
`docs/V0.6.0-BUILD-PLAN.md` is historical.

**BD-0.3 — Android `applicationId` (B1).** Keep `com.inscopelabs.buildengine`. The operator's reason: the
console is a 100 % component of the Build Engine System and its only operator-facing component. Debug builds
keep their `.debug` suffix. No re-enrolment is caused by this decision.

**BD-0.4 — AI Studio upload copies (B2).** Keep everything under `public/assets/aistudio/`. The operator will
ask AI Studio whether any of it matters to the Studio project; AI Studio is the only first-class member of
this repository. No deletion is authorised by this ADR.

**BD-0.5 — Release provenance with an unprotected `main` (A4).** The operator accepts, in writing, the
residual risk of an unprotected `main` (ADR 0010, so the AI Studio agent is never locked out), with these
compensating controls: a `v*` tag ruleset; releases only from a tag on a CI-green SHA; a GitHub `release`
environment with the operator as required reviewer. **None of those controls exists today** (see O-3).
Accepting the risk does not create them.

**BD-0.6 — Gateway GitHub credential model (B3).** The operator answered **(b), a fine-grained token held as a
Worker secret**, and: Gateway A learns which consumer repository a plan uses through *each plan's own
registry*. Gateways must hold no plan-specific information; that information is provided directly by each
plan. This is recorded as answered and is flagged below (Flags 1 and 2) because it conflicts with an
authoritative document and the current code.

**BD-3.1 — Confirmation tier policy (B4).** Option (a): staging and production accept both tiers and the
gateway records the tier used. `decide` and `resume` require `hardware_confirm` when the device can provide it.
A missing biometric never stops the app from reading.

**BD-3.2 — Operation journal (A5).** After a send, the journal stores no signed capability, signature or
nonce. Recovery is by `GET /operations/{id}` lookup. Encryption is Room plus Keystore-wrapped AES-GCM field
encryption. This records what `OperationIntent` and `JournalFieldCipher` already enforce (ADR 0009).

**BD-3.3 — Build-file authorisation (A6).** The build-file changes already merged (Room, WorkManager, field
encryption, manifest and ProGuard edits, no Play Integrity dependency in the build) are ratified as authorised.

**BD-7.1 — Release signing key custody (B5).** Option (a): the keystore is held as a GitHub `release`
environment secret; the APK is published as a GitHub Release and sideloaded. The operator generates and backs
up the key offline. The key is never given to an agent.

**BD-7.2 — Severity-1 (A7).** "Zero open" means no open item of this kind: any bypass of capability, guard or
halt; any false success report; any credential or capability leak; any data loss in the journal.

**BD-7.3 — Gateway A deployment identity (B6, partly).** Worker names: `test` = `ops-test`, `staging` =
`ops-stage`, `production` = `ops-production`. Hostnames are not yet recorded; they are fixed at first
deployment (ADR 0010 decision 2). The operator asked for GitHub environments `staging` and `production`, with
the operator as required reviewer on production, to be created now; see O-3 for why that has not happened.

**ProGuard rules, task 46 (A9).** Accepted **only after a release build has run on a device** (option b). The
`7fdd304` edit and the one `-dontwarn com.google.errorprone.annotations.**` line (merged in #88) stay
provisional until then. The release build, including R8, compiles in CI; nothing has run a release build on a
device. Task 46 stays blocked on that device run (EXT-7, task 61).

**Target version (A10).** The target release is `0.6.0`, matching `version.properties`. The `build-engine`
`RELEASE-PLAN.md` header ("VERSION 0.5.0 (unreleased)") is changed to `0.6.0 (unreleased)`.

## Operational decisions

**O-1 — Disagreeing plan ids (E6).** If the path plan id, the body `plan` and the capability's `plan`/`task`
disagree, the gateway refuses with `400 BAD_ENCODING`. The contract did not name this case.

**O-2 — `ENVIRONMENT` in the Worker template (E7).** `ENVIRONMENT = "test"` may be carried in
`gateway/wrangler.toml.template`, as a non-secret deployment variable. Not yet implemented.

**O-3 — Tag ruleset, `release` environment and deployment environments (F3, B6).** The `v*` ruleset and the
`release` environment are applied before the first release, not now. The `staging` and `production`
environments were requested now. The PAT used by Claude cannot create environments (GitHub returns 403:
no Administration permission), so **the operator must create them** in the repository settings, with
themselves as required reviewer on `production`. At the time of this ADR the repository has no environments.

**O-4 — SPKI pins (F4).** Captured at first deployment, when a host exists (task 39, EXT-6).

**O-5 — Capability conformance document (F5).** `CAPABILITY-CONFORMANCE.md` (EXT-11) is deferred past the PoC.

**Contract rows (E8, authorised).** Contract §2 is corrected so the status-transition row lists only
`set_in_progress`, as revision 1.3 and ADR 0016 require. Done in this PR.

## Flags (conflicts the operator should confirm)

1. **BD-0.6 contradicts `docs/gateway-credential-scope.md`.** That document is marked authoritative (T1-205,
   BD-0.6), names a GitHub App installation, and says Gateway A never uses personal access tokens. The Gateway A
   adapter implements the App flow (`GITHUB_APP_ID`, private key, installation id). The operator's answer was
   "B", read here as option (b), the fine-grained token. **No document or code is changed by this ADR.** If (b)
   stands, the credential-scope document and the adapter change, and EXT-3 (create a GitHub App) is no longer
   needed. A fine-grained token cannot be scoped per environment the way a per-environment App installation can;
   the operator should accept that explicitly. Until confirmed, this decision is **not implemented**.
2. **"Gateways must hold no plan-specific information."** Gateway A currently keeps plan records (status,
   tasks, order) in the `PlanDO` Durable Object. This sentence needs to be reconciled with that design: it may
   mean only "no static repository list in configuration". The operator should say which.
3. **Worker name.** `gateway/wrangler.toml.template` has `name = "cbridge"`, not the three names above. The
   deploy must set the name per environment. Not changed here.

## Consequences

- Task 49 (this ADR) is satisfied once this PR merges. Task 40 is unblocked: keep the `applicationId`, read
  the version from `version.properties`. EXT-9 is decided.
- BD-7.4 (acceptance device window) and BD-0.6's two flags remain open.
