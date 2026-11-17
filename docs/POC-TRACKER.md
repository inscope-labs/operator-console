# Operator Console PoC tracker

Source: `OPERATOR-CONSOLE-POC-ATOMIC-TASKS.md` (task numbers are its numbers). Opened 17 Nov 2026.

**Closing rule.** A row is `done` only when the change is merged to `main`, CI is green on that pushed SHA, and the SHA is in
the Evidence column. An agent report is a claim, not evidence. Claude ticks rows in the pull request that closes the task or in
a docs follow-up the operator merges. The AI Studio agent does not edit this file. EXT rows are ticked by the operator.

States: `todo`, `in-progress`, `blocked`, `done`, `deferred` (deferred rows also appear in `docs/DEFERRED.md`).
`(verify)` in a title means the task cannot close until that check has been run.

| ID | Lane | Task | State | Evidence / note | Closed on |
|---|---|---|---|---|---|
| 1 | APP | SetTaskStatusUseCase: SET_IN_PROGRESS only | in-progress | AI Studio, in flight | |
| 2 | APP | SetTaskStatusUseCaseTest: rejects DONE | in-progress | AI Studio, in flight | |
| 3 | APP | Remove TransitionKind.SET | in-progress | AI Studio; `4ba2c90` on main, close-out not yet confirmed | |
| 4 | APP | JournalIntentBuilder: drop SET branch | in-progress | AI Studio, in flight | |
| 5 | APP | ExistingMintingAdapter: drop SET branch | in-progress | AI Studio, in flight | |
| 6 | APP | ActConfirmingViewModel: drop SET branch | in-progress | AI Studio, in flight | |
| 7 | APP | TransitionPreconditionValidator: drop local SET rule | in-progress | AI Studio, in flight | |
| 8 | APP | TaskSummaryDto: `order` required (verify mapper) | todo |  | |
| 9 | APP | TaskSummaryDtoOrderTest (new) | todo |  | |
| 10 | APP | OperationIntent fingerprint includes decide notes (F1; locate) | todo |  | |
| 11 | APP | Idempotency-key collision returns Conflict (F2) | todo |  | |
| 12 | APP | Verify stored fingerprint before dispatch/recovery (F4) | todo |  | |
| 13 | APP | ErrorMapper: one source of gateway error codes (F3) | todo |  | |
| 14 | APP | GatewayErrorCatalogueParityTest (new) | todo |  | |
| 15 | APP | RevokeDeviceKeyUseCaseTest: replace TODO() fakes | in-progress | AI Studio, in flight | |
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
| 44 | PROT | scripts/release-check.py + CI step | todo |  | |
| 45 | PROT | build-apk-debug.yml consolidate | todo |  | |
| 46 | PROT | proguard-rules.pro: review 7fdd304 | blocked | needs operator acceptance | |
| 47 | PROT | gateway-deploy.yml: real V1-V12 gate | todo |  | |
| 48 | DOC | Delete/point ADR 0007 duplicate; fix references | todo |  | |
| 49 | DOC | ADR 0011 closed decisions (new) | blocked | needs operator answers | |
| 50 | DOC | ADR 0017 manual-pipeline approval (new) | blocked | needs EXT-10 | |
| 51 | DOC | ADR 0018 PMA decisions (new) | blocked | needs EXT-10 | |
| 52 | DOC | docs/DEFERRED.md (new) | in-progress | PR #81 | |
| 53 | DOC | docs/STATUS.md corrections + PoC rows | todo |  | |
| 54 | DOC | README status snapshot replaced | in-progress | PR #81 | |
| 55 | DOC | RELEASE-PLAN.md rev 1.3 (4 edits) | todo |  | |
| 56 | DOC | Contract: order route reserved (PR3-003) | in-progress | PR #81 | |
| 57 | DOC | PHASE-3-REMEDIATION-REGISTER rev 1.3 | todo |  | |
| 58 | DOC | MANUAL-PIPELINE-READINESS-REGISTER update | todo |  | |
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
