# ADR 0004 — Lab Mode and Test Gateway Conformance

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-10-30 |
| **Decision ID** | D-T5 |
| **Related** | Phase 5, `docs/gateway-contract.md`, `docs/threat-model.md` |

---

## Context

The primary mission of the test console is to exercise and validate the Cloudflare operator
gateway that replaces `cbridge`. To rigorously prove that the gateway functions as the
sole enforcement point, the console must be capable of executing a comprehensive test
catalogue of valid operations as well as **deliberately invalid mutations** (tampered signatures,
corrupted field counts, CRLF injections, expired timestamps, replayed nonces, drifted HEAD SHAs,
and branch guard violations).

In a production workflow:
1. Every capability requires human biometric confirmation via `BiometricPrompt` bound to the Keystore hardware key.
2. The device key cannot be used without physical user authentication.

Requiring biometric confirmation for hundreds of programmatic test scenarios is impossible in
practice and would degrade operator attentiveness. Furthermore, allowing synthetic test
requests or mutant capabilities to reach a live production gateway would risk repository
integrity or trigger false security alarms.

---

## Decision

**We introduce an explicit "Lab Mode" engine in the application with strict environment isolation.**

### 1. The Environment Guard
Before executing any scenario or mutating any capability, the Lab engine calls `GET /health`
on the configured gateway.
- If the gateway responds with `"environment": "test"`, the Lab is permitted to run.
- If the gateway reports any other environment (e.g. `"production"`), omits the environment
  property, or fails the health check, Lab Mode **refuses execution immediately** and
  displays a prominent safety refusal: `ENVIRONMENT_MISMATCH`.
- This check is enforced programmatically prior to each run and cannot be overridden by UI controls.

### 2. Lab Signing (D-T5)
- Real operator operations always mint capabilities using `DeviceKeyCapabilitySigner` backed
  by the Android Keystore and gated by biometric authentication.
- Lab scenarios mint capabilities using `HmacCapabilitySigner` using a runtime-configured
  lab secret and **no biometric prompt**.
- Real operations never use the lab signer or lab secret; the production confirmation flow
  (`ConfirmAndMintCapabilityUseCase`) remains the sole path for operator approval.

### 3. Safety Indications
Every UI screen and surface executing within Lab Mode carries a persistent, un-dismissible
top banner:
> **LAB — not an operator approval**

### 4. Ephemeral Execution & No Durable Storage
- Lab execution results, test run logs, and scenario summaries are stored **in-memory only**.
- There is no local database, Room schema, or persisted audit store for test runs.
- Test reports may be exported exclusively through an explicit user action triggering the
  system share sheet (`Intent.ACTION_SEND` with plain JSON text).
- The Lab toggle in Settings defaults to `false` and is **never persisted across app restarts**.

---

## Consequences

### Positive
- Allows automated, end-to-end regression testing and conformance validation of all gateway guards
  and rejections without operator fatigue.
- Guarantees that synthetic test traffic can never be executed against a production deployment.
- Keeps the application strictly stateless with zero persistent test debris on the mobile device.

### Negative
- The test gateway must maintain support for an HMAC verification key associated with a designated
  lab `kid` selector in addition to production ECDSA public keys.
- Requires dual signing abstractions (`HmacCapabilitySigner` alongside `DeviceKeyCapabilitySigner`).

### Neutral
- Lab classes reside in a dedicated `lab/` package that is layered above core and capability modules;
  core production code never imports or depends on `lab/`.

---

## Revisit Conditions

This decision should be revisited if:
- Cloudflare gateway testing transitions to headless CI runners, rendering the mobile Lab engine obsolete.
- Hardware-backed biometric authentication becomes automatable in secure test environments.
