# Operator Console PoC tracker

Source: `OPERATOR-CONSOLE-POC-ATOMIC-TASKS.md` (task numbers are its numbers). Opened 17 Nov 2026.

**Closing rule.** A row is `done` only when the change is merged to `main`, CI is green on that pushed SHA, and the SHA is in
the Evidence column. An agent report is a claim, not evidence. Claude ticks rows in the pull request that closes the task or in
a docs follow-up the operator merges. The AI Studio agent does not edit this file. EXT rows are ticked by the operator.

States: `todo`, `in-progress`, `blocked`, `done`, `deferred` (deferred rows also appear in `docs/DEFERRED.md`).
`(verify)` in a title means the task cannot close until that check has been run.

**2026-11-17 (PR #82).** Rows 1–7 and 15 were marked *in flight — AI Studio*, but the work had already landed in `4ba2c90` (CI green, run 44194377929); they are ticked `done` here against that SHA. Rows 52, 54 and 56 were marked *in flight — PR #81*; PR #81 is merged as `95a3465` (CI green, run 44195246660). Row 8 stays `todo`: `TaskSummaryDto.order` exists but still defaults to `""` and is not enforced non-blank, and `TaskSummaryDtoOrderTest` (row 9) does not exist.

**2026-11-17 (PR #83).** Rows 55, 57 and 58 are closed: the release plan is at revision 1.3, the Phase 3 remediation register at revision 1.3 and the manual pipeline readiness register at revision 1.2. All three were written against a repository state that has since moved.

**2026-11-17 (PR for rows 10–12).** AI Studio had already pushed rows 10, 11 and 12 to `main` as `fb040fa`; they are ticked against that SHA. The commit has no `agent-reports/<UTC>-<slug>.md`, which the standing convention requires, so the report is still owed.

**2026-11-18 (gateway Section 0, no PoC row).** `410a950` maps the upper-case wire op (`PUBLISH`, `SET_IN_PROGRESS`, `NEW_PLAN`…) to the gateway's internal names at the codec boundary only, and adds `gateway/tests/conformance/v-capability-vectors.test.ts` against `docs/fixtures/vectors`; the commit's checks, including `check`, were green. Rows 62, 63 and 64 are still `todo`.

**2026-11-18 (AI Studio rows 13, 14, 16, 17, 18).** All five were pushed as `c186ee5`, which was red: `scripts/release-check.py` (the first CI step) failed on a stale allowlist entry for the removed `BASE_URL` default, so the unit tests never ran. The gate fix is its own PR; the rows turn `done` only after `android-ci` is green on `main`. Open question for the report: `ErrorMapper.GATEWAY_CODES` holds four codes that contract §4.1 does not list (`BRANCH_INVALID`, `EXPIRED`, `HEAD_DRIFT`, `NOT_ENROLLED`), and `GatewayErrorCatalogueParityTest` demands exact equality, so it is the likely next failure.

**2026-11-18 (round 5).** Gateway PR 1 (capability strictness) is merged as #91 (`5306bd5`, merge `8ba48ca`); `Gateway CI` `check` is green there and now includes the conformance suite. Gateway PR 2 (rows 62, 63) and PR 3 (row 64) are not started. Emulator tests: the new `instrumented-tests` job exposed four stale UI tests that had never run in CI (audit-ref `assertTextContains` needed `substring = true`; `DecisionScreenTest` still expected the pre-ADR-0014 form and a default-blocked contract); they are fixed in PR #92.

**2026-11-18 (operator answers recorded).** ADR 0011, ADR 0017 and `docs/OPEN-OPERATOR-DECISIONS.md` are in PR #96. 17 items were answered, 25 left blank, and two answers conflict with existing documents (see ADR 0011 Flags).

**New finding, no PoC row.** The `manualpipeline` module still maps `TransitionKind.SET_DONE` to `OperationType.SET_DONE` and holds it back only with a local constant, `TransitionPreconditionValidator.SET_DONE_AWAITS_SEAL_ROUTE = true`, whose own KDoc says to flip it to `false` once BE-MP-12 lands. Contract revision 1.3 (ADR 0016 D-1) reserves `SET_DONE` and refuses it with `400 BAD_ENCODING`, before any seal question is reached, so that constant would mint a capability the contract reserves. This is the same shape as the `SET` defect closed in rows 3–7. It is in `app/**`, so it is the `[APP]` lane and it has no PoC row of its own; it is recorded as a finding in `docs/release/MANUAL-PIPELINE-READINESS-REGISTER.md` LIF-02 (owner: Feature Lead) and named as a follow-on in the Phase 3 register's PR3-026.

| ID | Lane | Task | State | Evidence / note | Closed on |
|---|---|---|---|---|---|
| 1 | APP | SetTaskStatusUseCase: SET_IN_PROGRESS only | done | `4ba2c90` — `DONE` rejected with `UnsupportedByContract` (ADR 0016 D-1) before any prompt or gateway call; CI green run 44194377929 | 2026-11-17 |
| 2 | APP | SetTaskStatusUseCaseTest: rejects DONE | done | `4ba2c90` — `use case rejects DONE with UnsupportedByContract before prompt or send` covers PENDING, IN_PROGRESS, DONE and UNKNOWN sources; CI green run 44194377929 | 2026-11-17 |
| 3 | APP | Remove TransitionKind.SET | done | `4ba2c90` — `SET` deleted from `TransitionKind`; CI green run 44194377929 | 2026-11-17 |
| 4 | APP | JournalIntentBuilder: drop SET branch | done | `4ba2c90`; CI green run 44194377929 | 2026-11-17 |
| 5 | APP | ExistingMintingAdapter: drop SET branch | done | `4ba2c90`; CI green run 44194377929 | 2026-11-17 |
| 6 | APP | ActConfirmingViewModel: drop SET branch | done | `4ba2c90`; CI green run 44194377929 | 2026-11-17 |
| 7 | APP | TransitionPreconditionValidator: drop local SET rule | done | `4ba2c90`; CI green run 44194377929 | 2026-11-17 |
| 8 | APP | TaskSummaryDto: `order` required (verify mapper) | done | `20e9e3a` (AI Studio): `TaskSummaryDto` requires a non-blank `order`, mapper updated; `android-ci` green | 2026-11-18 |
| 9 | APP | TaskSummaryDtoOrderTest (new) | done | `20e9e3a`: `TaskSummaryDtoOrderTest` added; `android-ci` green | 2026-11-18 |
| 10 | APP | OperationIntent fingerprint includes decide notes (F1; locate) | done | `fb040fa` — `notes` added to `OperationIntent` and to the fingerprint; test `bullet 2 DECIDE includes notes in OperationIntent so differing notes get distinct keys`; CI green run 44207403352 | 2026-11-17 |
| 11 | APP | Idempotency-key collision returns Conflict (F2) | done | `fb040fa` — a local key collision with a different fingerprint returns `Conflict(IDEMPOTENCY_KEY_REUSED)`, sends nothing; test `bullet 3 local key reuse with different fingerprint returns Conflict, sends nothing, and preserves existing row`; CI green run 44207403352 | 2026-11-17 |
| 12 | APP | Verify stored fingerprint before dispatch/recovery (F4) | done | `fb040fa` — decrypted intent is rebuilt and its fingerprint compared with the stored one (`JournalCryptoException`, fail closed) on the dispatch, lookup and recovery paths; tests `stored intentFingerprint mismatch fails closed to RecoveryRequired before dispatch and recovery` and `codec fails closed when stored intent fingerprint mismatches decrypted fields`; CI green run 44207403352 | 2026-11-17 |
| 13 | APP | ErrorMapper: one source of gateway error codes (F3) | done | `c186ee5` (AI Studio) plus gate fix #88, merge `e16bbeb`; `android-ci` green there and on `main` at `f656395` | 2026-11-18 |
| 14 | APP | GatewayErrorCatalogueParityTest (new) | done | `c186ee5` (AI Studio) plus gate fix #88, merge `e16bbeb`; `android-ci` green there and on `main` at `f656395` | 2026-11-18 |
| 15 | APP | RevokeDeviceKeyUseCaseTest: replace TODO() fakes | done | `4ba2c90` — 0 `TODO(` remain in the file; CI green run 44194377929 | 2026-11-17 |
| 16 | APP | SettingsViewModelTest deterministic (locate) | done | `c186ee5` (AI Studio) plus gate fix #88, merge `e16bbeb`; `android-ci` green there and on `main` at `f656395` | 2026-11-18 |
| 17 | APP | GatewayBaseUrlStore: no gateway.invalid default | done | `c186ee5` (AI Studio) plus gate fix #88, merge `e16bbeb`; `android-ci` green there and on `main` at `f656395` | 2026-11-18 |
| 18 | APP | GatewayEndpoints: remove gateway.invalid; update 2 tests | done | `c186ee5` (AI Studio) plus gate fix #88, merge `e16bbeb`; `android-ci` green there and on `main` at `f656395` | 2026-11-18 |
| 19 | APP | HaltResumeScreen | todo |  | |
| 20 | APP | HaltResumeViewModel (new) | todo |  | |
| 21 | APP | AuditInspectionScreen | todo |  | |
| 22 | APP | AuditInspectionViewModel (new) | todo |  | |
| 23 | APP | DeviceManagementScreen | todo |  | |
| 24 | APP | DeviceManagementViewModel (new) | todo |  | |
| 25 | APP | AppNavHost routes for 19-24 | todo |  | |
| 26 | APP | PlanDetailScreen: SET_IN_PROGRESS entry point | done | `f656395` (AI Studio); `android-ci` green | 2026-11-18 |
| 27 | APP | Color.kt tokens | blocked | design tokens: operator question E3 open (who supplies palette and type) | |
| 28 | APP | Type.kt typography | blocked | design tokens: operator question E3 open (who supplies palette and type) | |
| 29 | APP | Theme.kt wiring | blocked | design tokens: operator question E3 open (who supplies palette and type) | |
| 30 | APP | DeviceKeyManagerTest real assertions | todo |  | |
| 31 | APP | KeystoreSignerTest real assertions | in-progress | PR #92; `KeystoreSignerTest` sign/verify, tamper, kid, fail-closed; emulator job 28/28 green |  |
| 32 | APP | StrongBoxSupportTest real assertions | in-progress | PR #92; `StrongBoxSupportTest` honest-posture assertions; emulator job 28/28 green |  |
| 33 | APP | OperationJournalDatabaseTest (new) | todo |  | |
| 34 | APP | OperationRecoveryWorkerTest (new) | todo |  | |
| 35 | APP | ManualPipelineArchitectureTest host-import rule | done | PR #89, merge `e16bbeb`; host-import rule, no violations; `android-ci` green on `main` | 2026-11-18 |
| 36 | APP | ArchitectureManifestTest (new) | done | PR #89, merge `e16bbeb`; source manifest holds exactly 2 permissions, merged manifest adds 6 (ratcheted; stripping them is an operator decision) | 2026-11-18 |
| 37 | APP | ArchitectureTest Rule E ratchet (needs EXT-10) | blocked | operator question E1 open (Lab and HMAC signer: debug source set, delete, or keep behind a flag) | |
| 38 | APP | PoCHarnessContractTest (new) | todo |  | |
| 39 | PROT | network_security_config: host + pin-set | blocked | needs EXT-6 | |
| 40 | PROT | app/build.gradle.kts: version + applicationId | todo | unblocked: `applicationId` stays `com.inscopelabs.buildengine` (ADR 0011 BD-0.3); read the version from `version.properties` | |
| 41 | PROT | version.properties (new) | done | PR #84; `main` at `8f34bb2` green (including the manual APK workflow) | 2026-11-18 |
| 42 | PROT | android-ci: assembleRelease + kspReleaseKotlin | done | PR #88, merge `e16bbeb`; release KSP and unsigned release build run in `android-ci`; includes one ProGuard `-dontwarn` line (task 46 review otherwise open) | 2026-11-18 |
| 43 | PROT | android-ci: emulator instrumented job | in-progress | PR #92; `instrumented-tests` job on an API 30 emulator; 28/28 green; also fixed 4 stale UI tests that had never run in CI |  |
| 44 | PROT | scripts/release-check.py + CI step | done | `5dcbc2a` — the gate runs in `android-ci`; green on `b0e2b92c` (run 44196957274); 33 tracked outstanding findings, 0 failures; verified to fail on a new scaffold marker, a new placeholder host and a stale allowlist entry | 2026-11-17 (PR #82) |
| 45 | PROT | build-apk-debug.yml consolidate | done | PR #88, merge `e16bbeb`; manual workflow green on `main` at `8f34bb2` after the lint fix #90 | 2026-11-18 |
| 46 | PROT | proguard-rules.pro: review 7fdd304 | blocked | operator accepts the ProGuard rules only after a release build has run on a device (ADR 0011); needs EXT-7 / task 61 | |
| 47 | PROT | gateway-deploy.yml: real V1-V12 gate | in-progress | PR #89 deploy gate merged; the `staging` and `production` GitHub environments (reviewer on production) are requested but not created: Claude's token gets 403, the operator creates them | |
| 48 | DOC | Delete/point ADR 0007 duplicate; fix references | done | `6006359` — the duplicate is deleted and ADR 0013 is repointed at contract §1.4; green on `b0e2b92c` (run 44196957274) | 2026-11-17 (PR #82) |
| 49 | DOC | ADR 0011 closed decisions (new) | in-progress | ADR 0011 in PR #96; done when merged with CI green; two flags (B3 token vs GitHub App, plan-specific information) need operator confirmation | |
| 50 | DOC | ADR 0017 manual-pipeline approval (new) | in-progress | ADR 0017 in PR #96 (Decisions 1 to 4, 7, transition items 1 to 3); open: C5.4, C5.5, C6 | |
| 51 | DOC | ADR 0018 PMA decisions (new) | blocked | operator answers D1 to D6 are blank, so ADR 0018 is not written; see `docs/OPEN-OPERATOR-DECISIONS.md` | |
| 52 | DOC | docs/DEFERRED.md (new) | done | PR #81 merged as `95a3465`; CI green run 44195246660 | 2026-11-17 |
| 53 | DOC | docs/STATUS.md corrections + PoC rows | done | `b0e2b92` — Stages 1.4, 1.5, 2.3, 3.4 and 7.1 corrected, the verified SHA recorded, the PoC row set added; green on `b0e2b92c` (run 44196957274) | 2026-11-17 (PR #82) |
| 54 | DOC | README status snapshot replaced | done | PR #81 merged as `95a3465`; CI green run 44195246660 | 2026-11-17 |
| 55 | DOC | RELEASE-PLAN.md rev 1.3 (4 edits) | done | `6a73d3e` — A.4 baseline replaced from the repository, T3-303 per ADR 0014 D4, T3-404 per ADR 0014 D2/D5 and ADR 0016 D-1, Phase 1 marked gateway-owned with the recorded result; CI green on `6a73d3e2` (run 44201395647) | 2026-11-17 (PR #83) |
| 56 | DOC | Contract: order route reserved (PR3-003) | done | PR #81 merged as `95a3465`; CI green run 44195246660 | 2026-11-17 |
| 57 | DOC | PHASE-3-REMEDIATION-REGISTER rev 1.3 | done | `ca0b361` — §2.4 records the verified state of all 26 remediation tasks at `1f949ef`; PR3-026 closed for `SET_IN_PROGRESS` only (ADR 0016 D-1/D-2, Appendix D); CI green on `6a73d3e2` (run 44201395647) | 2026-11-17 (PR #83) |
| 58 | DOC | MANUAL-PIPELINE-READINESS-REGISTER update | done | `2c0f5c4` — register re-verified at rev 1.2: LIF-02, LIF-07, LIF-08 and LIF-10 corrected and the section 2 blocker table refreshed; CI green on `6a73d3e2` (run 44201395647) | 2026-11-17 (PR #83) |
| 59 | DOC | PMA-READINESS-REGISTER: GOV-02, link ADR 0018 | done | PR #84; GOV-02 PASS against `b0e2b92`; ADR 0018 still unwritten (task 51), noted not linked | 2026-11-18 |
| 60 | DOC | DEVICE-TEST-CHECKLIST + ENVIRONMENTS (new) | done | PR #84; staging/production hostnames stay unrecorded until first deployment | 2026-11-18 |
| 61 | DOC | PHASE-3-EVIDENCE: PoC run + ProGuard acceptance | todo |  | |
| 62 | GW | plans.ts: POST /plans/:id/status | todo |  | |
| 63 | GW | plan-status-transition.test.ts (new) | todo |  | |
| 64 | GW | tasks.ts: emit `order` | todo |  | |
| 65 | GW | operations.ts: verify lookup (verify) | todo |  | |
| 66 | GW | wrangler.toml.template + docs | todo |  | |
| EXT-1 | EXT | Revoke every PAT pasted in chat (incl. this session's) | todo | operator answered "Today" but the token was still valid when checked; not recorded as done | |
| EXT-2 | EXT | Create disposable test consumer repo | todo | operator question F1 blank (repo name) | |
| EXT-3 | EXT | Create GitHub App, test repo only | blocked | conflicts with BD-0.6 answer (b), a fine-grained token; resolve ADR 0011 Flag 1 first | |
| EXT-4 | EXT | v* tag ruleset + release environment (not PoC) | todo | decided: before the first release (ADR 0011 O-3) | |
| EXT-5 | EXT | Cloudflare test Worker, DOs, secrets | todo | Worker names decided: `ops-test`, `ops-stage`, `ops-production`; the template still says `cbridge` | |
| EXT-6 | EXT | Capture SPKI pins (beyond PoC) | todo | decided: capture at first deployment (ADR 0011 O-4) | |
| EXT-7 | EXT | Device install, enrol, run checklist | todo |  | |
| EXT-8 | EXT | Merge/close PR #79 (open draft); #80 merged | in-progress | PR #79 open | |
| EXT-9 | EXT | Decide applicationId (BD-0.3) | in-progress | decided: keep `com.inscopelabs.buildengine` (ADR 0011 BD-0.3); done when PR #96 merges | |
| EXT-10 | EXT | Operator decisions unblocking registers | in-progress | partly answered; the rest is in `docs/OPEN-OPERATOR-DECISIONS.md` | |
| EXT-11 | EXT | CAPABILITY-CONFORMANCE.md (X-2) | deferred | deferred past the PoC (ADR 0011 O-5; `docs/DEFERRED.md`) | |
| EXT-12 | EXT | build-engine RELEASE-PLAN header (hygiene) | in-progress | build-engine PR #71 changes the header to 0.6.0; three other `0.5.0` mentions in that file are queried in the PR | |
