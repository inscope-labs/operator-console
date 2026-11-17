# Operator Console — Atomic task list for a functional proof of concept

**Basis:** `RELEASE-PLAN.md` (rev 1.2, baseline 5 Nov 2026) read against `main` at `f62d6d2` (17 Nov 2026), `docs/STATUS.md`, `docs/gateway-contract.md` (rev 1.3 text), ADRs 0012–0016, `PHASE-3-REMEDIATION-REGISTER.md` (OC-PR3-RR-001), `MANUAL-PIPELINE-READINESS-REGISTER.md` (OC-MP-PRR-001), `PMA-READINESS-REGISTER.md` (OC-PMA-PRR-001) and the gateway source tree.
**Method:** static reading and grep only. I ran no build, test or deployment. Anything I could not confirm is marked **(verify)**.

## 0. What "functional proof of concept" means here

One operator, one device or emulator, one **local or `test`** Gateway A, one disposable consumer repository. The PoC chain is:

configure host → `/health` → preflight → enrol device key → read plans and next tasks (with `order`) → confirm and mint → `SET_IN_PROGRESS` and `publish` → journal survives a crash and recovers by lookup only → halt/resume → audit read.

Integration evidence comes from the local harness chosen in ADR 0016 D-2. Anything outside this chain is listed as **DEFER** and gets an entry in `docs/DEFERRED.md` (task 52), not code.

**Lanes** (from plan A.2 and the standing conventions): `[APP]` AI Studio, `app/**` only · `[PROT]` protected path, Claude via branch + PR, operator merges · `[DOC]` docs, either lane · `[GW]` `gateway/**`, gateway agent only (plan G-13) · `[EXT]` outside this repository.
Every `[APP]`, `[PROT]`, `[DOC]` and `[GW]` task is delivered as a full or partial change to the one target file named. None needs a shell.

## 1. Inconsistencies found (why the tasks below exist)

1. Plan A.4 still says the write use cases, journal and pinning are "scaffold / not started". `main` has them in code (STATUS.md), so the plan baseline is stale.
2. `docs/STATUS.md` is stale or overstated in places: Stages 1.4/1.5 say "not started" although `gateway-ci.yml`/`gateway-deploy.yml` and `gateway/package-lock.json` exist and `|| true` is gone from the gateway CI; Stage 7.1 repeats the `|| true` claim; Stage 2.3 says "safe to use" while `network_security_config.xml` still carries `gateway.invalid` and a commented-out pin set; the Stage 3.4 text says `DecideFrameworkUseCase` defaults `contractSchemaSettled = false`, but the code default is now `true`.
3. `docs/adr/` has **both** `0007-confirmation-tiers-and-attestation-challenge.md` and `0012-…` (T0-204 asked for a rename), and **no ADR 0011** (T0-101).
4. `README.md` still carries the 5 Nov status snapshot (write operations "scaffold / not started", pinning "scaffold").
5. `app/build.gradle.kts` is `versionName 0.1.0` / `versionCode 1`; `build-apk-debug.yml` reads a `version.properties` that does not exist; `applicationId` is the build-engine name `com.inscopelabs.buildengine` (BD-0.3 undecided).
6. `TransitionKind.SET` maps to `OperationType.SET_IN_PROGRESS` in three manual-pipeline files, so a "Set stage status" action would mint a start-task capability (MP LIF-02 finding).
7. ADR 0016 D-1 reserves `completed`/`SET_DONE`; `SetTaskStatusUseCase` and the plan's T3-404 still describe `SET_DONE` and completion evidence.
8. Gateway A has no `POST /plans/:id/status` (`plans.ts` registers GETs only) and `tasks.ts` does not emit `order`, although contract rev 1.2 §3.6 requires it. Publish therefore cannot complete end to end (`PublishOrderResolver` fails closed).
9. Contract §2 lists `POST /plans/{id}/order` with no §3 section or schema (PR3-003).
10. The plan's T3-303 still requires a "TTL expiry while the prompt is open" test; ADR 0014 D4 replaced it.
11. Open findings F1–F4 from the PR3-012 audit are recorded but unpatched.
12. `RevokeDeviceKeyUseCaseTest.kt` contains 19 `TODO()` calls in a fake; the T7-102 release check (`TODO(` pattern) would flag them.
13. Unresolved decisions that the registers say block rows: manual-pipeline transition table (Decision 5), PMA and manual-pipeline ADRs, `proguard-rules.pro` edit in `7fdd304`, ArchitectureTest Rule E ratchet findings.

---

## 2. Android app code `[APP]` — `app/**`

Path shorthand: `M/` = `app/src/main/kotlin/com/inscopelabs/buildengine/`, `T/` = `app/src/test/kotlin/com/inscopelabs/buildengine/`, `A/` = `app/src/androidTest/kotlin/com/inscopelabs/buildengine/`.

### 2.1 Existing defects to fix (PoC-critical)

1. **`M/domain/usecase/write/SetTaskStatusUseCase.kt`** — accept only `SET_IN_PROGRESS`; return `UnsupportedByContract` for `DONE`/`SET`; keep the read-back confirmation from draft PR #79. *Closes PR3-026 (SET_IN_PROGRESS only, ADR 0016 D-1).*
2. **`T/domain/usecase/write/SetTaskStatusUseCaseTest.kt`** (locate) — add the "use case rejects DONE" case (D-1 test T5) and keep the in-progress case.
3. **`M/manualpipeline/domain/model/TransitionKind.kt`** — remove `SET` (reserved by rev 1.3). *MP LIF-02.*
4. **`M/manualpipeline/data/journal/JournalIntentBuilder.kt`** — delete the `TransitionKind.SET` branch (line 15).
5. **`M/manualpipeline/data/integration/ExistingMintingAdapter.kt`** — delete the `TransitionKind.SET` branch (line 43).
6. **`M/manualpipeline/ui/act/confirming/ActConfirmingViewModel.kt`** — delete the `TransitionKind.SET` branch (line 81).
7. **`M/manualpipeline/domain/validation/TransitionPreconditionValidator.kt`** — delete the local `SET` rule (line 81); the module holds no transition table of its own (I-10).
8. **`M/core/network/dto/TaskSummaryDto.kt`** — make `order` a required non-blank field on the `GET /tasks/next` item; an item without it is not ready (contract §3.6). Also confirm the mapper to the domain `Task.order` that `PublishOrderResolver` reads **(verify)**. *PR3-021 open item (1).*
9. **`T/core/network/TaskSummaryDtoOrderTest.kt`** *(new)* — fixture with and without `order`; missing `order` fails closed.
10. **OperationIntent declaration** (not found under that filename; likely in `M/data/repository/OperationRepositoryImpl.kt` or the journal package — **locate**) — include `decide` `notes` in the intent fingerprint. *PR3-012 F1.*
11. **`M/data/repository/OperationRepositoryImpl.kt`** — a local idempotency-key collision with a different fingerprint returns `Conflict`, not `RecoveryRequired`. *PR3-012 F2.*
12. **`M/data/repository/OperationRepositoryImpl.kt`** — verify the stored fingerprint against the decrypted fields before dispatch and before recovery; mismatch fails closed. *PR3-012 F4.*
13. **`M/core/network/ErrorMapper.kt`** — one source of truth for gateway error codes (today `KNOWN_GATEWAY_CODES` in the repository and the mapper's own list); add the codes the contract freezes. *PR3-012 F3.*
14. **`T/core/network/GatewayErrorCatalogueParityTest.kt`** *(new)* — assert the app's code set equals `docs/schemas/error.schema.json`'s enum.
15. **`T/domain/usecase/enrollment/RevokeDeviceKeyUseCaseTest.kt`** — replace the 19 `TODO()` fake bodies with `error("not used in this test")` so the `TODO(` release check stays meaningful.
16. **`T/ui/settings/SettingsViewModelTest.kt`** (locate) — make `saveCredential and clearCredential update state` deterministic (drive the test scheduler instead of the virtual-clock timing). *PMA TST-03.*
17. **`M/data/local/GatewayBaseUrlStore.kt`** — stop using `gateway.invalid` as a live default; "not configured" is `null`. Release check T7-102 forbids the dummy host.
18. **`M/core/network/GatewayEndpoints.kt`** — same: remove the `gateway.invalid` literal; update `T/core/network/GatewayEndpointsConfigTest.kt` and `T/data/repository/GatewayCallsTransportTest.kt` to use a test-only host constant.

### 2.2 Unimplemented UI that the PoC chain needs

19. **`M/ui/operations/HaltResumeScreen.kt`** — replace the `TODO(Stage 4.4 / T4-401)`; connect to `HaltUseCase`/`ResumeUseCase`; resume requires the recorded cause; remove both markers (G-09).
20. **`M/ui/operations/HaltResumeViewModel.kt`** *(new)* — state, confirmation tier display, outcome banner.
21. **`M/ui/audit/AuditInspectionScreen.kt`** — complete the screen against `GET /audit` as the contract defines it; remove markers.
22. **`M/ui/audit/AuditInspectionViewModel.kt`** *(new)* — read path, redacted rendering, `auditRef` display.
23. **`M/ui/device/DeviceManagementScreen.kt`** — device list and revoke with typed confirmation (BD-1.10); remove markers.
24. **`M/ui/device/DeviceManagementViewModel.kt`** *(new)* — list/revoke state; wire `NeedsReenrollment` from `RevokeDeviceKeyUseCase`. *PR3-032.*
25. **`M/ui/navigation/AppNavHost.kt`** — register routes for tasks 19–24; no Lab route in a release variant.
26. **`M/ui/plans/PlanDetailScreen.kt`** — add the single entry point for `SET_IN_PROGRESS` (PR3-026 "UI entry point"); show the documented alternative for work-order creation (already present; confirm).
27. **`M/ui/theme/Color.kt`** — real colour tokens, remove marker (T4-501).
28. **`M/ui/theme/Type.kt`** — real typography, remove marker.
29. **`M/ui/theme/Theme.kt`** — wire 27–28, remove marker.

### 2.3 Tests and runtime evidence

30. **`A/crypto/DeviceKeyManagerTest.kt`** — real Keystore assertions; remove `SCAFFOLD PLACEHOLDER` (or delete per T5-503).
31. **`A/crypto/KeystoreSignerTest.kt`** — real sign-and-verify assertions.
32. **`A/crypto/StrongBoxSupportTest.kt`** — real posture assertions.
33. **`A/data/local/OperationJournalDatabaseTest.kt`** *(new)* — Room + Keystore AES-GCM round trip, AAD binding, corrupt-record fail-closed. *PR3-040.*
34. **`A/data/worker/OperationRecoveryWorkerTest.kt`** *(new)* — unique work, lookup-only, no resend. *PR3-041.*
35. **`T/manualpipeline/ManualPipelineArchitectureTest.kt`** — add the host-to-feature import rule (host imports only `manualpipeline.public`). *MP API-01, TST-08.*
36. **`T/ArchitectureManifestTest.kt`** *(new)* — merged manifest requests exactly `INTERNET` and `USE_BIOMETRIC`, `allowBackup=false`. *MP BLD-04.*
37. **`T/ArchitectureTest.kt`** — record the Rule E ratchet decision once the operator answers (task 62); do not widen the baseline silently.
38. **`T/data/repository/PoCHarnessContractTest.kt`** *(new)* — drives `SET_IN_PROGRESS` and `publish` against recorded contract vectors from `docs/fixtures/vectors/`; this is the PoC integration evidence under ADR 0016 D-2.

---

## 3. Protected paths `[PROT]` — Claude, branch + PR, operator merges

39. **`app/src/main/res/xml/network_security_config.xml`** — replace `gateway.invalid` with the deployed host; add the `<pin-set>` with expiry (BD-2.2: ≥180 days) and a backup pin. *Needs EXT-6.* Until then, the PoC runs on the debug overlay (`app/src/debug/res/xml/network_security_config.xml`, cleartext to `10.0.2.2`/`localhost` only), whose `gateway.invalid` entry also goes.
40. **`app/build.gradle.kts`** — read version from `version.properties`; decide `applicationId` per BD-0.3 *(decision EXT-9)*.
41. **`version.properties`** *(new)* — `0.6.0` / code; fixes the missing file that `build-apk-debug.yml` reads. *T7-103.*
42. **`.github/workflows/android-ci.yml`** — add `:app:assembleRelease` and `:app:kspReleaseKotlin` steps. *MP BLD-01, DI-03.*
43. **`.github/workflows/android-ci.yml`** — add an emulator instrumented-test job. *PR3-043, TST-03.*
44. **`scripts/release-check.py`** *(new, plain file)* + **`android-ci.yml`** step — fail on `TODO(`, `SCAFFOLD`, `gateway.invalid`, empty/near-expiry pin set, cleartext, Lab/HMAC in release, debug keystore. *T7-102.*
45. **`.github/workflows/build-apk-debug.yml`** — consolidate with `android-ci.yml` or document the split; drop the unneeded `continue-on-error`. *T7-104.*
46. **`app/proguard-rules.pro`** — Claude review of the `7fdd304` edit; either accept in writing (task 61) or re-land via PR. *PR3-002.*
47. **`.github/workflows/gateway-deploy.yml`** — replace the line-33 `TODO: run V1–V12` with a real gate and a required-reviewer environment. *(Gateway-owned; listed because production promotion is blocked on it. Not PoC-critical for a `test` Worker.)*

---

## 4. Documentation and registers `[DOC]`

48. **`docs/adr/0007-confirmation-tiers-and-attestation-challenge.md`** — delete (or reduce to a pointer to ADR 0012); fix any remaining references in `device-enrollment.md`, `threat-model.md`, `gateway-contract.md`, ADR 0006. *T0-204.*
49. **`docs/adr/0011-release-plan-closed-decisions.md`** *(new)* — closed decisions BD-0.1…0.6, BD-3.1–3.3 from the operator's answers. *T0-101.*
50. **`docs/adr/0017-manual-pipeline-approval.md`** *(new)* — approval of the module, `SET_DONE` deferral (links ADR 0016), archive-building choice, Decisions 1–7. *MP "no ADR" blocker; needs EXT-10.*
51. **`docs/adr/0018-pma-decisions.md`** *(new)* — draft schema, API contract, size limits, retention, authorisation, stage mapping. *PMA GOV-01, GOV-03, DAT-05, SEC-03; needs EXT-10.*
52. **`docs/DEFERRED.md`** *(new)* — mTLS stub, Play Integrity, `completed`/`SET_DONE`, `SET`, work-order creation, PMA submission, Lab, decision history, submission review, persisted cache, each with its deferring ADR. *T5-502.*
53. **`docs/STATUS.md`** — correct the rows listed in §1 item 2; add the PoC row set; stop saying "HEAD SHA unverified".
54. **`README.md`** — replace the 5 Nov snapshot table (lines ~132–142) with a pointer to `docs/STATUS.md` and current vocabulary words. *T0-201.*
55. **`RELEASE-PLAN.md`** — rev 1.3: (a) replace the A.4 baseline table; (b) T3-303 per ADR 0014 D4; (c) T3-404 per ADR 0014 D2/D5 and ADR 0016; (d) mark Phase 1 as owned by the gateway plan with the recorded result. Four separate edits to one file.
56. **`docs/gateway-contract.md`** — mark `POST /plans/{id}/order` in the §2 table as "reserved / not supported" or add the missing §3 section and schema. *PR3-003.*
57. **`docs/release/PHASE-3-REMEDIATION-REGISTER.md`** — rev 1.3: record PR3-026 closing for `SET_IN_PROGRESS` only (ADR 0016 Appendix D) and the real status of PR3-010…PR3-033.
58. **`docs/release/MANUAL-PIPELINE-READINESS-REGISTER.md`** — update LIF-02 (after tasks 3–7), LIF-07 and the blocker table (Decision 5, ADR 0016).
59. **`docs/release/PMA-READINESS-REGISTER.md`** — mark GOV-02 as corrected; link ADR 0018.
60. **`docs/DEVICE-TEST-CHECKLIST.md`** *(new)* and **`docs/ENVIRONMENTS.md`** *(new, no secrets)* — device smoke steps for PoC; hostnames and environment names. *T7-201, BD-7.3.*
61. **`docs/release/PHASE-3-EVIDENCE.md`** — add the PoC harness run and the operator's written acceptance (or rejection) of the `7fdd304` ProGuard edit. *PR3-002, PR3-044.*

---

## 5. Gateway A in this repository `[GW]` — gateway agent only

Not Android work, but the PoC cannot complete without items 62–66. They are scheduled by `gateway/GATEWAY-A-COMPLETION-PLAN.md`, not by this plan. I did not read the gateway code deeply; its agent reports claim publish, upload, reconcile, halt/resume and decide transactions, which I did not verify.

62. **`gateway/src/routes/plans.ts`** (+ `router.ts`) — add `POST /plans/:id/status`; accept `SET_IN_PROGRESS`; refuse `SET`, `completed`, `SET_DONE` with `400 BAD_ENCODING` (rev 1.3). *ADR 0014 D3, ADR 0016.*
63. **`gateway/tests/routes/plan-status-transition.test.ts`** *(new)* — conformance tests for 62 against `docs/schemas/plan-status-transition.schema.json` (local harness, D-2).
64. **`gateway/src/routes/tasks.ts`** — emit required `order` on each `/tasks/next` item; omit tasks whose work order is unknown (contract §3.6).
65. **`gateway/src/routes/operations.ts`** — **(verify)** `GET /operations/{key}` matches ADR 0013 and returns `404 OPERATION_NOT_FOUND` for unknown keys (the MP register still lists it as missing; the 82-line file suggests it exists).
66. **`gateway/wrangler.toml.template`** → a real `wrangler.toml` is operator-held (EXT-5); the template needs `ENVIRONMENT = "test"` and Durable Object bindings documented in `gateway/docs/`.

---

## 6. External to this repository `[EXT]` — required for the PoC unless marked later

Operator / account actions (nothing here can be delivered as a file change):

- **EXT-1** Revoke every PAT ever pasted in chat, including the one supplied in this conversation (plan T0-102 / OP-0.1; risk register "Credential exposure in chat"). Create a new one only when needed and keep it out of chat (G-06).
- **EXT-2** GitHub: create the disposable **test consumer repository** with `task-fx-*` branches (OP-1.1).
- **EXT-3** GitHub: create the **GitHub App**, install it on the test repository only, record permissions in `docs/gateway-credential-scope.md` (OP-1.2, BD-0.6).
- **EXT-4** GitHub: apply the `v*` tag ruleset and the `release` environment with operator as reviewer (OP-0.2, BD-0.5). Not needed to *run* the PoC; needed before any release.
- **EXT-5** Cloudflare: create the `test` Worker and Durable Objects, set `ENVIRONMENT=test`, load secrets (GitHub App credential, attestation root fingerprints OP-1.3, operator credential); deploy with `wrangler`. This is the shell step — operator or a Class A agent.
- **EXT-6** Capture the SPKI pins for the deployed host and a backup pin (OP-2.1 / T2-301). Claude supplies the exact commands; the operator runs them. Needed only to close task 39 and Gate 2.
- **EXT-7** Device: install the debug build on the emulator or the Galaxy A03 Core, enter the operator credential in Settings (never in chat), enrol, and run the checklist from task 60 (Stage 2.4 / 3.2 smoke runs).
- **EXT-8** Merge or close PR #79 (PR3-026 client) and PR #80 (ADR 0016, contract rev 1.3); approve the stacked changes before the AI Studio prompts for tasks 1–2 are issued.
- **EXT-9** Decide `applicationId` (BD-0.3) before any enrolment you want to keep: changing it later is a different app and needs re-enrolment.
- **EXT-10** Operator decisions that unblock register rows: manual-pipeline approval and archive-building choice; Decision 5 (one transition table); PMA ADR items; Rule E ratchet baseline findings; acceptance of the `7fdd304` ProGuard edit; BD-7.2 Sev-1 definition.

Build-engine repository (`inscope-labs/build-engine`):

- **EXT-11** `CAPABILITY-CONFORMANCE.md` (X-2) — the contract authority that BD-1.3 says may supersede the console's reduction. Needed to close PR3-003 for operations beyond contract v1.
- **EXT-12** X-1: reconcile the build-engine `RELEASE-PLAN.md` header (still "VERSION 0.5.0 (unreleased)"). Hygiene; not PoC-critical.
- **Not needed for the PoC chain (later):** X-3 protocol id `build-engine/0.6`, X-4 DX-1/DX-2, X-5 production-on-`workers.dev` gate, X-6 BE-7 joint pilot and BE-8 compatibility record, `COMPATIBILITY-MATRIX.md`, `CBRIDGE-COVERAGE-MATRIX.md`, Gateways B/C staging, BE-MP-12 seal route for `SET_DONE`, the plan-number allocator route and the plan-materialising workflow (PMA SUB-01/SUB-02), release signing key (OP-7.1), clean-device verification (Stage 8.2), Termux retirement signatures (Stage 8.4).

AI Studio / agent-process steps (outside the repo, per standing conventions): authorise one stage per run, have the agent pull `main` first, and require the `agent-reports/<UTC>-<slug>.md` report; Claude confirms CI is green on the pushed SHA before a stage is called closed (G-04).

---

## 7. Suggested order

1. EXT-1, EXT-8 · tasks 3–7, 1–2, 15–18 (small, safe, remove false behaviour)
2. Tasks 48–49, 53–55 (documentation matches reality)
3. Tasks 62–65 `[GW]` and 8–9 `[APP]` (so `order` and status exist on both sides)
4. EXT-2, EXT-3, EXT-5, then tasks 38, 19–26 and 30–34
5. EXT-7 device run, tasks 60–61 (evidence)
6. Tasks 39–47 and EXT-6 only when moving beyond the PoC

## 8. What I did not verify

No build, test run, CI result or deployment was inspected, so every "implemented" status above is the repository's own claim. I did not read the gateway transaction code beyond route registration, did not confirm whether `GET /operations/{key}` is complete, and did not check whether the supersession banners on `docs/V0.6.0-BUILD-PLAN.md` and `docs/console-connectivity-remediation.md` (T0-203) were added.
