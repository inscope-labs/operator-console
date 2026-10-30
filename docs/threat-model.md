# Threat model

Residual risks, the three enforcement layers that bound them, and the checks that prove
the layers exist.

The organising constraint:

> **No operator gets full repository access — only the minted branch.**

This document is the console's half of that argument. The gateway's half — the guard set
(`guard_task()`), the `assert_private()` preflight and the durable audit stream — lives
in the gateway project and the build engine, and is referenced here only where the
console's behaviour depends on it.

---

## 1. What the device holds

| Material | Protection | Repository scope |
|---|---|---|
| Device signing keypair | Android Keystore; StrongBox where available; non-exportable | **None.** The gateway does not accept it. |
| Operator credential | Keystore-backed encrypted storage (`EncryptedSharedPreferences`) | **None.** Gateway surface only. |
| Selection state | Plain application storage | **None.** Not security state. |

The device holds **no repository-scoped credential of any kind**. That is the single most
consequential property of the design, and it is what makes the remaining risks bounded
rather than catastrophic.

---

## 2. Three independent enforcement layers

A single layer is a policy. Three layers, failing independently, make the property
structural. Each must be sufficient on its own to stop a write outside the minted
branch; none may be the only one.

| Layer | Mechanism | What it stops | Fails if… |
|---|---|---|---|
| **1. Capability scope** | Signed, expiring artefact naming the operation, the branch, the head SHA and the nonce | Any request for an operation outside the named one, or for a branch the operator did not approve, is refused before any GitHub call | The signer is compromised — but the signer is the operator's own device, and the artefact still names one branch |
| **2. Gateway code** | The ported `guard_task()` set (G1–G16) plus a hard ref check before every write | A well-formed capability for the right branch but the wrong content: denied paths, secret patterns, merge commits, wrong ancestry | The gateway has a logic bug — but layer 3 still refuses the write |
| **3. GitHub platform** | Branch rulesets with "Restrict updates" on `main` and `build-engine`; a push ruleset for denied paths | Any push to a protected branch by the app's actor, regardless of what the app or the gateway believes | The actor is on the bypass list — a deliberate, reviewable operator decision (D-d) |

### The honest limitation

GitHub does not issue branch-scoped tokens. A fine-grained PAT, a GitHub App
installation token, an OAuth token and a deploy key are all repository-level. Branch
scope is therefore **effective** scope: a repository-level credential plus a ruleset
that denies the protected refs to that actor.

The token *can* write `main`; GitHub refuses the push. Layer 3 is what makes the claim
true, which is precisely why it cannot be optional — and why V1 and V4 below must be
confirmed as *platform* rejections rather than gateway-code rejections.

---

## 3. Residual risks

These survive the recommended architecture. Each is stated with the layer that mitigates
it and what remains unmitigated.

| ID | Risk | Mitigation | Residual |
|---|---|---|---|
| **R1** | The gateway is a single point of compromise for every privileged operation | Narrow credential scope (§4); rulesets (§6); full guard set (§7) | A gateway compromise with the signing key could mint capabilities for the operator's branches. It still cannot reach `main` or the plan. |
| **R2** | Branch rulesets are configuration, and configuration drifts | Verification procedure V1–V4 run in CI against a throwaway repository | Requires the check to run on every configuration change. A ruleset removed silently would not be detected by the gateway itself. |
| **R3** | The console is a new client for a privileged surface | Capabilities are scoped and expiring; the console holds no repository credential | A compromised console can still request operations within its operator credential's scope. |
| **R4** | Plan-supplied commands remain a code-execution surface | `verify` stays off the gateway (§12.1) | Unchanged from today. The console must not become a way to reach it. |
| **R5** | Workers Logs may capture request metadata, or — without the gate — secrets | The redaction gate and the log-hygiene check, extended to the new routes | Cloudflare retains request metadata under its own policies. The claim must be restated: the gateway stores no operator credentials, no repository metadata and no submission content in Cloudflare-managed storage; request metadata is processed transiently. |
| **R6** | Compare-and-swap conflicts surface as operator-visible errors | Durable Object serialisation plus bounded retry | Two operators editing the same plan concurrently will see conflicts rather than silent loss. This is the intended trade. |
| **R7** | The gateway's GitHub credential is repository-scoped, not branch-scoped | Rulesets (§6) | The credential *can* write `main`; GitHub refuses the push. The property depends on layer 3 being configured. |
| **R8** | Device compromise yields capabilities, not credentials | Five-minute capability TTL; enrolment revocation | Up to five minutes of capability validity after a device is compromised. Bounded and acceptable. |

### Console-specific additions

| ID | Risk | Mitigation | Residual |
|---|---|---|---|
| **R9** | The device claims hardware backing it does not have | `KeyInfo.isInsideSecureHardware` is read and reported, not assumed (§13) | On a software-backed device the console is honest but weaker. The operator must know which they are looking at — hence `SecurityPostureBadge`. |
| **R10** | A proxy or mis-pointed DNS becomes a credential-capture point | Mutually authenticated transport (mTLS / attestation) plus certificate pinning (ADR 0003) | A compromised CA in the device's trust store. Pinning narrows this further. |
| **R11** | A capability or its nonce reaches a log sink | `util/Redactor` is a hard gate on every line; the gateway mirrors it with `redaction.ts` | A future code path that bypasses `util.Logger`. Mitigated by making the redactor the only way to log. |

---

## 4. Verification procedure

Branch scope must be verified as an executable procedure, not asserted in prose.

| # | Check | Expected result | Layer proven |
|---|---|---|---|
| **V1** | Push a commit to `main` on the product repository using the app's GitHub App credential | Rejected **by GitHub**, not by our code. Capture the rejection. | 3 |
| **V2** | Push a commit to `main` using the operator's human credential | Accepted — the bypass path still works, so the operator can merge. | 3 |
| **V3** | Push a commit to `task-<id>` using the app's credential | Accepted. This is the one permitted write. | 3 |
| **V4** | Push a file under `plans/` to `task-<id>` | Rejected by the push ruleset, regardless of branch. | 3 |
| **V5** | Present a capability naming `task-A`, then request a write to `task-B` | Rejected by the gateway | 1 |
| **V6** | Replay a previously used capability | Rejected | 1 |
| **V7** | Present a capability whose `head` no longer matches the branch | Rejected | 1 |
| **V8** | Trigger a gateway action while a `HALT` marker is present | Refused | 2 |

> **Acceptance.** All eight checks pass, with V1 and V4 confirmed as *platform* rejections
> rather than gateway-code rejections. Only then is branch-scoped authority a property of
> the system rather than a property of its implementation.

**Running them in CI.** V1–V4 need a credential that can attempt the push; V5–V8 need a
live gateway. A CI job can run all of them against a throwaway repository and a staging
gateway, given the same narrowly scoped credential and the same rulesets.

---

## 5. The console's part of the guard set

The gateway enforces G1–G15. The console owns two of them, and must not duplicate the
rest:

| Guard | Console's obligation |
|---|---|
| **G1 — private-repository assertion** | None. The gateway runs it before any write, and it is not skippable by a request parameter. The console must not offer a way to influence it. |
| **G16 — explicit publish confirmation** | **Owned.** The confirmation is the operator's, and it is what produces the capability. The console must not offer a path that skips it. |
| Guard parity | The console must never make an authorisation decision. It may *display* the guard summary; it may not evaluate it. |

A useful rule to hold onto: **the client must not be able to widen its own authority.**
The console's whole contribution to that rule is the capability format — one operation,
one branch, one head SHA, one nonce, signed by the operator's own device after a
confirmation gesture.
