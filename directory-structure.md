Operator Console — Android Kotlin App Structure

App ID: com.inscopelabs.buildengine

The structure below mirrors the review's architecture: a thin console that holds only a device key and a gateway-scoped credential, mints ctxop-v1 capabilities after biometric confirmation, and has no GitHub credential and no local git/verify execution path.

---

1. Repository root

```
operator-console/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml                 # version catalog
│   └── wrapper/gradle-wrapper.properties
├── gradlew / gradlew.bat
├── .gitignore
├── README.md
├── docs/
│   ├── architecture.md                    # maps packages → design review §§
│   ├── threat-model.md                    # §15 residual risks R1–R8
│   ├── capability-format.md               # ctxop-v1 wire format (§5)
│   ├── device-enrollment.md               # Model B enrolment & rotation (D-a)
│   └── adr/
│       ├── 0001-capability-signing-direction.md
│       ├── 0002-no-repository-credentials-on-device.md
│       └── 0003-mtls-to-gateway.md
└── app/
```

The Cloudflare gateway is a separate project (its own repo, wrangler.toml, deploy gate). Nothing in this tree implements enforcement.

---

2. The app module — full source tree

```
app/
├── build.gradle.kts
├── proguard-rules.pro
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   ├── kotlin/com/inscopelabs/buildengine/
    │   └── res/
    ├── test/                              # JVM unit tests
    └── androidTest/                       # instrumented (Keystore, biometrics, mTLS)
```

2.1 Main source tree

```
com/inscopelabs/buildengine/
│
├── BuildEngineApplication.kt              # @HiltAndroidApp; fails closed if posture check fails
├── MainActivity.kt                        # single Activity; FLAG_SECURE; hosts Compose NavHost
│
├── di/
│   ├── AppModule.kt
│   ├── CryptoModule.kt                    # binds CapabilitySigner, Clock, NonceSource
│   ├── NetworkModule.kt                   # Ktor client, mTLS, pinning
│   ├── DataModule.kt
│   └── UseCaseModule.kt
│
├── core/
│   ├── crypto/
│   │   ├── DeviceKeyManager.kt            # create/retrieve device keypair in Keystore
│   │   ├── DeviceKeySpec.kt               # setUserAuthenticationRequired(true), StrongBox pref
│   │   ├── StrongBoxSupport.kt            # KeyInfo.isInsideSecureHardware / getSecurityLevel
│   │   ├── KeystoreSigner.kt              # ECDSA-P256 or Ed25519 sign over ctxop-v1 message
│   │   ├── SecureRandomSource.kt          # CSPRNG for nonces
│   │   └── Base64Url.kt                   # base64url, no padding (§5)
│   │
│   ├── security/
│   │   ├── BiometricGate.kt               # BiometricPrompt wrapper, CryptoObject-bound
│   │   ├── BiometricPolicy.kt             # setAllowedAuthenticators, invalidation rules (§13)
│   │   ├── DevicePosture.kt               # honest StrongBox report: hardware vs software
│   │   ├── DeviceAttestation.kt           # Play Integrity / key attestation → gateway
│   │   ├── CertificatePinning.kt          # cert pin set for gateway host
│   │   └── SecureWindow.kt                # FLAG_SECURE, screenshot suppression
│   │
│   ├── network/
│   │   ├── GatewayHttpClient.kt           # Ktor/OkHttp with mTLS + pinning
│   │   ├── MtlsConfig.kt                  # client certificate (device-attested)
│   │   ├── GatewayEndpoints.kt
│   │   ├── ErrorMapper.kt                 # maps gateway error catalogue → UI states
│   │   └── dto/                           # wire DTOs (separate from domain models)
│   │
│   ├── time/
│   │   └── Clock.kt                       # injectable, for TTL tests
│   │
│   └── result/
│       └── AppResult.kt
│
├── capability/                            # §5 — the operator capability
│   ├── model/
│   │   ├── OperatorCapability.kt          # kid, exp, order, plan, task, op, branch, head, nonce
│   │   ├── OperationType.kt               # PUBLISH, UPLOAD, RECONCILE, DECIDE, HALT,
│   │   │                                  #   RESUME, SYNC, DOCTOR, ORDER, SET_IN_PROGRESS,
│   │   │                                  #   SET_DONE, NEW_PLAN  (closed enum)
│   │   └── CapabilityScope.kt             # op + branch + head + nonce bound together
│   ├── codec/
│   │   └── OperatorCapabilityCodec.kt     # LF-separated ctxop-v1 encode/decode
│   ├── signing/
│   │   ├── CapabilitySigner.kt            # interface (Model A vs Model B — D-a)
│   │   ├── DeviceKeyCapabilitySigner.kt   # Model B: console signs, gateway verifies  ← recommended
│   │   ├── HmacCapabilitySigner.kt        # Model A: gateway mints (kept for compatibility)
│   │   └── SignedCapability.kt
│   ├── CapabilityMinter.kt                # builds message, signs after biometric unlock
│   └── NonceSource.kt                     # random, single-use per mint
│
├── data/
│   ├── remote/
│   │   ├── GatewayApi.kt                  # suspend calls to gateway surface
│   │   └── GatewayRepository.kt
│   ├── local/
│   │   ├── SelectionStore.kt              # DataStore — repo/plan selection (not security state)
│   │   ├── OperatorCredentialStore.kt     # EncryptedSharedPreferences over Keystore key
│   │   ├── DeviceEnrollmentStore.kt       # kid ↔ device key alias, enrolment state
│   │   └── prefs/
│   └── repository/
│       ├── PlanRepositoryImpl.kt
│       ├── TaskRepositoryImpl.kt
│       ├── OperationRepositoryImpl.kt
│       └── SessionRepositoryImpl.kt
│
├── domain/
│   ├── model/
│   │   ├── Plan.kt
│   │   ├── Task.kt
│   │   ├── TaskBranch.kt                  # name, head SHA, guard status
│   │   ├── WorkOrder.kt
│   │   ├── OperatorSession.kt             # in-memory only; no durable session token
│   │   ├── SecurityPosture.kt             # hardware-backed? attested? pinned?
│   │   └── OperationRequest.kt            # op + scope, pre-confirmation
│   ├── repository/
│   │   ├── PlanRepository.kt
│   │   ├── TaskRepository.kt
│   │   ├── OperationRepository.kt
│   │   └── SessionRepository.kt
│   └── usecase/
│       ├── read/                          # Phase 1 — no write capability
│       │   ├── GetPlansUseCase.kt
│       │   ├── GetNextTasksUseCase.kt
│       │   ├── ValidatePlanUseCase.kt
│       │   ├── RenderCheckUseCase.kt
│       │   └── GetStatusUseCase.kt
│       ├── enrollment/
│       │   ├── EnrollDeviceUseCase.kt
│       │   ├── RotateDeviceKeyUseCase.kt
│       │   └── ReportDevicePostureUseCase.kt
│       ├── write/                         # Phases 2–4 — capability-minted
│       │   ├── PublishTaskUseCase.kt       # the only path to the public repo
│       │   ├── SetTaskStatusUseCase.kt
│       │   ├── CreateWorkOrderUseCase.kt
│       │   ├── UploadSubmissionUseCase.kt
│       │   ├── ReconcileUseCase.kt
│       │   ├── DecideFrameworkUseCase.kt   # D-e: highest-authority action
│       │   ├── HaltUseCase.kt
│       │   └── ResumeUseCase.kt
│       └── common/
│           ├── ConfirmAndMintCapabilityUseCase.kt
│           └── VerifyBranchHeadUseCase.kt  # head drift check before mint
│
├── ui/
│   ├── theme/                             # Material 3
│   ├── navigation/
│   │   ├── AppNavHost.kt
│   │   └── Routes.kt
│   ├── components/
│   │   ├── ConfirmationScaffold.kt        # biometric + explicit confirm
│   │   ├── CapabilitySummaryCard.kt       # shows op, branch, SHA, TTL before signing
│   │   ├── BranchShaRow.kt                # full 40-char SHA, monospace, copyable
│   │   ├── SecurityPostureBadge.kt        # honest hardware/software indicator (§13)
│   │   ├── OperationResultBanner.kt       # surfaces V5–V8 rejections clearly
│   │   └── ErrorBanner.kt
│   ├── dashboard/
│   │   ├── DashboardScreen.kt
│   │   └── DashboardViewModel.kt
│   ├── plans/
│   │   ├── PlanListScreen.kt
│   │   ├── PlanDetailScreen.kt
│   │   └── PlanViewModel.kt
│   ├── taskdetail/
│   │   ├── TaskDetailScreen.kt
│   │   └── TaskDetailViewModel.kt
│   ├── publish/
│   │   ├── PublishConfirmScreen.kt        # branch + exact SHA + guards summary
│   │   ├── PublishProgressScreen.kt
│   │   └── PublishViewModel.kt
│   ├── decision/
│   │   ├── DecisionScreen.kt              # D-01…D-11, attribution, permanent record
│   │   └── DecisionViewModel.kt
│   ├── enrollment/
│   │   ├── EnrollmentScreen.kt            # device key registration + posture display
│   │   └── EnrollmentViewModel.kt
│   ├── settings/
│   │   ├── SettingsScreen.kt
│   │   └── SettingsViewModel.kt
│   └── common/
│       └── UiState.kt
│
└── util/
    ├── Logger.kt                          # redaction-aware; never logs capability signature/nonce
    └── Redactor.kt                        # mirrors gateway's redaction rules (§8)
```

2.2 Resources

```
res/
├── values/
│   ├── strings.xml                        # all operator-facing copy (no hardcoded text)
│   ├── themes.xml
│   └── colors.xml
├── xml/
│   ├── network_security_config.xml        # cleartext disabled; pin set for gateway host
│   ├── backup_rules.xml                   # nothing backed up
│   └── data_extraction_rules.xml          # nothing transferred off-device
├── drawable/  mipmap-*/                   # launcher icon
└── ...
```

2.3 Tests

```
src/test/kotlin/com/inscopelabs/buildengine/
├── capability/codec/OperatorCapabilityCodecTest.kt    # LF framing, base64url nopad, field order
├── capability/signing/CapabilitySignerTest.kt          # fake signer; verify-on-gateway round trip
├── capability/CapabilityMinterTest.kt                  # exp TTL, nonce uniqueness, branch regex
├── domain/usecase/PublishTaskUseCaseTest.kt            # refuses if head drifted before mint
├── data/remote/GatewayRepositoryTest.kt                # MockWebServer; V5–V8 error mapping
└── util/RedactorTest.kt

src/androidTest/kotlin/com/inscopelabs/buildengine/
├── crypto/DeviceKeyManagerTest.kt                     # key exists, non-exportable, auth-required
├── crypto/StrongBoxSupportTest.kt                     # honest posture report on emulator/device
├── crypto/KeystoreSignerTest.kt                       # sign then verify with public key
├── security/BiometricGateTest.kt                      # androidx.biometric test fakes
├── security/CertificatePinningTest.kt                 # pin mismatch rejected
└── ui/publish/PublishFlowTest.kt                      # Compose flow against fake gateway
```

---

3. Package-to-design mapping

Package Design review reference Why it lives there
core.crypto §4 (device keypair), §13 (StrongBox) Device key never leaves Keystore; honest isInsideSecureHardware reporting
core.security.BiometricGate §5, §13 Gate runs before minting — no capability exists until the operator confirms
core.security.DeviceAttestation §13 network posture mTLS / attestation so a proxy can't become a credential-capture point
core.security.CertificatePinning §13 Pin the gateway host
capability §5 (whole section) Model B by default (console signs, gateway verifies); Model A kept behind the CapabilitySigner interface for D-a
data.local.OperatorCredentialStore §4 credential classes Gateway-scoped credential only — never a GitHub token
data.local.SelectionStore §1 "selection state stays on device" Plain DataStore; not security state
domain.usecase.write §12 capability matrix One use case per privileged operation, each minting its own scoped capability
ui.publish §12, V1–V8 Shows branch + exact SHA + guard summary before signing
ui.decision D-e Highest-authority action; own confirmation + attribution
util.Redactor §8 Capability signature and nonce are added to the forbidden set

---

4. Deliberately absent from the app

These must not appear anywhere in the tree — their absence is a security property:

· ❌ Any GitHub token, PAT, OAuth token, or GitHub App credential (on-device or in prefs).
· ❌ A verify screen or verify.argv execution path (§12.1 — no subprocess, and plan-supplied commands stay off the gateway).
· ❌ bootstrap / init (§12.1 — terminal-only).
· ❌ A local git worktree, a shell bridge (Termux replacement), or Runtime.exec.
· ❌ Durable audit storage on-device (§8 — the audit trail lives in the private consumer repo).
· ❌ Session tokens persisted across launches (§9 — session is in memory only; the capability TTL is the real bound).

---

5. Build configuration highlights

app/build.gradle.kts:

· applicationId = "com.inscopelabs.buildengine"
· minSdk = 28 (Keystore StrongBox API surface; honest posture check still reports software-backed devices)
· targetSdk / compileSdk = current
· buildFeatures { compose = true }, Kotlin + KSP, Hilt
· signingConfigs from a secret store / CI, never committed
· buildTypes { release { isMinifyEnabled = true; isShrinkResources = true } }
· packaging { resources.excludes += … }

AndroidManifest.xml key attributes:

```xml
<application
    android:name=".BuildEngineApplication"
    android:allowBackup="false"
    android:dataExtractionRules="@xml/data_extraction_rules"
    android:fullBackupContent="false"
    android:usesCleartextTraffic="false"
    android:networkSecurityConfig="@xml/network_security_config"
    android:theme="@style/Theme.OperatorConsole" />
```

Permissions requested (and nothing else):

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.USE_BIOMETRIC" />
```

No storage, no location, no contacts, no REQUEST_INSTALL_PACKAGES.

Dependencies (via gradle/libs.versions.toml):

Concern Library
UI Compose BOM, Material 3, Navigation-Compose, Lifecycle-ViewModel-Compose
DI Hilt + KSP
Networking Ktor Client (OkHttp engine) + kotlinx.serialization, or Retrofit + OkHttp
mTLS / pinning OkHttp CertificatePinner + custom SSLSocketFactory
Biometrics androidx.biometric:biometric
Secure prefs androidx.security:security-crypto (EncryptedSharedPreferences)
Selection state androidx.datastore:datastore-preferences
Device attestation com.google.android.play:integrity
Coroutines kotlinx-coroutines-android
Test JUnit, MockK, Turbine, MockWebServer, Compose UI Test, androidx.biometric test fakes

proguard-rules.pro: keep OperatorCapabilityCodec field order stable and obfuscate the rest; never keep capability strings in logs.

---

6. Optional later split into Gradle modules

If the team grows, the package boundaries above translate 1:1 into modules without rewriting code:

```
:app                    → MainActivity, NavHost, DI wiring
:core:crypto            → core/crypto
:core:security          → core/security
:core:network           → core/network
:feature:read           → ui/dashboard, ui/plans, ui/taskdetail + read use cases
:feature:publish        → ui/publish + write use cases
:feature:enrollment     → ui/enrollment
:capability             → capability/
:domain                 → domain/
:data                   → data/
```

Keeping the packages clean now means this split is mechanical later — no re-architecture.

---

7. One-line summary

com.inscopelabs.buildengine is a thin, credential-minimal console: core.crypto holds the device key, capability mints the signed ctxop-v1 slip, core.security gates it behind biometrics and mTLS, data talks only to the gateway, and domain.usecase exposes exactly the 21 non-local commands from §12 — with verify and bootstrap/init deliberately left on a real machine.