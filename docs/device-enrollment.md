# Device enrolment and key rotation

How a device becomes able to sign capabilities, how that enrolment is revoked, and what
the console is allowed to claim about the hardware it is running on.

This document covers decision **D-a** (signing direction) and the enrolment half of
**D-b**.

---

## 1. The model

**Model B — the console mints, the gateway verifies.** The console signs each
capability with its own device key, after the operator confirms; the gateway verifies
against the public key it has on file for that device.

Model B is recommended because it makes the capability a **record of a human decision**
rather than a pre-issued permission. It matches the confirmation flow exactly: no
artefact exists until the operator has decided.

Model A — the gateway mints and the console holds the result — is kept behind the
`CapabilitySigner` interface (`capability/signing/`) so the decision can be reversed
without a re-architecture. See `adr/0001-capability-signing-direction.md`.

---

## 2. The device key

| Property | Value | Why |
|---|---|---|
| Location | Android Keystore; StrongBox where available | The private key never leaves the Secure Element. It cannot be read by the app, and therefore cannot be read by anything that compromises the app's process. |
| Exportability | Non-exportable | A stolen phone yields a *capability*, never a key. |
| Algorithm | EC P-256 (or Ed25519 where the device supports it) | Available on every device at `minSdk 28`; small signatures. |
| User authentication | `setUserAuthenticationRequired(true)` | The key is unusable when the app cannot prompt. This is the desired property for the signing key. |
| Invalidated by biometric enrolment | **Yes** — never `setInvalidatedByBiometricEnrollment(false)` | Otherwise a newly enrolled biometric could mint capabilities. |
| Alias | Stable per device, e.g. `operator-console-signing-v1` | The alias is the link between the `kid` selector and the key. |

`DeviceKeySpec` (`core/crypto/DeviceKeySpec.kt`) carries these parameters; the key
itself is created and used by `DeviceKeyManager` (`core/crypto/DeviceKeyManager.kt`).

### The degradation is deliberate

A key created with `setUserAuthenticationRequired(true)` is unusable when the app cannot
prompt for authentication. The consequence is that the console degrades to **"no
capabilities can be minted"** — never to "mint without confirmation". A device without a
working biometric or device credential can still *read* plan state; it cannot approve a
write. That asymmetry is the correct one.

---

## 3. Honest posture reporting

> "Hardware-backed" is not a claim the app can make unconditionally.

StrongBox requires Android 12 **and** a device with a Secure Element. Many Android
devices have a software-backed Keystore instead. The console must query
`KeyInfo.isInsideSecureHardware` — and `KeyInfo.getSecurityLevel()` where available —
and report the actual result in the UI and in its self-declared posture.

Claiming hardware backing on a device that does not have it is a misrepresentation, and
this project has a norm that an implementation must not claim properties it does not
possess.

`core/crypto/StrongBoxSupport.kt` performs the query.
`core/security/DevicePosture.kt` carries the result.
`ui/components/SecurityPostureBadge.kt` displays it, and distinguishes:

| State | Badge |
|---|---|
| `KeyInfo.isInsideSecureHardware == true` | "Hardware-backed key" |
| `KeyInfo.isInsideSecureHardware == false` | "Software-backed key" |

The badge is not decoration. An operator deciding whether to approve a publish is
deciding on the strength of the key that authorised it.

---

## 4. The enrolment record

The gateway holds a registry of enrolled devices. The console's side of it is
`data/local/DeviceEnrollmentStore.kt`.

| Field | Description |
|---|---|
| `kid` | The key selector that appears in the capability's second field. A selector, not a secret. Rotating keys means minting under a new `kid`. |
| Key alias | The Keystore alias for the private key. Local to the device; the gateway never sees it. |
| Public key | base64url, no padding. The gateway verifies against this. |
| Enrolment state | Pending / active / retired. |
| Created / retired timestamps | For audit. |

The public key is the only thing that leaves the device during enrolment.

### Enrolment flow

```
  operator opens the enrolment screen
            │
            ▼
  DeviceKeyManager.ensureDeviceKey(spec)
            │   creates the key if absent, with
            │   setUserAuthenticationRequired(true)
            ▼
  DeviceKeyManager.publicKeyBase64(alias)
            │
            ▼
  console presents: public key + device attestation statement
            │   (ADR 0003 — the gateway checks the key is
            │    hardware-backed and the device is attested)
            ▼
  gateway registers the device, allocates a `kid`, returns it
            │
            ▼
  DeviceEnrollmentStore.recordEnrollment(kid, alias, publicKey)
```

The attestation is what makes enrolment meaningful: without it, anyone could enrol a
software key on a compromised device. The gateway verifies the attestation before it
issues a `kid`.

---

## 5. Rotation

Keys are rotated per device, and rotation is a first-class operation rather than an
incident response.

| Step | Action |
|---|---|
| 1 | `RotateDeviceKeyUseCase` creates a new key under a new alias. |
| 2 | The new public key is enrolled with the gateway; the gateway issues a new `kid`. |
| 3 | The new key is made active locally; the old enrolment is retired. |
| 4 | Capabilities signed under the retired `kid` stop verifying. |

Rotation is the answer to a suspected key exposure, to a device that is being
re-provisioned, and to a scheduled rotation policy. Because a retired `kid` stops
working immediately, rotation is also the *revocation* mechanism for a lost device's
signing key — although revoking the **enrolment** (§6) is the stronger action.

The console keeps a key *history* only as enrolment records. It never keeps a retired
private key.

---

## 6. Revocation

Three distinct situations, three distinct actions:

| Situation | Action | Effect |
|---|---|---|
| Phone lost or stolen | Revoke the device's enrolment on the gateway | The `kid` stops verifying immediately. Outstanding capabilities signed under it are dead at once. |
| Phone lost or stolen | (additional) Clear the Keystore key locally | The private key is destroyed. Nothing is recoverable. |
| Device compromised, key suspected exposed | Rotate the key | Capabilities under the old `kid` stop verifying. The new enrolment is attested afresh. |

**The five-minute TTL is the backstop, not the control.** Revocation is the control. The
TTL bounds how long a stolen device's *outstanding* capability remains useful if
revocation has not yet happened — at most five minutes (`R8`).

Revocation must be reachable without the device. The operator's human account — not the
console — is the actor that can revoke an enrolment, which is why the human is on the
GitHub ruleset bypass list and the app's actor is not (`D-d`).

---

## 7. What the console must never do

| Never | Why |
|---|---|
| Export the private key | It is not exportable. Any code path that tries to read it is a bug. |
| Set `setInvalidatedByBiometricEnrollment(false)` on the operator credential | A newly enrolled biometric could then mint capabilities. |
| Claim hardware backing without checking `isInsideSecureHardware` | Misrepresentation (§13). |
| Enrol without an attestation statement | Otherwise "enrolled" means nothing. |
| Keep a session token across launches | The session is in-memory only. The capability TTL is the bound. |
| Hold a GitHub credential | There is no reason to. See `adr/0002-no-repository-credentials-on-device.md`. |
