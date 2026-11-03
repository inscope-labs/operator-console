# Stage 4.2 — Confirmation and minting (the single path)

**Execution Date**: 2026-11-03T14:35:30Z  
**Repository**: inscope-labs/operator-console  
**Target Platform**: Android (minSdk 28, compileSdk 34)  

---

## 1. Summary of Completed Tasks

### T4-201: Confirmation Tiers & Biometric Prompt Host
- Created `core/security/AuthTier.kt`: Defines closed enumeration for `HARDWARE_CONFIRM` (`"hardware_confirm"`) and `IN_APP_CONFIRM` (`"in_app_confirm"`).
- Created `core/security/AuthTierResolver.kt`: Implemented pure function `resolve(sdkInt, isDeviceSecure, biometricStatus)`:
  - On API >= 30: `KeyguardManager.isDeviceSecure` allows `HARDWARE_CONFIRM` without requiring a biometric sensor.
  - On API 28–29: strictly requires `BiometricManager.BIOMETRIC_SUCCESS` with strong biometrics.
  - Degrades gracefully to `IN_APP_CONFIRM` when secure lock screen/biometrics are unavailable.
- Created `core/security/BiometricPromptHost.kt`: Defines `PromptInfoSpec`, `BiometricPromptResult`, and `BiometricPromptHost` with weak-reference lifecycle registration.
- Updated `core/security/BiometricGate.kt`: Converted to `open class BiometricGate` with `suspend fun authorize(signature, info, policy): AuthorizedSignatureResult`, returning typed outcomes (`Success`, `Cancelled`, `Lockout`, `NoPromptHost`, `HardwareUnavailable`, `KeyInvalidated`, `Failed`). Kept legacy `authenticate` and `canAuthenticate` for backward compatibility.
- Updated `MainActivity.kt`: Extends `FragmentActivity`, registers `BiometricPromptHost` in `onResume()` and unregisters in `onPause()`.

### T4-202: Live Remote Head Verification
- Updated `domain/repository/TaskRepository.kt`: Added `suspend fun liveHead(branch: String): AppResult<String>`.
- Updated `data/repository/TaskRepositoryImpl.kt`: Implemented `liveHead(branch)` delegating to `GatewayApi.branchHead(branch)`.
- Updated `domain/usecase/common/VerifyBranchHeadUseCase.kt`: Returns sealed `VerifyBranchHeadResult` (`Match`, `Drifted(liveSha)`, `Unavailable(reason)`). Fails closed on drift or error.
- Updated `testutil/FakeRepositories.kt`: Added `liveHeadResult` and `liveHead` implementation in `FakeTaskRepository`.

### T4-203: Confirm and Mint (The Single Path)
- Updated `domain/model/OperationRequest.kt`: Added `typedHeadConfirmation: String? = null`.
- Updated `core/crypto/KeystoreSigner.kt`: Added constructor accepting pre-authorized `Signature`.
- Updated `capability/signing/DeviceKeyCapabilitySigner.kt`: Added constructor accepting pre-authorized `Signature`.
- Updated `capability/CapabilityMinter.kt`: Added optional `overrideSigner` and `mintedAtEpochSeconds` parameters to `mint(...)`. Existing overloads and codec vectors preserved unchanged.
- Implemented `domain/usecase/common/ConfirmAndMintCapabilityUseCase.kt`:
  - Enforces strict order: `VerifyBranchHead` -> `onShowSummary` -> Authorize -> Mint.
  - Sealed `MintResult` return type: `Minted`, `HeadDrifted`, `HeadUnavailable`, `Declined`, `AuthFailed`, `LockedOut`, `KeyInvalidated`, `NotEnrolled`, `ExpiredWhileConfirming`, `TierNotAllowed`.
  - For `HARDWARE_CONFIRM`: initializes `Signature` (SHA256withECDSA) with private key before prompt, passes via `BiometricPrompt.CryptoObject`, receives authorized `Signature` upon confirmation, calculates TTL post-approval (`approvedAt + ttlSeconds`), and produces 64-byte IEEE P1363 raw signature ($r \parallel s$).
  - For `IN_APP_CONFIRM`: verifies typed first 7 hex characters of expected HEAD SHA, signs using non-auth key.
- Created `ArchitectureTest.kt`: Enforces architectural constraint that `.mint(` appears exclusively in `ConfirmAndMintCapabilityUseCase.kt`, `CapabilityMinter.kt`, and minter test suites.

### T4-204: Generic UI Components
- Updated `ui/components/CapabilitySummaryCard.kt`: Displays operation, task, plan, branch, full 40-character head SHA, TTL, posture/tier badge, and typed confirmation input field when `in_app_confirm` is active.
- Updated `ui/components/ConfirmationScaffold.kt`: Provides generic layout scaffold with content slot and confirm gesture action.

### T4-205: Remediation of 4.1 Enrolment & Key Lifecycle
- Updated `core/crypto/DeviceKeySpec.kt`: Added `attestationChallenge: ByteArray? = null` and `authTier: AuthTier = AuthTier.HARDWARE_CONFIRM`.
- Updated `core/crypto/DeviceKeyManager.kt`:
  - Added `DeviceKeyStatus` (`Valid`, `KeyNeedsRotation`, `NeedsReenrollment`), `KeyNeedsRotationException`, and `NeedsReenrollmentException`.
  - Configured user authentication per tier and API level (`setUserAuthenticationParameters` on API >= 30; `setUserAuthenticationRequired` on API 28-29; `setUserAuthenticationRequired(false)` for in-app confirm).
  - Passed raw challenge bytes to `KeyGenParameterSpec.Builder.setAttestationChallenge`.
  - Handles `KeyPermanentlyInvalidatedException` and `UnrecoverableKeyException` gracefully as `NeedsReenrollment`.
- Updated `core/security/DeviceAttestation.kt`: Enforces certificate chain size >= 2 (refuses self-signed certificates with size < 2).
- Updated `data/enrollment/DeviceEnrollmentStore.kt`: Persists `authTier` across enrollment records, pending records, encoding, and decoding.
- Updated `domain/usecase/enrollment/EnrollDeviceUseCase.kt` & `RotateDeviceKeyUseCase.kt`:
  - Fetches single-use challenge from `/enroll/challenge`.
  - Generates key with decoded raw 32-byte challenge.
  - Validates attestation chain length >= 2.
  - Automatically retries once on `ENROLL_CHALLENGE_INVALID`.
  - Maps `AUTH_TIER_NOT_ALLOWED` to `AuthTierNotAllowedException` without crashing.
  - For rotation, preserves old key until new key enrollment succeeds, deleting old key only upon successful activation.
- Updated `domain/model/SecurityPosture.kt` & `core/security/DevicePosture.kt`: Added `authTier: AuthTier? = null`.
- Updated `ui/components/SecurityPostureBadge.kt`: Displays tier text ("Biometric/PIN confirm" vs "In-app confirm only (no device lock)").
- Updated `ui/enrollment/EnrollmentViewModel.kt`: Handles `AuthTierNotAllowedException`, `KeyNeedsRotationException`, and `NeedsReenrollmentException` gracefully.
- Updated `di/UseCaseModule.kt` and `di/CryptoModule.kt`: Wired dependencies for use cases and components.

### T4-206: Gateway Network Layer
- Updated `core/network/GatewayEndpoints.kt`: Added `ENROLL_CHALLENGE = "/enroll/challenge"` and endpoint builder.
- Updated `core/network/dto/EnrollDto.kt`: Added `EnrollChallengeDto(challenge, expiresAt)`. Added `challenge` and `authTier` to `EnrollRequestDto` and `RotateKeyRequestDto`. Added optional `authTier` to `EnrollResponseDto`.
- Updated `core/network/ErrorMapper.kt`: Added error codes `ENROLL_CHALLENGE_INVALID` (400), `ENROLL_ATTESTATION_INVALID` (400), and `AUTH_TIER_NOT_ALLOWED` (403), mapping to `GatewayFailureState`.
- Updated `data/remote/GatewayApi.kt` & `data/remote/GatewayRepository.kt`: Added `suspend fun enrollChallenge(): EnrollChallengeDto`.
- Updated `testutil/FakeGatewayApi.kt`: Implemented `enrollChallengeResult` and `enrollChallenge()`.

### T4-207: Documentation
- Updated `docs/gateway-contract.md`: Added §3.15a `POST /enroll/challenge`, updated §3.15 and §3.16 with `challenge` and `authTier`, added error codes to §4.1 table.
- Created `docs/adr/0007-confirmation-tiers-and-attestation-challenge.md`: Documents architecture, tiers, attestation challenge, degradation model, and gateway policy.
- Updated `docs/device-enrollment.md`: Documented challenge flow, attestation chain verification ($\ge 2$), and auth tiers.
- Updated `docs/threat-model.md`: Added `R12` covering `in_app_confirm` residual risk and gateway policy gating.

---

## 2. Files Changed and Created

### Source Code (`app/src/main/`)
- `core/security/AuthTier.kt` (new)
- `core/security/AuthTierResolver.kt` (new)
- `core/security/BiometricPromptHost.kt` (new)
- `core/security/BiometricGate.kt`
- `core/security/BiometricPolicy.kt`
- `core/security/DevicePosture.kt`
- `core/security/DeviceAttestation.kt`
- `core/crypto/DeviceKeySpec.kt`
- `core/crypto/DeviceKeyManager.kt`
- `core/crypto/KeystoreSigner.kt`
- `core/network/GatewayEndpoints.kt`
- `core/network/ErrorMapper.kt`
- `core/network/dto/EnrollDto.kt`
- `data/remote/GatewayApi.kt`
- `data/remote/GatewayRepository.kt`
- `data/repository/TaskRepositoryImpl.kt`
- `data/enrollment/DeviceEnrollmentStore.kt`
- `domain/model/OperationRequest.kt`
- `domain/model/SecurityPosture.kt`
- `domain/repository/TaskRepository.kt`
- `domain/usecase/common/VerifyBranchHeadUseCase.kt`
- `domain/usecase/common/ConfirmAndMintCapabilityUseCase.kt`
- `domain/usecase/enrollment/EnrollDeviceUseCase.kt`
- `domain/usecase/enrollment/RotateDeviceKeyUseCase.kt`
- `domain/usecase/enrollment/ReportDevicePostureUseCase.kt`
- `capability/CapabilityMinter.kt`
- `capability/signing/DeviceKeyCapabilitySigner.kt`
- `ui/MainActivity.kt`
- `ui/components/CapabilitySummaryCard.kt`
- `ui/components/ConfirmationScaffold.kt`
- `ui/components/SecurityPostureBadge.kt`
- `ui/enrollment/EnrollmentViewModel.kt`
- `ui/enrollment/EnrollmentScreen.kt`
- `di/UseCaseModule.kt`

### Test Code (`app/src/test/`)
- `ArchitectureTest.kt` (new)
- `core/security/AuthTierResolverTest.kt` (new)
- `core/security/BiometricGateTest.kt`
- `domain/usecase/common/ConfirmAndMintCapabilityUseCaseTest.kt`
- `domain/usecase/common/VerifyBranchHeadUseCaseTest.kt`
- `domain/usecase/enrollment/EnrollDeviceUseCaseTest.kt`
- `domain/usecase/enrollment/RotateDeviceKeyUseCaseTest.kt`
- `data/enrollment/DeviceEnrollmentStoreTest.kt`
- `testutil/FakeDeviceCrypto.kt`
- `testutil/FakeGatewayApi.kt`
- `testutil/FakeRepositories.kt`

### Documentation (`docs/`)
- `docs/gateway-contract.md`
- `docs/adr/0007-confirmation-tiers-and-attestation-challenge.md` (new)
- `docs/device-enrollment.md`
- `docs/threat-model.md`

---

## 3. Test Results

- **Unit Test Suite**: `gradle :app:testDebugUnitTest`
  - Total Tests Executed: **216**
  - Failures: **0**
  - Errors: **0**
  - Skipped: **0**
- **Architecture Test**: Verified that `.mint(` appears exclusively in `ConfirmAndMintCapabilityUseCase.kt`, `CapabilityMinter.kt`, and test files.
- **Wire Compatibility**: All existing capability codec vectors and `CapabilityMinterTest` tests pass unchanged.
- **Compilation Check**: `compile_applet` succeeded with zero warnings or errors.

---

## 4. Skipped Items

- None. All tasks from T4-201 through T4-207 were executed in full.
- Phase 4.3/4.4 features (Publish, Reconcile, Halt, Resume screens and write use cases) were intentionally not touched per Section 5 "Out of scope".
- No Lab bypass code added.

---

## 5. Stop-and-Ask Items

- None. All requirements were fully addressed within the CONTRACT ADDENDUM and permitted Section 3 files. No protected paths (`.github/workflows/*.yml`, `proguard-rules.pro`, `AndroidManifest.xml`, `app/build.gradle.kts`, `version.properties`, `build-logs/**`) required modification.
