# Operator Console — Stage 4.1 implementation package

Target repository: `inscope-labs/operator-console` (default branch observed: `main`)
Scope: Stage 4.1 — Device key and enrolment, based on the supplied File Manifest by Phase / Stage.

## Package contents

The package contains complete replacement source for the twelve Stage 4.1 manifest files:
- DeviceKeySpec, DeviceKeyManager, StrongBoxSupport, KeystoreSigner
- DevicePosture, SecurityPostureBadge, DeviceAttestation
- DeviceEnrollmentStore
- EnrollDeviceUseCase, RotateDeviceKeyUseCase, ReportDevicePostureUseCase
- EnrollmentScreen and EnrollmentViewModel

It also contains focused tests, a repository integration guide, and this review note.

## Important findings from repository inspection

1. The current repository's `DeviceEnrollmentStore.kt` is under `data/local`, but the supplied stage manifest specifies `M/data/enrollment/DeviceEnrollmentStore.kt`. This package follows the manifest and uses `com.inscopelabs.buildengine.data.enrollment`. The existing `DataModule` import/provider must be updated as described in `INTEGRATION.md`.
2. `GatewayApi` already exposes `enroll(EnrollRequestDto)` and `rotateKey(RotateKeyRequestDto)`. `EnrollDto.kt` already defines the exact wire shapes. No new endpoint or DTO is invented.
3. The repository's current `KeystoreSigner` returns a 64-byte zero signature when no signature provider is supplied. That is unsafe: it makes a missing signer appear successful. This package changes the behaviour to fail closed. The current Stage 4.2 `BiometricGate` is only a Boolean API and does not yet return a `CryptoObject`-authorized `Signature`; production signing therefore remains blocked until Stage 4.2 wires the biometric prompt to the signing operation.
4. The current `/enroll` contract carries `alias`, `publicKey`, and `attestationChain`. It does not define a server nonce/challenge exchange. `requestAttestation` remains deferred as required by the manifest. Key-attestation certificate-chain availability is implemented, but the gateway's challenge-verification semantics must be agreed before production enrolment can be considered fully attested.
5. The current gateway contract says the gateway verifies attestation before issuing a `kid`. The client refuses to enrol if it cannot obtain a key-attestation chain; it does not silently omit attestation.
6. StrongBox is attempted only when requested and supported. The code falls back to the normal Android Keystore only for an explicit StrongBox-unavailable condition; it does not mask unrelated key-generation errors.
7. Local enrolment metadata is not authority. The gateway remains the enforcement point. Local persistence is used only to select and track the device key; malformed or inconsistent records fail closed.

## Validation status

This package was authored against the repository files and contracts inspected on 2026-10-31. It has **not** been built with the repository's Android SDK/Gradle toolchain in this environment, nor tested on a physical StrongBox device. Do not label it a verified release artifact until:
- integration changes in `INTEGRATION.md` are applied;
- Gradle compilation and JVM tests pass;
- Android instrumentation tests pass on at least one software-backed device and one StrongBox-capable device if available;
- Stage 4.2 supplies a CryptoObject-bound authentication path;
- the gateway confirms the attestation challenge semantics.

## Security boundary

No private key bytes are exported. No GitHub credential is introduced. The public key is encoded as base64url without padding. The certificate chain is sent as standard Base64 DER certificates, matching the existing DTO examples.
