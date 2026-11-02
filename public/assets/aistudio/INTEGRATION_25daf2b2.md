# Integration instructions

Apply these changes on a review branch; do not overwrite unrelated operator work.

## 1. Replace manifest-listed files

Copy the files in `app/src/main/...` over the matching paths in the repository. Preserve the package paths exactly.

## 2. Resolve the enrollment-store path discrepancy

The supplied stage manifest requires:
`app/src/main/kotlin/com/inscopelabs/buildengine/data/enrollment/DeviceEnrollmentStore.kt`

The currently inspected repository has a scaffold at:
`app/src/main/kotlin/com/inscopelabs/buildengine/data/local/DeviceEnrollmentStore.kt`

Use the new `data/enrollment` path and remove the old `data/local` placeholder only after updating all imports. In `DataModule.kt`:
```kotlin
import com.inscopelabs.buildengine.data.enrollment.DeviceEnrollmentStore
```
and keep the existing singleton provider, now calling:
```kotlin
fun provideDeviceEnrollmentStore(
    @ApplicationContext context: Context,
): DeviceEnrollmentStore = DeviceEnrollmentStore(context)
```

## 3. Update `CryptoModule.kt`

Replace the no-argument key-manager provider with:
```kotlin
@Provides
@Singleton
fun provideDeviceKeyManager(
    @ApplicationContext context: Context,
): DeviceKeyManager = DeviceKeyManager(context)

@Provides
@Singleton
fun provideStrongBoxSupport(
    @ApplicationContext context: Context,
): StrongBoxSupport = StrongBoxSupport(context)

@Provides
@Singleton
fun provideKeystoreSigner(
    deviceKeyManager: DeviceKeyManager,
): KeystoreSigner = KeystoreSigner(deviceKeyManager)
```
Keep the existing `CapabilitySigner` binding only after Stage 4.2 provides the CryptoObject-bound signing flow. Do not treat `KeystoreSigner`'s fail-closed default as a completed biometric integration.

## 4. Update `UseCaseModule.kt`

Replace the three existing Stage 4.1 providers with:
```kotlin
@Provides
@Singleton
fun provideEnrollDeviceUseCase(
    deviceKeyManager: DeviceKeyManager,
    deviceAttestation: DeviceAttestation,
    gatewayApi: GatewayApi,
    enrollmentStore: DeviceEnrollmentStore,
): EnrollDeviceUseCase =
    EnrollDeviceUseCase(deviceKeyManager, deviceAttestation, gatewayApi, enrollmentStore)

@Provides
@Singleton
fun provideRotateDeviceKeyUseCase(
    deviceKeyManager: DeviceKeyManager,
    deviceAttestation: DeviceAttestation,
    gatewayApi: GatewayApi,
    enrollmentStore: DeviceEnrollmentStore,
): RotateDeviceKeyUseCase =
    RotateDeviceKeyUseCase(deviceKeyManager, deviceAttestation, gatewayApi, enrollmentStore)

@Provides
@Singleton
fun provideReportDevicePostureUseCase(
    strongBoxSupport: StrongBoxSupport,
    deviceAttestation: DeviceAttestation,
): ReportDevicePostureUseCase =
    ReportDevicePostureUseCase(strongBoxSupport, deviceAttestation)
```
Add the matching imports. `GatewayApi` is already bound by the existing network/data modules.

## 5. Ensure `DeviceAttestation` receives a cryptographically secure challenge

`DeviceKeySpec` accepts an optional attestation challenge. Production generation must provide a fresh unpredictable challenge. The current `/enroll` DTO has no challenge field or challenge endpoint, so do not invent a server-side challenge protocol in this client package. Agree the gateway's validation semantics first. The current implementation supports retrieving the certificate chain for a key created with a challenge.

## 6. UI wiring

`EnrollmentScreen` obtains its `EnrollmentViewModel` through Hilt. `EnrollmentViewModel` calls the three use cases, exposes only non-secret state, and does not expose private key material or the operator credential. `AppNavHost` already includes `Routes.ENROLLMENT`; no navigation change is needed.

## 7. Remove or update stale imports

- Remove `com.inscopelabs.buildengine.data.local.DeviceEnrollmentStore`.
- Remove the old `DeviceEnrollmentStore` provider signature from `DataModule`.
- Update `CryptoModule` providers as above.
- Update `UseCaseModule` providers as above.

## 8. Tests and verification

Run:
```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```
Then inspect key properties on a real device:
- private key is not exportable;
- user authentication is required for each signing operation;
- biometric enrollment invalidates the key;
- StrongBox status reflects actual `KeyInfo`;
- a non-StrongBox device reports TEE/software backing honestly;
- missing key-attestation chain refuses enrolment;
- failed remote enrolment leaves no active local enrolment;
- failed rotation preserves the previous active enrolment and private key.
