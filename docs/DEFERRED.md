# Deferred items

Everything listed here is deliberately **not** part of the proof-of-concept chain. Each item names the record that defers it.
Where no ADR exists yet, the row says so and names the task that will create it; it is not given a decision it does not have.

Status words follow `README.md`: **not started**, **scaffold**, **unsupported**.

| Item | Status | Deferred by | Notes |
|---|---|---|---|
| mTLS client certificates (transport stub) | not started | `docs/adr/0006-mtls-deferred.md` (supersedes the mTLS requirement of `0003-mtls-to-gateway.md`) | Transport is certificate pinning plus operator bearer plus device-signed capability. |
| Play Integrity attestation (`DeviceAttestation.requestAttestation`) | scaffold | `docs/adr/0006-mtls-deferred.md` | Needs a published Play listing; unavailable on emulators and pre-release devices. |
| `completed` status and capability op `SET_DONE` | unsupported | `docs/adr/0016-contract-revision-1-3-completion-deferred.md` (D-1, Option A) | Reserved until the seal route and the evidence rule are decided (assessment Decisions 1, 4). |
| `SET` transition and capability op `SET` | unsupported | `docs/adr/0016-contract-revision-1-3-completion-deferred.md` | Reserved by contract revision 1.3; the console mints only `SET_IN_PROGRESS`. |
| Work-order creation (`POST /plans/{id}/order`, `CreateWorkOrderUseCase`) | unsupported | `docs/adr/0014-phase-3-operator-decisions.md` D5 (BD-1.3 Option B) | Engine tooling creates work orders. Contract §2 marks the route reserved. |
| Plan Management Authoring submission (SUB) | not started | No ADR yet; ADR 0018 (PoC task 51) is to record it | See `docs/release/PMA-READINESS-REGISTER.md`. The draft editor stays local. |
| Lab (test harness mode) | not started | `docs/adr/0004-lab-mode.md` defines it; no ADR defers it | Lab code and its HMAC signer must not appear in release builds (README). |
| Decision history view | not started | No ADR yet | `docs/STATUS.md`, Stage 4.4. |
| Submission review | not started | No ADR yet | `docs/STATUS.md`, Stage 6.2. |
| Persisted cache of gateway reads | not started | No ADR yet | The session is in memory by design (README, deliberate absences). |
| Capability conformance document (`CAPABILITY-CONFORMANCE.md`, EXT-11) | not started | ADR 0011 O-5 | Deferred past the PoC; the vectors in `docs/fixtures/vectors` and `docs/capability-format.md` stand in. |

When an item gains a deferring ADR, replace its "No ADR yet" cell with the path. When an item is built, delete its row in the
same pull request.
