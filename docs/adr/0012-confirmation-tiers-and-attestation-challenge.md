# ADR 0012 — Confirmation Tiers and Attestation Challenge

| | |
|---|---|
| **Status** | Accepted (renumbered from 0007 on 2026-11-06 per T0-204) |
| **Date** | 2026-11-03 |
| **Decision IDs** | D-T1, D-T5, D-T6 |
| **Related** | `docs/adr/0001-capability-signing-direction.md`, `docs/adr/0006-mtls-deferred.md`, `docs/gateway-contract.md` |

---

## Context

Audit of the initial capability signing and device enrolment implementations (Stage 4.1) surfaced three core limitations:

1. **Attestation Challenge Omission**:
   Android Keystore only produces a hardware attestation certificate chain if an explicit challenge is supplied via `setAttestationChallenge()`. Without a challenge, Android returns a single self-signed certificate; checking that the certificate chain is non-empty was insufficient to prove hardware backing.
2. **Brittle Device Authentication**:
   Configuring `setUserAuthenticationRequired(true)` without specifying authenticator types or device credential fallbacks caused devices without biometric hardware (or where operators rely on device PIN/pattern) to fail key generation or operation. Additionally, Android API level differences dictate distinct Keystore configuration APIs.
3. **Disconnected Biometric Authorization**:
   Biometric authentication simply returned a Boolean success flag rather than authenticating a `Signature` wrapped in a `BiometricPrompt.CryptoObject`. This allowed potential decoupling between user presence and cryptographic minting.

---

## Decision

### 1. Two Confirmation Tiers (`hardware_confirm` and `in_app_confirm`)

We define two explicit confirmation tiers resolved on-device via `AuthTierResolver.resolve()`:

- **`hardware_confirm`**:
  Hardware-enforced, per-use operator authentication using the Android Keystore.
  - **API >= 30**: Configured with `setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG or AUTH_DEVICE_CREDENTIAL)`. Secure lock screen (`KeyguardManager.isDeviceSecure`) is sufficient; biometric sensor is utilized when enrolled.
  - **API 28-29**: Configured with `setUserAuthenticationRequired(true)` only when `BiometricManager.canAuthenticate == SUCCESS`.
  - Keys are bound to user authentication, requiring a valid `BiometricPrompt.CryptoObject(Signature)` authorization before signing.
- **`in_app_confirm`**:
  Degraded confirmation tier applied when device lock screen or biometric hardware is absent.
  - `setUserAuthenticationRequired(false)` on the Keystore key.
  - Operators confirm actions by explicitly inspecting the operation summary and typing the first 7 hexadecimal characters of the target HEAD SHA.
  - The app never refuses to run or read; it only degrades how write capabilities are authorized.

### 2. Single-Use Gateway Attestation Challenge

- The gateway provides `POST /enroll/challenge` returning a single-use, 32-byte cryptographically secure random string (`base64url`, unpadded) with a 300-second TTL.
- Key generation passes the raw 32 decoded bytes to `setAttestationChallenge`.
- Enrolment and rotation require an X.509 certificate chain length $\ge 2$ to verify hardware attestation. Single-certificate chains (self-signed) are rejected.
- If a challenge expires or has been consumed, the client retries challenge acquisition once on `ENROLL_CHALLENGE_INVALID`.

### 3. Gateway-Configured Production Policy

- The tier (`authTier`) is submitted during `/enroll` and `/enroll/rotate`. The gateway verifies the attestation and registers the tier associated with the key identifier `kid`.
- The wire format of the capability (10 LF-separated fields) and signature encoding are byte-identical across tiers (D-T1).
- Production environments can disallow `in_app_confirm` registrations by returning `403 Forbidden` (`AUTH_TIER_NOT_ALLOWED`). The console gracefully transitions into a read-only state.

### 4. Single-Path Minting Architecture

- All capability minting is routed through `ConfirmAndMintCapabilityUseCase`.
- Order of execution:
  1. `VerifyBranchHeadUseCase`: Verifies live remote head SHA (`Match`, `Drifted`, or `Unavailable`). Fails closed on drift or error.
  2. Operator Confirmation: Presents `CapabilitySummaryCard`.
  3. Signature Authorization: Pre-initializes `Signature` before prompt in `hardware_confirm`, or validates 7-char head confirmation in `in_app_confirm`.
  4. Capability Construction: Timestamps, expiration, and nonces are computed strictly after authorization, ensuring the 300s TTL begins upon approval.
  5. Signing: Generates IEEE P1363 (64-byte) raw $r \parallel s$ signature.

---

## Consequences

### Positive
- Accommodates modern Android lock screen credentials (PIN/pattern/password) without failing on devices lacking biometric hardware.
- Cryptographically couples operator biometric authentication directly to the signing operation via `CryptoObject`.
- Robust hardware verification prevents forged self-signed public keys.
- Preserves read capability on devices with degraded security posture while enforcing policy on write actions.

### Negative & Residual Risks
- `in_app_confirm` lacks hardware-enforced presence proof; reliance is placed on gateway policy configuration to restrict high-risk environments.
- Key invalidation (e.g., biometric enrollment alterations on API 28-29) requires explicit re-enrolment.
