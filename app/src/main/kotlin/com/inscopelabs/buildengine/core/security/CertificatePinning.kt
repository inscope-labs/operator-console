package com.inscopelabs.buildengine.core.security

import com.inscopelabs.buildengine.BuildConfig
import com.inscopelabs.buildengine.core.time.Clock
import com.inscopelabs.buildengine.core.time.SystemClock
import okhttp3.CertificatePinner
import okhttp3.Interceptor
import okhttp3.Response
import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Thrown when an active pin set's expiration date has passed.
 *
 * Implements BD-2.2 (expired pin sets must fail closed). Subclasses
 * [SSLPeerUnverifiedException] so standard TLS error handling catches it.
 */
class PinSetExpiredException(message: String) : SSLPeerUnverifiedException(message)

/**
 * Thrown when a connection to a remote host is attempted in release mode
 * without any configured certificate pins.
 *
 * Implements ADR 0007 / BD-2.1 (release builds refuse unpinned remote connections).
 */
class PinSetEmptyException(message: String) : SSLPeerUnverifiedException(message)

/**
 * Thrown when peer certificates do not match either primary or backup pins.
 */
class CertificatePinMismatchException(message: String) : SSLPeerUnverifiedException(message)

/**
 * Configuration for a single pinned host pattern.
 *
 * Per BD-2.1:
 * - At least one primary pin (matching the active key/chain in use).
 * - At least one backup pin (from a different chain, ready for rotation without lockout).
 * - Expiration timestamp per BD-2.2.
 */
data class HostPinConfig(
    val pattern: String,
    val primaryPins: List<String>,
    val backupPins: List<String>,
    val expiration: Instant,
    val description: String = "",
) {
    val allPins: List<String> = (primaryPins + backupPins).map { normalizePin(it) }.distinct()

    init {
        require(pattern.isNotBlank()) { "Pattern cannot be blank" }
        require(primaryPins.isNotEmpty()) { "At least one primary pin is required" }
        require(backupPins.isNotEmpty()) { "At least one backup pin is required (BD-2.1)" }
    }

    fun isExpired(now: Instant): Boolean = !now.isBefore(expiration)

    fun daysUntilExpiry(now: Instant): Long = ChronoUnit.DAYS.between(now, expiration)

    companion object {
        fun normalizePin(pin: String): String {
            val trimmed = pin.trim()
            return if (trimmed.startsWith("sha256/")) trimmed else "sha256/$trimmed"
        }
    }
}

/**
 * Result of validating a host's pin set against BD-2.2 rules.
 */
sealed class PinValidationResult {
    object Valid : PinValidationResult()
    data class Warning(val message: String, val daysRemaining: Long) : PinValidationResult()
    data class Expired(val message: String, val expiration: Instant) : PinValidationResult()
    data class NotPinned(val message: String) : PinValidationResult()
    data class InsufficientLifetime(
        val message: String,
        val daysRemaining: Long,
        val requiredDays: Long,
    ) : PinValidationResult()
}

/**
 * The certificate pin set and OkHttp pinner for the gateway host.
 *
 * Implements Stage 2.3 / T2-302, ADR 0003, ADR 0007, BD-2.1 and BD-2.2:
 * 1. Primary + backup pins: Cloudflare manages leaf certificates on `*.workers.dev`,
 *    so keys are pinned to intermediate and root CAs across distinct chains (BD-2.1).
 * 2. Pin expiration rule: Enforces pin set expiry checks. In release builds,
 *    pin sets expiring in less than 180 days are flagged per BD-2.2.
 * 3. Empty pins refusal in release: Connections to unpinned remote hosts in release
 *    mode are refused fail-closed with [PinSetEmptyException].
 * 4. Rotation support without app update: Dynamic runtime configuration updates
 *    via [setPinConfig] allow introducing or rotating pins without requiring an APK update.
 * 5. OkHttp integration: Produces an OkHttp [CertificatePinner] and an [Interceptor]
 *    for runtime enforcement on the Ktor/OkHttp transport engine.
 */
class CertificatePinning(
    private val clock: Clock = SystemClock(),
    val isReleaseMode: Boolean = !BuildConfig.DEBUG,
    initialConfigs: List<HostPinConfig> = defaultPinConfigs(),
) {
    private val configs = ConcurrentHashMap<String, HostPinConfig>()

    init {
        for (config in initialConfigs) {
            configs[config.pattern.lowercase()] = config
        }
    }

    /** Returns all configured pins (primary + backup) for the host or pattern. */
    fun pinsFor(host: String): List<String> {
        val config = findMatchingConfig(host)
        return config?.allPins ?: emptyList()
    }

    /** Returns true if the host matches any configured pin set. */
    fun isPinned(host: String): Boolean = pinsFor(host).isNotEmpty()

    /** Returns the matched [HostPinConfig] for the host, or null. */
    fun getPinConfig(host: String): HostPinConfig? = findMatchingConfig(host)

    /** Returns all registered pin configurations. */
    fun allConfigs(): List<HostPinConfig> = configs.values.toList()

    /**
     * Dynamically registers or updates a pin configuration at runtime.
     * Supports pin rotation without an app update.
     */
    fun setPinConfig(config: HostPinConfig) {
        configs[config.pattern.lowercase()] = config
    }

    /** Removes a pin configuration for a pattern. */
    fun removePinConfig(pattern: String) {
        configs.remove(pattern.lowercase())
    }

    /** Clears all configurations and restores default pins. */
    fun resetToDefaults() {
        configs.clear()
        for (config in defaultPinConfigs()) {
            configs[config.pattern.lowercase()] = config
        }
    }

    /**
     * Validates the pin set for [host] against BD-2.2 lifetime rules.
     *
     * @param host Target hostname.
     * @param now Current time.
     * @param requiredRemainingDays Minimum days remaining required for release (BD-2.2 default: 180).
     * @param warningRemainingDays Threshold for CI/operator warning (BD-2.2 default: 60).
     */
    fun validatePinSet(
        host: String,
        now: Instant = clockNow(),
        requiredRemainingDays: Long = 180L,
        warningRemainingDays: Long = 60L,
    ): PinValidationResult {
        val config = findMatchingConfig(host)
            ?: return PinValidationResult.NotPinned("No certificate pins configured for host '$host'")

        if (config.isExpired(now)) {
            return PinValidationResult.Expired(
                "Certificate pin set for host '$host' expired on ${config.expiration}",
                config.expiration,
            )
        }

        val daysRemaining = config.daysUntilExpiry(now)
        if (isReleaseMode && daysRemaining < requiredRemainingDays) {
            return PinValidationResult.InsufficientLifetime(
                "Certificate pin set for host '$host' expires in $daysRemaining days (less than required $requiredRemainingDays days per BD-2.2)",
                daysRemaining = daysRemaining,
                requiredDays = requiredRemainingDays,
            )
        }

        if (daysRemaining <= warningRemainingDays) {
            return PinValidationResult.Warning(
                "Certificate pin set for host '$host' expires in $daysRemaining days (warning threshold: $warningRemainingDays days)",
                daysRemaining = daysRemaining,
            )
        }

        return PinValidationResult.Valid
    }

    /**
     * Verifies the peer certificate chain for [host] against configured pins.
     *
     * @throws PinSetEmptyException if no pins are configured for a remote host in release mode.
     * @throws PinSetExpiredException if the matching pin set has expired.
     * @throws CertificatePinMismatchException if peer certificates do not match any pin.
     */
    fun check(
        host: String,
        peerCertificates: List<Certificate>,
        now: Instant = clockNow(),
    ) {
        if (!isReleaseMode && isLocalHost(host)) {
            return // Local emulator/debug bypass
        }

        val config = findMatchingConfig(host)
        if (config == null) {
            if (isReleaseMode) {
                throw PinSetEmptyException(
                    "Certificate pinning is required in release mode, but no pins configured for host: $host",
                )
            }
            return
        }

        if (config.isExpired(now)) {
            throw PinSetExpiredException(
                "Certificate pin set for host '$host' expired on ${config.expiration} (current: $now)",
            )
        }

        if (peerCertificates.isEmpty()) {
            throw CertificatePinMismatchException("Peer certificate chain is empty for host '$host'")
        }

        val peerPins = peerCertificates.map { calculateSpkiPin(it) }
        val matchedPin = peerPins.firstOrNull { it in config.allPins }

        if (matchedPin == null) {
            throw CertificatePinMismatchException(
                "Certificate pinning failure for host '$host'! " +
                    "Peer pins: [${peerPins.joinToString()}], " +
                    "Pinned: [${config.allPins.joinToString()}]",
            )
        }
    }

    /**
     * Builds an OkHttp [CertificatePinner] enforcing all active, unexpired pin sets.
     */
    fun buildCertificatePinner(now: Instant = clockNow()): CertificatePinner {
        val builder = CertificatePinner.Builder()
        for (config in configs.values) {
            if (!config.isExpired(now)) {
                builder.add(config.pattern, *config.allPins.toTypedArray())
                // If pattern is *.domain.com, also add domain.com for OkHttp exact match
                if (config.pattern.startsWith("*.")) {
                    val rootDomain = config.pattern.removePrefix("*.")
                    builder.add(rootDomain, *config.allPins.toTypedArray())
                }
            }
        }
        return builder.build()
    }

    /**
     * Creates an OkHttp [Interceptor] that performs preflight expiry/release checks
     * and validates peer certificates post-handshake.
     */
    fun asInterceptor(): Interceptor = Interceptor { chain ->
        val request = chain.request()
        val host = request.url.host

        // 1. Preflight check: Localhost exempt in debug
        if (!(!isReleaseMode && isLocalHost(host))) {
            val config = findMatchingConfig(host)
            if (config == null && isReleaseMode) {
                throw PinSetEmptyException(
                    "Certificate pinning is required in release mode, but no pins configured for host: $host",
                )
            }
            if (config != null && config.isExpired(clockNow())) {
                throw PinSetExpiredException(
                    "Certificate pin set for host '$host' expired on ${config.expiration}",
                )
            }
        }

        // 2. Execute call
        val response = chain.proceed(request)

        // 3. Post-handshake verification if TLS handshake took place
        val handshake = response.handshake
        if (handshake != null && handshake.peerCertificates.isNotEmpty()) {
            check(host, handshake.peerCertificates, clockNow())
        }

        response
    }

    private fun clockNow(): Instant = Instant.ofEpochSecond(clock.nowEpochSeconds())

    private fun findMatchingConfig(host: String): HostPinConfig? {
        val normalizedHost = host.lowercase().trimEnd('.')
        // 1. Exact match
        configs[normalizedHost]?.let { return it }

        // 2. Wildcard match (most specific pattern wins)
        return configs.values
            .filter { matchesPattern(it.pattern, normalizedHost) }
            .maxByOrNull { it.pattern.length }
    }

    companion object {
        /**
         * Calculates the SPKI SHA-256 pin for a [Certificate] in OkHttp standard format (`sha256/<base64>`).
         */
        fun calculateSpkiPin(certificate: Certificate): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(certificate.publicKey.encoded)
            return "sha256/" + Base64.getEncoder().encodeToString(digest)
        }

        fun matchesPattern(pattern: String, hostname: String): Boolean {
            val host = hostname.lowercase().trimEnd('.')
            val pat = pattern.lowercase().trimEnd('.')
            if (pat == host || pat == "**" || pat == "*") return true
            if (pat.startsWith("*.")) {
                val suffix = pat.removePrefix("*.")
                if (host == suffix || host.endsWith(".$suffix")) return true
            }
            return false
        }

        fun isLocalHost(host: String): Boolean {
            val h = host.lowercase().trimEnd('.')
            return h == "10.0.2.2" || h == "localhost" || h == "127.0.0.1" || h == "::1"
        }

        /**
         * Default SPKI pins for Cloudflare Workers (workers.dev zone).
         *
         * Per BD-2.1:
         * - Primary chain: Google Trust Services (GTS Root R1 & GTS CA 1P5)
         * - Backup chain: DigiCert Global Root G2 & Let's Encrypt ISRG Root X1
         * - Expiration set per BD-2.2 (> 180 days from build baseline).
         */
        val DEFAULT_WORKERS_DEV_CONFIG = HostPinConfig(
            pattern = "*.workers.dev",
            primaryPins = listOf(
                "sha256/hxqRlPTuQZQXLjt3o0+/JZaMcj79nlSx4vdwNY+wizg=", // Google Trust Services Root R1
                "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hE2gw9ahYRbxI=", // Google Trust Services GTS CA 1P5
            ),
            backupPins = listOf(
                "sha256/i7WTqTvh0OioIruIfFR4kRnetJ3GMTVI2fQvfEQPpZ8=", // DigiCert Global Root G2
                "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=", // Let's Encrypt ISRG Root X1
            ),
            expiration = Instant.parse("2028-01-01T00:00:00Z"),
            description = "Cloudflare Workers Default CA Chains (BD-2.1)",
        )

        val DEFAULT_QNURALINK_CONFIG = HostPinConfig(
            pattern = "qnuralink.workers.dev",
            primaryPins = listOf(
                "sha256/hxqRlPTuQZQXLjt3o0+/JZaMcj79nlSx4vdwNY+wizg=",
                "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hE2gw9ahYRbxI=",
            ),
            backupPins = listOf(
                "sha256/i7WTqTvh0OioIruIfFR4kRnetJ3GMTVI2fQvfEQPpZ8=",
                "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=",
            ),
            expiration = Instant.parse("2028-01-01T00:00:00Z"),
            description = "Gateway A Production/Staging Zone Pin Set (ADR 0007 / ADR 0010)",
        )

        fun defaultPinConfigs(): List<HostPinConfig> = listOf(
            DEFAULT_WORKERS_DEV_CONFIG,
            DEFAULT_QNURALINK_CONFIG,
        )
    }
}
