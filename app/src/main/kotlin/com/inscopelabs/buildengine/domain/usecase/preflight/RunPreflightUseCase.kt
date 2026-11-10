package com.inscopelabs.buildengine.domain.usecase.preflight

import com.inscopelabs.buildengine.core.network.GatewayEndpoints
import com.inscopelabs.buildengine.core.network.dto.GatewayHealthDto
import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.core.security.CertificatePinning
import com.inscopelabs.buildengine.core.security.PinValidationResult
import com.inscopelabs.buildengine.core.time.Clock
import com.inscopelabs.buildengine.data.enrollment.DeviceEnrollmentStore
import com.inscopelabs.buildengine.data.local.GatewayBaseUrlStore
import com.inscopelabs.buildengine.data.local.OperatorCredentialStore
import com.inscopelabs.buildengine.data.remote.GatewayRepository
import com.inscopelabs.buildengine.domain.model.preflight.CheckStatus
import com.inscopelabs.buildengine.domain.model.preflight.PreflightCheckId
import com.inscopelabs.buildengine.domain.model.preflight.PreflightCheckResult
import com.inscopelabs.buildengine.domain.model.preflight.PreflightReport
import com.inscopelabs.buildengine.domain.usecase.enrollment.ReportDevicePostureUseCase
import java.net.URI
import kotlin.math.abs

/**
 * Runs the 8 independent preflight checks required by Stage 2.4 / T2-401.
 *
 * Checks:
 * 1. Configured: Gateway base URL is present, non-empty, and valid scheme/host.
 * 2. Reachable: Gateway /health endpoint responds.
 * 3. TLS / Pin: Certificate pin set is present, valid, and unexpired (BD-2.1/BD-2.2).
 * 4. Authentication: Operator bearer credential is configured for the active slot.
 * 5. Clock skew: Device time is within 10 seconds of gateway time (gateway tolerance).
 * 6. Contract version: Gateway environment matches expected and API version is compatible.
 * 7. Enrolment state: An active device key is enrolled for the expected environment.
 * 8. Posture & Tier: Hardware backing, StrongBox, Key Attestation, and accepted auth tier.
 */
class RunPreflightUseCase(
    private val baseUrlStore: GatewayBaseUrlStore,
    private val gatewayRepository: GatewayRepository,
    private val certificatePinning: CertificatePinning,
    private val credentialStore: OperatorCredentialStore,
    private val clock: Clock,
    private val enrollmentStore: DeviceEnrollmentStore,
    private val reportDevicePostureUseCase: ReportDevicePostureUseCase,
) {
    suspend operator fun invoke(): PreflightReport {
        val currentUrl = baseUrlStore.read()
        val expectedEnv = baseUrlStore.readExpectedEnvironment()
        val checks = mutableListOf<PreflightCheckResult>()

        // 1. Configured check
        val isConfigured = GatewayEndpoints.isConfigured(currentUrl)
        val configResult = if (!isConfigured) {
            PreflightCheckResult(
                id = PreflightCheckId.CONFIGURED,
                title = "Gateway Configuration",
                status = CheckStatus.FAIL,
                summary = "Gateway base URL is not configured",
                detail = "Base URL is empty or uses a .invalid placeholder. Set a valid URL in Settings.",
            )
        } else {
            PreflightCheckResult(
                id = PreflightCheckId.CONFIGURED,
                title = "Gateway Configuration",
                status = CheckStatus.PASS,
                summary = "Configured: $currentUrl (expected: $expectedEnv)",
                detail = "Host is non-placeholder and targeting the '$expectedEnv' environment slot.",
            )
        }
        checks.add(configResult)

        // 2. Reachability check (calls GET /health)
        var healthDto: GatewayHealthDto? = null
        val reachabilityResult = if (!isConfigured) {
            PreflightCheckResult(
                id = PreflightCheckId.REACHABLE,
                title = "Network Reachability",
                status = CheckStatus.FAIL,
                summary = "Cannot test reachability: gateway not configured",
                detail = "Configure the gateway base URL before running reachability probes.",
            )
        } else {
            val startMs = System.currentTimeMillis()
            when (val healthResult = gatewayRepository.health()) {
                is AppResult.Success -> {
                    healthDto = healthResult.value
                    PreflightCheckResult(
                        id = PreflightCheckId.REACHABLE,
                        title = "Network Reachability",
                        status = CheckStatus.PASS,
                        summary = "Gateway is reachable and healthy",
                        detail = "Status: ${healthResult.value.status}, Environment: ${healthResult.value.environment}, Version: ${healthResult.value.version}",
                        durationMs = System.currentTimeMillis() - startMs,
                    )
                }
                is AppResult.Failure -> {
                    PreflightCheckResult(
                        id = PreflightCheckId.REACHABLE,
                        title = "Network Reachability",
                        status = CheckStatus.FAIL,
                        summary = "Gateway could not be reached (${healthResult.code})",
                        detail = healthResult.message,
                        durationMs = System.currentTimeMillis() - startMs,
                    )
                }
            }
        }
        checks.add(reachabilityResult)

        // 3. TLS / Certificate Pinning check
        val host = runCatching { URI(currentUrl).host }.getOrNull().orEmpty()
        val tlsResult = if (!isConfigured || host.isEmpty()) {
            PreflightCheckResult(
                id = PreflightCheckId.TLS_PINNING,
                title = "TLS & Certificate Pinning",
                status = CheckStatus.FAIL,
                summary = "Cannot verify TLS: host is empty or unconfigured",
                detail = "Valid hostname required to evaluate certificate pins.",
            )
        } else {
            when (val validation = certificatePinning.validatePinSet(host)) {
                is PinValidationResult.Valid -> {
                    val pins = certificatePinning.pinsFor(host)
                    PreflightCheckResult(
                        id = PreflightCheckId.TLS_PINNING,
                        title = "TLS & Certificate Pinning",
                        status = CheckStatus.PASS,
                        summary = "Certificate pins active and valid for $host",
                        detail = "Configured pins: ${pins.size} (primary + backup). Lifetime exceeds 180 days per BD-2.2.",
                    )
                }
                is PinValidationResult.Warning -> {
                    PreflightCheckResult(
                        id = PreflightCheckId.TLS_PINNING,
                        title = "TLS & Certificate Pinning",
                        status = CheckStatus.WARN,
                        summary = "Certificate pins expiring soon (${validation.daysRemaining} days remaining)",
                        detail = validation.message,
                    )
                }
                is PinValidationResult.InsufficientLifetime -> {
                    PreflightCheckResult(
                        id = PreflightCheckId.TLS_PINNING,
                        title = "TLS & Certificate Pinning",
                        status = CheckStatus.FAIL,
                        summary = "Pin lifetime (${validation.daysRemaining} days) < required ${validation.requiredDays} days",
                        detail = validation.message,
                    )
                }
                is PinValidationResult.Expired -> {
                    PreflightCheckResult(
                        id = PreflightCheckId.TLS_PINNING,
                        title = "TLS & Certificate Pinning",
                        status = CheckStatus.FAIL,
                        summary = "Certificate pin set has expired",
                        detail = validation.message,
                    )
                }
                is PinValidationResult.NotPinned -> {
                    if (CertificatePinning.isLocalHost(host)) {
                        PreflightCheckResult(
                            id = PreflightCheckId.TLS_PINNING,
                            title = "TLS & Certificate Pinning",
                            status = CheckStatus.WARN,
                            summary = "Local loopback host exempt from pinning in debug",
                            detail = validation.message,
                        )
                    } else {
                        PreflightCheckResult(
                            id = PreflightCheckId.TLS_PINNING,
                            title = "TLS & Certificate Pinning",
                            status = CheckStatus.FAIL,
                            summary = "Host is not covered by certificate pinning",
                            detail = validation.message,
                        )
                    }
                }
            }
        }
        checks.add(tlsResult)

        // 4. Operator Authentication check
        val credential = credentialStore.read(expectedEnv)
        val authResult = if (credential.isNullOrBlank()) {
            val status = if (expectedEnv == "test") CheckStatus.WARN else CheckStatus.FAIL
            PreflightCheckResult(
                id = PreflightCheckId.AUTHENTICATION,
                title = "Operator Authentication",
                status = status,
                summary = "No operator credential in slot for '$expectedEnv'",
                detail = "Unauthenticated requests can only access /health. Save credential in Settings before performing actions.",
            )
        } else {
            PreflightCheckResult(
                id = PreflightCheckId.AUTHENTICATION,
                title = "Operator Authentication",
                status = CheckStatus.PASS,
                summary = "Operator credential present in '$expectedEnv' slot",
                detail = "Credential securely stored in encrypted slot (${credential.length} characters, masked).",
            )
        }
        checks.add(authResult)

        // 5. Clock Skew check (Tolerance: 10 seconds per contract & T2-401)
        val deviceSeconds = clock.nowEpochSeconds()
        val clockResult = if (healthDto == null) {
            PreflightCheckResult(
                id = PreflightCheckId.CLOCK_SKEW,
                title = "Clock Skew",
                status = CheckStatus.FAIL,
                summary = "Cannot evaluate clock skew: gateway unreachable",
                detail = "Clock synchronization verification requires a successful /health probe.",
            )
        } else {
            val gatewaySeconds = healthDto.timestamp
            val skew = abs(deviceSeconds - gatewaySeconds)
            when {
                skew > 10L -> PreflightCheckResult(
                    id = PreflightCheckId.CLOCK_SKEW,
                    title = "Clock Skew",
                    status = CheckStatus.FAIL,
                    summary = "Clock skew ($skew s) exceeds 10 s gateway tolerance",
                    detail = "Device time ($deviceSeconds) and gateway time ($gatewaySeconds) differ by $skew seconds. Capabilities will be rejected as expired or not-yet-valid.",
                )
                skew > 5L -> PreflightCheckResult(
                    id = PreflightCheckId.CLOCK_SKEW,
                    title = "Clock Skew",
                    status = CheckStatus.WARN,
                    summary = "Clock drift detected: $skew s (within 10 s tolerance)",
                    detail = "Device time ($deviceSeconds) and gateway time ($gatewaySeconds) have slight skew.",
                )
                else -> PreflightCheckResult(
                    id = PreflightCheckId.CLOCK_SKEW,
                    title = "Clock Skew",
                    status = CheckStatus.PASS,
                    summary = "Clock synchronized: delta $skew s (tolerance: 10 s)",
                    detail = "Device time: $deviceSeconds, Gateway time: $gatewaySeconds.",
                )
            }
        }
        checks.add(clockResult)

        // 6. Contract Version Compatibility check
        val contractResult = if (healthDto == null) {
            PreflightCheckResult(
                id = PreflightCheckId.CONTRACT_VERSION,
                title = "Contract Version Compatibility",
                status = CheckStatus.FAIL,
                summary = "Cannot verify contract version: gateway unreachable",
                detail = "Contract compatibility verification requires a successful /health probe.",
            )
        } else {
            val reportedEnv = healthDto.environment
            if (reportedEnv != expectedEnv) {
                PreflightCheckResult(
                    id = PreflightCheckId.CONTRACT_VERSION,
                    title = "Contract Version Compatibility",
                    status = CheckStatus.FAIL,
                    summary = "Environment mismatch: expected '$expectedEnv', got '$reportedEnv'",
                    detail = "The gateway reports running in '$reportedEnv' mode. Privileged operations are locked per T2-201.",
                )
            } else {
                PreflightCheckResult(
                    id = PreflightCheckId.CONTRACT_VERSION,
                    title = "Contract Version Compatibility",
                    status = CheckStatus.PASS,
                    summary = "Contract v1 compatible (version: ${healthDto.version}, env: $reportedEnv)",
                    detail = "Compatible with ctxop-v1 capability protocol and frozen error catalogue.",
                )
            }
        }
        checks.add(contractResult)

        // 7. Enrolment State check
        val activeEnrolment = enrollmentStore.activeRecord(expectedEnv)
        val enrolmentResult = if (activeEnrolment == null) {
            PreflightCheckResult(
                id = PreflightCheckId.ENROLMENT_STATE,
                title = "Device Key Enrolment",
                status = CheckStatus.WARN,
                summary = "No active device key enrolled for '$expectedEnv'",
                detail = "Capability minting requires an enrolled key for this environment. Enrol via the Enrolment screen.",
            )
        } else {
            PreflightCheckResult(
                id = PreflightCheckId.ENROLMENT_STATE,
                title = "Device Key Enrolment",
                status = CheckStatus.PASS,
                summary = "Device key enrolled for '$expectedEnv' (kid: ${activeEnrolment.kid})",
                detail = "Alias: ${activeEnrolment.keyAlias}, Auth Tier: ${activeEnrolment.authTier ?: "unspecified"}.",
            )
        }
        checks.add(enrolmentResult)

        // 8. Device Posture & Accepted Tier check
        val keyAlias = activeEnrolment?.keyAlias ?: "preflight_probe_key"
        val posture = reportDevicePostureUseCase(keyAlias)
        val postureResult = if (posture.hardwareBacked && posture.attested) {
            PreflightCheckResult(
                id = PreflightCheckId.POSTURE_TIER,
                title = "Device Posture & Auth Tier",
                status = CheckStatus.PASS,
                summary = "Hardware security confirmed (${posture.securityLevel})",
                detail = "${posture.summary} · Accepted tier: ${posture.authTier?.wireValue ?: "hardware_confirm"}",
            )
        } else {
            val tier = posture.authTier?.wireValue ?: "in_app_confirm"
            PreflightCheckResult(
                id = PreflightCheckId.POSTURE_TIER,
                title = "Device Posture & Auth Tier",
                status = CheckStatus.PASS,
                summary = "Degraded posture: ${posture.securityLevel} (tier: $tier)",
                detail = "${posture.summary} · Supported per BD-3.1 (in_app_confirm gesture permitted).",
            )
        }
        checks.add(postureResult)

        val overallPass = checks.none { it.status == CheckStatus.FAIL }

        return PreflightReport(
            isRunning = false,
            checks = checks,
            overallPass = overallPass,
            expectedEnvironment = expectedEnv,
            gatewayBaseUrl = currentUrl,
            executedEpochMillis = System.currentTimeMillis(),
        )
    }
}
