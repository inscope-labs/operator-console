# Operator Console & Gateway A — Operator Guide (v2.1)

**Target release:** 0.6.0  
**Status:** Canonical Operator Guide (replaces v2.0 per `RELEASE-PLAN.md` Part B and Stage 0.2 / T0-206)  
**Companion plans:** [`RELEASE-PLAN.md`](../RELEASE-PLAN.md), [`STATUS.md`](STATUS.md), [`gateway-contract.md`](gateway-contract.md)

---

## 1. System Topology & Ownership Boundaries

The Build Engine coordinates autonomous AI coding agents and human operator authority. **Gateway A is the sole enforcement point for operator actions.**

```
┌─────────────────────────────────┐
│     Operator Console (Android)   │
│     app/ (Kotlin, Compose)      │
│  • Sole seat of human authority │
│  • Untrusted for enforcement    │
│  • Holds no GitHub credentials  │
└────────────────┬────────────────┘
                 │ HTTPS (TLS 1.3 + SPKI Pinning)
                 │ Capability (ctxop-v1) + Operator Bearer
                 ▼
┌─────────────────────────────────┐
│     Gateway A (Cloudflare)      │
│     gateway/ (TypeScript)       │
│  • Sole enforcement point       │
│  • Evaluates guard set (G1–G15) │
│  • Holds GitHub App credential  │
│  • Writes consumer repo audit   │
└────────────────┬────────────────┘
                 │ Branch CAS, Commits, PRs
                 ▼
┌─────────────────────────────────┐
│   GitHub Consumer Repository    │
│   (Private target repository)   │
└─────────────────────────────────┘
```

### 1.1 Repository Locations & Roles
- **Operator Console (`app/`)**: Native Android application. Manages non-exportable Keystore signing keys, evaluates device posture, collects human confirmation, and mints scoped `ctxop-v1` capabilities.
- **Gateway A (`gateway/`)**: Cloudflare Worker co-located in this repository. Verifies capability signatures, nonces, and TTLs; evaluates repo guards; executes branch-head compare-and-swap mutations; writes durable audit records.
- **Gateways B & C**: Located in `inscope-labs/build-engine`. Provide read context links and write submission channels for Class B autonomous agents.

> **Correction from v2.0**: Earlier drafts stated the gateway was an external repository. **Gateway A is located directly in `gateway/` in this repository.** The legacy workflow names `cbridge-ci.yml` and `cbridge-deploy.yml` build and deploy Gateway A, not the discontinued Termux bridge.

---

## 2. The Credential & Security Model

### 2.1 The Cardinal Security Invariant
> **No operator gets full repository access: only the minted branch.**

1. **Zero Repository Credentials on Device**: The Android app holds no GitHub Personal Access Token (PAT), OAuth token, or GitHub App secret. There is nothing repository-scoped on the device that can be stolen or extracted.
2. **Gateway-Scoped Operator Bearer**: The app stores an encrypted, rotatable operator bearer credential in Android Keystore-backed storage (`EncryptedSharedPreferences`). This credential authenticates requests to the gateway's administrative surface but grants zero GitHub repository privileges.
3. **Session vs Credential**: The operator bearer credential is persisted; the operator **session** is in-memory only. The 5-minute capability TTL is the boundary of authority.
4. **Per-Environment Isolation**:
   - Device keys are strictly namespaced per environment (`test`, `staging`, `production`) and host. An enrolment on `staging` cannot mint capabilities for `production`.
   - The gateway rejects any request where the client's configured environment does not match the Worker's `ENVIRONMENT` setting.

---

## 3. Human-in-the-Loop Confirmation Tiers

Every consequential operation requires individual human confirmation. **There are no batch approvals, automatic pre-approvals, or background minting.**

### 3.1 Confirmation Tiers (`AuthTier`)
Per ADR 0012, the console resolves one of two confirmation tiers based on device security:

| Tier | Required Gesture | Device Hardware Requirements | Audit Record Marking |
|---|---|---|---|
| **`hardware_confirm`** | Biometric prompt or device lock credential (PIN/pattern/password) | Keystore key with `setUserAuthenticationParameters` (API 30+) or `setUserAuthenticationRequired` (API 28–29). | `authTier: "hardware_confirm"` |
| **`in_app_confirm`** | Typing the first 7 hexadecimal characters of the target HEAD SHA | Unlocked device or hardware lacking biometric sensors. | `authTier: "in_app_confirm"` |

### 3.2 Display & Confirmation Rules
- **Full SHA Display (Mandatory)**: The confirmation dialog must always display the **full 40-character commit SHA**, operation name, plan ID, task ID, and branch name.
- **Gesture Semantics**: Typing the 7-character prefix in `in_app_confirm` is solely the human gesture; the minted capability always binds to the full 40-character SHA.
- **Environment Gating**: Production environments may reject `in_app_confirm` enrolments with `AUTH_TIER_NOT_ALLOWED`. The app gracefully degrades to read-only operation.

---

## 4. Operation Lifecycle & Failure Vocabulary

### 4.1 Single-Path Minting & Execution
Every privileged action (publish, reconcile, upload, halt, resume, decide) follows an identical pipeline:
1. **Live-Head Check**: `VerifyBranchHeadUseCase` queries the gateway for the current branch SHA. Fails closed on drift or network error.
2. **Intent Journaling**: Operation intent and a unique idempotency key are written to the local journal *before* any network transmission.
3. **Operator Confirmation**: Summary card presented to operator; confirmation gesture executed.
4. **Capability Minting**: Single-use nonce sourced; `ctxop-v1` capability constructed and signed with the device private key.
5. **Gateway Dispatch**: Payload dispatched over HTTPS. Gateway evaluates G1–G15 guards and performs compare-and-swap update.
6. **Live Outcome & Verification**: Gateway responds with `auditRef`. App checks live branch state before reporting success.

### 4.2 Handling Uncertain Outcomes
If the network drops or the process terminates between request dispatch and response:
- The outcome is **`UncertainCompletion`** — never assume success or failure.
- The app **never automatically retries** a state-changing capability.
- Upon reconnection or restart, the app executes `GET /operations/{id}` to query the gateway for the resolution of the idempotency key.

### 4.3 Distinct Failure Vocabulary
The console distinguishes between distinct failure domains:
- `NOT_CONFIGURED`: No gateway URL or credentials configured; zero sockets opened.
- `UNREACHABLE`: Network offline, DNS failure, or gateway timeout.
- `TLS_PINNING`: Certificate chain mismatch or expired SPKI pin set.
- `AUTH`: Operator bearer credential rejected or expired.
- `STALE`: Remote branch HEAD moved since inspection; capability expired.
- `POLICY_REFUSED`: Gateway guard failed (e.g., G14 halt active, G8 denied path, `AUTH_TIER_NOT_ALLOWED`).

---

## 5. Operator Runbook & Responsibilities

### 5.1 What the Operator Must Do
- **Inspect Before Approving**: Verify task ID, branch, and expected SHA on the `PublishConfirmScreen`.
- **Enrol Separately Per Environment**: Never enrol the same device key on both staging and production.
- **Observe Key Posture**: Verify the `SecurityPostureBadge` shows the true hardware or software backing of the key.
- **Monitor Pin Expiry**: Ensure SPKI pin sets are updated at least 60 days before expiration.
- **Record Rationale**: Decisions (`decide`) and emergency actions (`halt`/`resume`) require attribution, rationale, scope, and evidence.

### 5.2 What the Operator Must NEVER Do
- ❌ **Never paste credentials into chats or logs**: Do not paste GitHub PATs, operator bearer tokens, private keys, capabilities, or nonces into conversational AI interfaces, issues, or logs.
- ❌ **Never use Lab Mode on staging or production**: Lab Mode uses synthetic HMAC signing without biometrics and is restricted exclusively to gateways reporting `"environment": "test"`.
- ❌ **Never run verification commands on the phone**: Plan verification (`check`, `render --check`, `verify`) runs in CI on an isolated runner; the phone has no shell execution capability.
- ❌ **Never treat an unverified or debug APK as an authority client**: Operational approvals must originate from release-signed APKs verified against published SHA-256 checksums.

---

## 6. Pre-Operational Reconciliation Checklist

Before transitioning from Termux `cbridge` to the Operator Console for any operation, the following gates must be confirmed:

1. [ ] **Gateway A Deployed & Verifiable**: Staging gateway passes typecheck, lint, unit tests, and live conformance V1–V12 without permissive flags.
2. [ ] **SPKI Pins Active**: Production/staging host pins configured in `network_security_config.xml` with valid expiration date.
3. [ ] **Device Posture Verified**: Target device (e.g. Galaxy A03 Core) enrolled; posture badge displays accurate security level and accepted auth tier.
4. [ ] **Row-by-Row Sign-Off**: Operator has signed the replacement row in `docs/CBRIDGE-COVERAGE-MATRIX.md`.
5. [ ] **Staging Proof Archived**: At least one complete live end-to-end run (enrolment → status read → confirmation → capability minting → guarded publish → audit verification) archived in `docs/release/`.
