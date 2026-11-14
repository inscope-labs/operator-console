# Agent Report — Stage 3.5: Confirmation Tier Hardening [AIS]

- **Timestamp (UTC):** 2026-11-14T05:40:00Z
- **Operator:** afterdawn.service (Juan Hunter)
- **Plan reference:** `RELEASE-PLAN.md» Stage 3.5 (T3-501, T3-502, T3-503); contract §3.21; BD-1.10; BD-3.1
- **Base commit:** 7ab2ac2 (Stage 3.3 closeout)
- **Rule compliance:** one stage per run (G-01); pushes direct to `main» per ADR 0010 (G-02); no protected paths touched (G-06); report filed per G-03.

## Pre-flight

- Read `RELEASE-PLAN.md» stage 3.5, contract §3.21, and the current `docs/STATUS.md» row.
- Found Stage 3.5 previously marked `implemented» on the strength of `AuthTierResolver»/`BiometricGate» alone; the per-operation policy, revocation surface and tests were absent. STATUS.md row corrected in this run.
- Stage 3.4 was still scaffold; the operator confirmed Stage 3.5 should proceed now, executed against the publish path. Stage 3.4 scaffold markers in `OperationRepositoryImpl.kt»/`OperationRepository.kt» were not touched.

## Operator decision recorded

- **BD-3.1 closed as option (a)** by the operator before implementation: staging and production accept both confirmation tiers for every operation, with one exception — DECIDE and RESUME require `hardware_confirm» *when the device can provide it*. A device without usable biometric hardware is never locked out, but the downgrade to `in_app_confirm» is explicit and visible, never silent.

## Implemented

1. **T3-501/T3-502 — policy + enforcement**
   - `core/security/ConfirmationTierPolicy.kt»: `TierDecision» + `resolve(op, enrolledTier)». DECIDE/RESUME require HARDWARE_CONFIRM when hardware is available; on devices without it, in_app_confirm proceeds with `explicitDowngrade = true». A provider failure is treated as hardware-unavailable (fail open to the degraded tier, never a crash). `explainForSummary» gives the static "tier and why" line.
   - `ConfirmAndMintCapabilityUseCase» Step 3b: the policy runs after the summary and before any confirmation gesture; a refusal returns `MintResult.TierNotAllowed(reason)». Existing PUBLISH and no-biometric behaviour is unchanged.
   - `PublishSummary.tierReason» rendered on the publish confirmation screen (`publish_tier_reason» test tag).
2. **T3-503 — gateway-side key revocation (§3.21, BD-1.10)**
   - `RevokeKeyDto.kt» (request/response), `GatewayEndpoints.ADMIN_REVOKE»/`adminRevoke()», `GatewayApi.revokeKey», `GatewayRepository.revokeKey».
   - `RevokeDeviceKeyUseCase»: typed confirmation `REVOKE <kid>»; blank reason refused before any network call; fail closed (G-08) — `DeviceEnrollmentStore.retire» and local key deletion happen only after the gateway confirms `revoked=true» for the same `kid». HTTP error → `GatewayRefused», transport error → `Unreachable», both leaving local state untouched.
   - `DeviceEnrollmentStore.retire(kid, now)» marks RETIRED and clears the active pointer when it pointed at the kid.

## Tests

- NEW `ConfirmationTierPolicyTest» (9): both tiers accepted for PUBLISH; DECIDE refuses in_app when hardware available; DECIDE allows hardware tier; explicit downgrade on no-biometric devices; RESUME governed like DECIDE; null tier refused when hardware available; throwing provider treated as hardware-unavailable; `explainForSummary» cases.
- NEW `RevokeDeviceKeyUseCaseTest» (8): success retires record + deletes key; typed confirmation format; blank reason refused pre-network; NotEnrolled; HTTP 403 → GatewayRefused with local state untouched; `revoked=false» → GatewayRefused untouched; kid mismatch → GatewayRefused untouched; IOException → Unreachable untouched.
- NEW `ConfirmAndMintTierEnforcementTest» (3): DECIDE + in_app tier + hardware available → TierNotAllowed; DECIDE + in_app tier without hardware → Minted; PUBLISH unaffected.
- **Not executed in-agent** (see below); CI on the pushed SHA is the verification of record.

## Commands run / tooling

No local Gradle, git or network access exists in the agent environment. All work was done via the GitHub contents API: file reads, then `push_files» commits to `main» (policy/DTO/use case, mint-path enforcement, endpoint+repository, store retire, UI tier-and-why line, tests, STATUS.md, this report).

## Defects found during the run

- One redundant push occurred: the publish summary UI files were re-pushed with content identical to what an earlier commit in this run had already landed (a context-compaction artifact). No content change resulted; disclosed here for the audit trail (G-08 honesty rule).

## Open items

- CI green on the final pushed SHA is required before the stage may be called closed (G-04). At report time CI had not completed; the STATUS.md row says `CI pending» accordingly.
- The NeedsReenrollment revocation UI flow (device management screen calling `RevokeDeviceKeyUseCase») is deferred to Stage 4.4; the use case plus `DeviceEnrollmentStore.retire» are the domain flow of record.
- Live gateway revocation against a deployed Gateway A is unverified (Stage 7.3 scope).

## Unverified in-agent (honest disclosure)

- No Gradle build or unit-test execution was possible in-agent: there is no build environment. Compile correctness of the pushed Kotlin rests on CI.
- androidTest / on-device behaviour (biometric gate, Keystore deletion) unverified; deferred to Stage 7.2 device acceptance.
- Live `POST /admin/revoke» behaviour against Gateway A unverified.

## Protected paths confirmation

No file under `gateway/**», `.github/workflows/*», `app/build.gradle.kts», `AndroidManifest.xml», `proguard-rules.pro», `gradle/libs.versions.toml», `version.properties», or `network_security_config.xml» was read for modification or pushed. Stage 3.4 scaffold markers remain untouched.
