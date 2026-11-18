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
| 8 | APP | TaskSummaryDto: `order` required (verify mapper) | todo |  | |
| 9 | APP | TaskSummaryDtoOrderTest (new) | todo |  | |
| 10 | APP | OperationIntent fingerprint includes decide notes (F1; locate) | done | `fb040fa` — `notes` added to `OperationIntent` and to the fingerprint; test `bullet 2 DECIDE includes notes in OperationIntent so differing notes get distinct keys`; CI green run 44207403352 | 2026-11-17 |
| 11 | APP | Idempotency-key collision returns Conflict (F2) | done | `fb040fa` — a local key collision with a different fingerprint returns `Conflict(IDEMPOTENCY_KEY_REUSED)`, sends nothing; test `bullet 3 local key reuse with different fingerprint returns Conflict, sends nothing, and preserves existing row`; CI green run 44207403352 | 2026-11-17 |
| 12 | APP | Verify stored fingerprint before dispatch/recovery (F4) | done | `fb040fa` — decrypted intent is rebuilt and its fingerprint compared with the stored one (`JournalCryptoException`, fail closed) on the dispatch, lookup and recovery paths; tests `stored intentFingerprint mismatch fails closed to RecoveryRequired before dispatch and recovery` and `codec fails closed when stored intent fingerprint mismatches decrypted fields`; CI green run 44207403352 | 2026-11-17 |
| 13 | APP | ErrorMapper: one source of gateway error codes (F3) | todo |  | |
| 14 | APP | GatewayErrorCatalogueParityTest (new) | todo |  | |
| 15 | APP | RevokeDeviceKeyUseCaseTest: replace TODO() fakes | done | `4ba2c90` — 0 `TODO(` remain in the file; CI green run 44194377929 | 2026-11-17 |
| 16 | APP | SettingsViewModelTest deterministic (locate) | todo |  | |
| 17 | APP | GatewayBaseUrlStore: no gateway.invalid default | todo |  | |
| 18 | APP | GatewayEndpoints: remove gateway.invalid; update 2 tests | todo |  | |
| 19 | APP | HaltResumeScreen | todo |  | |
| 20 | APP | HaltResumeViewModel (new) | todo |  | |
| 21 | APP | AuditInspectionScreen | todo |  | |
| 22 | APP | AuditInspectionViewModel (new) | todo |  | |
| 23 | APP | DeviceManagementScreen | todo |  | |
| 24 | APP | DeviceManagementViewModel (new) | todo |  | |
| 25 | APP | AppNavHost routes for 19-24 | todo |  | |
| 26 | APP | PlanDetailScreen: SET_IN_PROGRESS entry point | todo |  | |
| 27 | APP | Color.kt tokens | todo |  | |
| 28 | APP | Type.kt typography | todo |  | |
| 29 | APP | Theme.kt wiring | todo |  | |
| 30 | APP | DeviceKeyManagerTest real assertions | todo |  | |
| 31 | APP | KeystoreSignerTest real assertions | todo |  | |
| 32 | APP | StrongBoxSupportTest real assertions | todo |  | |
| 33 | APP | OperationJournalDatabaseTest (new) | todo |  | |
| 34 | APP | OperationRecoveryWorkerTest (new) | todo |  | |
| 35 | APP | ManualPipelineArchitectureTest host-import rule | todo |  | |
| 36 | APP | ArchitectureManifestTest (new) | todo |  | |
| 37 | APP | ArchitectureTest Rule E ratchet (needs EXT-10) | blocked | waits on operator decision | |
| 38 | APP | PoCHarnessContractTest (new) | todo |  | |
| 39 | PROT | network_security_config: host + pin-set | blocked | needs EXT-6 | |
| 40 | PROT | app/build.gradle.kts: version + applicationId | blocked | needs EXT-9 | |
| 41 | PROT | version.properties (new) | todo |  | |
| 42 | PROT | android-ci: assembleRelease + kspReleaseKotlin | todo |  | |
| 43 | PROT | android-ci: emulator instrumented job | todo |  | |
| 44 | PROT | scripts/release-check.py + CI step | done | `5dcbc2a` — the gate runs in `android-ci`; green on `b0e2b92c` (run 44196957274); 33 tracked outstanding findings, 0 failures; verified to fail on a new scaffold marker, a new placeholder host and a stale allowlist entry | 2026-11-17 (PR #82) |
| 45 | PROT | build-apk-debug.yml consolidate | todo |  | |
| 46 | PROT | proguard-rules.pro: review 7fdd304 | blocked | needs operator acceptance | |
| 47 | PROT | gateway-deploy.yml: real V1-V12 gate | todo |  | |
| 48 | DOC | Delete/point ADR 0007 duplicate; fix references | done | `6006359` — the duplicate is deleted and ADR 0013 is repointed at contract §1.4; green on `b0e2b92c` (run 44196957274) | 2026-11-17 (PR #82) |
| 49 | DOC | ADR 0011 closed decisions (new) | blocked | needs operator answers | |
| 50 | DOC | ADR 0017 manual-pipeline approval (new) | blocked | needs EXT-10 | |
| 51 | DOC | ADR 0018 PMA decisions (new) | blocked | needs EXT-10 | |
| 52 | DOC | docs/DEFERRED.md (new) | done | PR #81 merged as `95a3465`; CI green run 44195246660 | 2026-11-17 |
| 53 | DOC | docs/STATUS.md corrections + PoC rows | done | `b0e2b92` — Stages 1.4, 1.5, 2.3, 3.4 and 7.1 corrected, the verified SHA recorded, the PoC row set added; green on `b0e2b92c` (run 44196957274) | 2026-11-17 (PR #82) |
| 54 | DOC | README status snapshot replaced | done | PR #81 merged as `95a3465`; CI green run 44195246660 | 2026-11-17 |
| 55 | DOC | RELEASE-PLAN.md rev 1.3 (4 edits) | done | `6a73d3e` — A.4 baseline replaced from the repository, T3-303 per ADR 0014 D4, T3-404 per ADR 0014 D2/D5 and ADR 0016 D-1, Phase 1 marked gateway-owned with the recorded result; CI green on `6a73d3e2` (run 44201395647) | 2026-11-17 (PR #83) |
| 56 | DOC | Contract: order route reserved (PR3-003) | done | PR #81 merged as `95a3465`; CI green run 44195246660 | 2026-11-17 |
| 57 | DOC | PHASE-3-REMEDIATION-REGISTER rev 1.3 | done | `ca0b361` — §2.4 records the verified state of all 26 remediation tasks at `1f949ef`; PR3-026 closed for `SET_IN_PROGRESS` only (ADR 0016 D-1/D-2, Appendix D); CI green on `6a73d3e2` (run 44201395647) | 2026-11-17 (PR #83) |
| 58 | DOC | MANUAL-PIPELINE-READINESS-REGISTER update | done | `2c0f5c4` — register re-verified at rev 1.2: LIF-02, LIF-07, LIF-08 and LIF-10 corrected and the section 2 blocker table refreshed; CI green on `6a73d3e2` (run 44201395647) | 2026-11-17 (PR #83) |
| 59 | DOC | PMA-READINESS-REGISTER: GOV-02, link ADR 0018 | todo |  | |
| 60 | DOC | DEVICE-TEST-CHECKLIST + ENVIRONMENTS (new) | todo |  | |
| 61 | DOC | PHASE-3-EVIDENCE: PoC run + ProGuard acceptance | todo |  | |
| 62 | GW | plans.ts: POST /plans/:id/status | todo |  | |
| 63 | GW | plan-status-transition.test.ts (new) | todo |  | |
| 64 | GW | tasks.ts: emit `order` | todo |  | |
| 65 | GW | operations.ts: verify lookup (verify) | todo |  | |
| 66 | GW | wrangler.toml.template + docs | todo |  | |
| EXT-1 | EXT | Revoke every PAT pasted in chat (incl. this session's) | todo |  | |
| EXT-2 | EXT | Create disposable test consumer repo | todo |  | |
| EXT-3 | EXT | Create GitHub App, test repo only | todo |  | |
| EXT-4 | EXT | v* tag ruleset + release environment (not PoC) | todo |  | |
| EXT-5 | EXT | Cloudflare test Worker, DOs, secrets | todo |  | |
| EXT-6 | EXT | Capture SPKI pins (beyond PoC) | todo |  | |
| EXT-7 | EXT | Device install, enrol, run checklist | todo |  | |
| EXT-8 | EXT | Merge/close PR #79 (open draft); #80 merged | in-progress | PR #79 open | |
| EXT-9 | EXT | Decide applicationId (BD-0.3) | todo |  | |
| EXT-10 | EXT | Operator decisions unblocking registers | todo |  | |
| EXT-11 | EXT | CAPABILITY-CONFORMANCE.md (X-2) | todo |  | |
| EXT-12 | EXT | build-engine RELEASE-PLAN header (hygiene) | todo |  | |
