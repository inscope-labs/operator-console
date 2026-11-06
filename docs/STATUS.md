# Operator Console — Status Matrix

**Authoritative project status document per Guide §10.3 and `RELEASE-PLAN.md` (Stage 0.2 / T0-202).**  
Last updated: **2026-11-06 (Stage 0.2 baseline)**.  
Reference plan of record: [`RELEASE-PLAN.md`](../RELEASE-PLAN.md).

---

## 1. Status Vocabulary (Guide §1.3)

| Term | Strict Definition |
|---|---|
| **implemented** | Code exists, adheres to contract, wire format, and rules; verified by executed tests. |
| **reference only** | Document, schema, or vector intended as architectural guidance or specification; not executable runtime code. |
| **scaffold** | Code compiles and structure exists, but carries `TODO`, placeholder logic, or mock returns. Unsafe for operational use. |
| **not started** | Required item for v0.6.0 whose code, tests, or documentation have not yet been authored. |
| **not deployed** | Code exists and passes local verification, but has not been deployed to an active Cloudflare Worker or test environment. |
| **unsupported** | Out of scope by architectural design (e.g., shell execution, on-device git worktree, broad storage access). |

> **Critical Safety Constraint**: Nothing in this repository may be described as "production ready, deployed, and supported" until the gates in Phase 7 have passed with archived evidence in `docs/release/PHASE-7-EVIDENCE.md`.

---

## 2. Complete Stage-by-Stage Status Table

| Phase / Stage | Area / Component | Implemented | Tested | Merged to main | Released | Safe to use | Notes / Evidence |
|---|---|---|---|---|---|---|---|
| **Phase 0** | **Baseline, Decisions & Hygiene** | | | | | | Gate 0 open |
| Stage 0.1 | Decisions Record (ADR 0011, OP-0.1, BD-0.5) | partially | no | yes | no | **no** | BD-0.1–0.6 confirmed; ADR 0011 drafting assigned to Claude |
| Stage 0.2 | Documentation Baseline | **implemented** | yes | yes | no | **yes** | README updated, STATUS.md created, ADR 0012 renumbered, Guide v2.1 published |
| Stage 0.3 | Hygiene & Asset Cleanup | **implemented** | yes | yes | no | **yes** | Deleted 35 redundant files in `public/assets/aistudio/*` after mapping replacements |
| **Phase 1** | **Gateway A (Cloudflare Worker)** | | | | | | Gate 1 open |
| Stage 1.1 | Fix Publish & Stubs 501 (T1-103) | **implemented** | pending CI | yes | no | **yes (fail-closed)** | Publish and stub routes return 501 NOT_IMPLEMENTED, zero 2xx on stubs, unit tests added |
| Stage 1.2 | Contract & Schema Authority | partially | yes | yes | no | **no** | Reconcile `code` vs `error`, JSON schemas, closed ops enum |
| Stage 1.3 | Guards & Publish Transaction | scaffold | unverified | yes | no | **no** | G5–G8, G11, G14, G15 stubs; transaction CAS not implemented |
| Stage 1.4 | Remaining Operations (upload, reconcile, halt, etc.) | scaffold | unverified | yes | no | **no** | Routes are stubs returning empty or unverified responses |
| Stage 1.5 | Audit, Privacy & Admin Revocation | scaffold | unverified | yes | no | **no** | `routes/admin.ts` and audit DO stream are stubs |
| Stage 1.6 | Conformance & Deployed Staging Acceptance | not deployed | unverified | no | no | **no** | Staging Worker not deployed; secrets not provisioned |
| **Phase 2** | **Android Transport & Connectivity** | | | | | | Gate 2 open |
| Stage 2.1 | Connectivity Remediation (BaseUrlStore, no init dial) | scaffold | partially | yes | no | **no** | Stage 1 of remediation merged; stages 2–8 pending |
| Stage 2.2 | Environment Safety (per-env keys & slots) | not started | no | no | no | **no** | Enrolment key namespacing by environment required |
| Stage 2.3 | Certificate Pinning & Expiry Rules | scaffold | no | yes | no | **no** | `CertificatePinning.kt` throws TODO; `gateway.invalid` in config |
| Stage 2.4 | Preflight Screen & Failure Vocabulary | scaffold | no | yes | no | **no** | Needs TLS/pinning, auth, stale, and policy failure states |
| **Phase 3** | **Privileged Operations & Durable Recovery** | | | | | | Gate 3 open |
| Stage 3.1 | Build-file Authorization (Room, WorkManager) | not started | no | no | no | **no** | Awaiting Claude PR for Room/WorkManager/encryption deps |
| Stage 3.2 | Operation Journal & GET /operations/{id} Recovery | not started | no | no | no | **no** | Pre-send intent logging and restart recovery required |
| Stage 3.3 | Guarded Publish Flow (full 40-char SHA confirmation) | scaffold | no | yes | no | **no** | `PublishTaskUseCase.kt` has scaffold markers |
| Stage 3.4 | Remaining Operations (halt, resume, upload, reconcile) | scaffold | no | yes | no | **no** | 25 app files carry TODO/SCAFFOLD placeholders |
| Stage 3.5 | Confirmation Tier Hardening (degraded mode) | **implemented** | yes | yes | no | **yes (read/minter)** | `AuthTierResolver`, `BiometricGate`, 216 unit tests passing |
| Stage 3.6 | Architecture Tests (single mint path, no exec) | **implemented** | yes | yes | no | **yes** | `ArchitectureTest` guards `.mint(` and package boundaries |
| **Phase 4** | **Operator Interface Completion** | | | | | | Gate 4 open |
| Stage 4.1 | Shell & Dashboard (data age, halt visibility) | partially | yes | yes | no | **no** | Dashboard exists; data age and offline write-blocking needed |
| Stage 4.2 | Plans & Tasks (discovery, executor classification) | partially | yes | yes | no | **no** | Read screens exist; executor classification pending |
| Stage 4.3 | Intake & Review (submission inspection, blockers) | not started | no | no | no | **no** | Multi-failure blocker categorization pending |
| Stage 4.4 | Governance & Audit Views (decision history, revoke) | not started | no | no | no | **no** | Decision history and key revocation UI pending |
| Stage 4.5 | Theme & Accessibility Polish | scaffold | partially | yes | no | **no** | `Theme.kt`, `Color.kt`, `Type.kt` carry scaffold placeholders |
| **Phase 5** | **Lab Engine** | | | | | | Gate 5 open |
| Stage 5.1 | Scenario Engine (runner, memory-only reports) | not started | no | no | no | **no** | `LabEnvironmentGuard` exists; runner not implemented |
| Stage 5.2 | Mutations & Error Coverage Catalogue | not started | no | no | no | **no** | Mutators not implemented |
| Stage 5.3 | Lab UI & Safety Banner | not started | no | no | no | **no** | Persistent warning banner and test-only gate needed |
| Stage 5.4 | Contract Self-check | not started | no | no | no | **no** | DTO drift checker not implemented |
| Stage 5.5 | Release Packaging Isolation (exclude Lab) | not started | no | no | no | **no** | Lab source set separation deferred to Stage 5.5 / 7.1 |
| **Phase 6** | **Class B Operator Workflow & cbridge Coverage** | | | | | | Gate 6 open |
| Stage 6.1 | Context Links (Gateway A issuance, status, revoke) | not started | no | no | no | **no** | Depends on build-engine BE-0 documents |
| Stage 6.2 | Submission Approval & Transfer | not started | no | no | no | **no** | Depends on Phase 3 single-path confirmation |
| Stage 6.3 | Cross-Gateway Revocation & Audit Linkage | not started | no | no | no | **no** | Cross-gateway linkage through Gateway A |
| Stage 6.4 | cbridge Coverage Matrix (row-by-row sign-off) | not started | no | no | no | **no** | Termux retirement blocked on row-by-row sign-off |
| **Phase 7** | **CI, Release Integrity & Acceptance** | | | | | | Gate 7 open |
| Stage 7.1 | CI Hardening & Release Check | scaffold | unverified | yes | no | **no** | Permissive `\|\| true` in gateway CI; release check needed |
| Stage 7.2 | Device Acceptance (Samsung Galaxy A03 Core) | not started | no | no | no | **no** | Physical device acceptance checklist pending |
| Stage 7.3 | Live End-to-End Staging Proof | not started | no | no | no | **no** | Full loop with disposable repository pending |
| Stage 7.4 | Joint Pilot with Build Engine (BE-7) | not started | no | no | no | **no** | Dependent on Gateways A, B, and C staging |
| Stage 7.5 | Security Drills (credential & key rotation) | not started | no | no | no | **no** | Key loss, credential revocation drills pending |
| **Phase 8** | **Release Candidate, Publication & Retirement** | | | | | | Gate 8 open |
| Stage 8.1 | Release Candidate 0.6.0 | not started | no | no | no | **no** | Version alignment, CHANGELOG, protocol identifier |
| Stage 8.2 | Independent Clean-Device Verification | not started | no | no | no | **no** | Clean sideload install and checksum verification |
| Stage 8.3 | Tagged Release Publication | not started | no | no | no | **no** | Gated on Operator manual authorization |
| Stage 8.4 | Termux Retirement Per Command | unsupported | no | no | no | **no** | Termux remains fallback until each row is signed |
| Stage 8.5 | Post-Release Compatibility Record | not started | no | no | no | **no** | Compatibility entry in build-engine record |

---

## 3. Deliberately Unsupported Features (By Design)

The following capabilities are excluded from the Operator Console architecture. Any PR or proposal introducing them will be rejected:

- **On-device GitHub credentials**: No PAT, OAuth token, or GitHub App secret on Android.
- **Local shell / Termux bridge**: No `Runtime.exec()`, `ProcessBuilder`, or local terminal wrappers.
- **Repository or branch creation**: `bootstrap` and `init` belong to CI or external setup tools.
- **Verification execution on phone**: `check`, `render --check`, and test execution run exclusively on CI runners.
- **Local git worktrees**: The phone operates solely via minted HTTP capabilities sent to Gateway A.
- **Persistent session tokens**: Sessions exist in-memory only; capability TTL (5 min) is the outer boundary.
- **Unbounded cloud analytics**: Third-party crash trackers with remote payload storage (e.g. Firebase Crashlytics) are removed in v0.6.0.
