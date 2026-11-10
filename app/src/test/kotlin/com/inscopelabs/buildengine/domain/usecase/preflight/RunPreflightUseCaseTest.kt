package com.inscopelabs.buildengine.domain.usecase.preflight

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.inscopelabs.buildengine.core.crypto.StrongBoxSupport
import com.inscopelabs.buildengine.core.network.ErrorMapper
import com.inscopelabs.buildengine.core.network.GatewayHttpClient
import com.inscopelabs.buildengine.core.security.AuthTier
import com.inscopelabs.buildengine.core.security.CertificatePinning
import com.inscopelabs.buildengine.core.security.PinValidationResult
import com.inscopelabs.buildengine.core.time.FixedClock
import com.inscopelabs.buildengine.data.enrollment.DeviceEnrollmentStore
import com.inscopelabs.buildengine.data.local.GatewayBaseUrlStore
import com.inscopelabs.buildengine.data.local.OperatorCredentialStore
import com.inscopelabs.buildengine.data.remote.GatewayRepository
import com.inscopelabs.buildengine.domain.model.preflight.CheckStatus
import com.inscopelabs.buildengine.domain.model.preflight.PreflightCheckId
import com.inscopelabs.buildengine.domain.usecase.enrollment.ReportDevicePostureUseCase
import com.inscopelabs.buildengine.testutil.FakeDeviceAttestation
import com.inscopelabs.buildengine.testutil.FakeDeviceKeyManager
import com.inscopelabs.buildengine.testutil.FakeSharedPreferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RunPreflightUseCaseTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var httpClient: HttpClient
    private lateinit var gatewayRepository: GatewayRepository
    private lateinit var baseUrlStore: GatewayBaseUrlStore
    private lateinit var credentialStore: OperatorCredentialStore
    private lateinit var enrollmentStore: DeviceEnrollmentStore
    private lateinit var clock: FixedClock
    private lateinit var certificatePinning: CertificatePinning
    private lateinit var reportDevicePostureUseCase: ReportDevicePostureUseCase
    private lateinit var deviceKeyManager: FakeDeviceKeyManager
    private lateinit var deviceAttestation: FakeDeviceAttestation

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        httpClient = HttpClient(OkHttp) { expectSuccess = false }
        gatewayRepository = GatewayRepository(
            httpClient = GatewayHttpClient(httpClient, baseUrlProvider = { server.url("/").toString() }),
            errorMapper = ErrorMapper(),
        )

        val prefsFile = File(temporaryFolder.root, "gateway_url.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { prefsFile })
        baseUrlStore = GatewayBaseUrlStore(dataStore = dataStore)

        credentialStore = OperatorCredentialStore(context = null, preferences = FakeSharedPreferences())
        enrollmentStore = DeviceEnrollmentStore(preferences = FakeSharedPreferences())
        clock = FixedClock(1761840000L) // Fixed baseline
        certificatePinning = CertificatePinning(clock = clock)

        deviceKeyManager = FakeDeviceKeyManager()
        deviceAttestation = FakeDeviceAttestation(deviceKeyManager)
        val strongBoxSupport = StrongBoxSupport(
            hardwareBackedOverride = { true },
            securityLevelOverride = { "STRONGBOX" },
            strongBoxAvailableOverride = true,
        )
        reportDevicePostureUseCase = ReportDevicePostureUseCase(
            strongBoxSupport = strongBoxSupport,
            deviceAttestation = deviceAttestation,
            enrollmentStore = enrollmentStore,
            certificatePinning = certificatePinning,
            baseUrlStore = baseUrlStore,
        )
    }

    @After
    fun tearDown() {
        httpClient.close()
        server.shutdown()
    }

    private fun createUseCase(): RunPreflightUseCase = RunPreflightUseCase(
        baseUrlStore = baseUrlStore,
        gatewayRepository = gatewayRepository,
        certificatePinning = certificatePinning,
        credentialStore = credentialStore,
        clock = clock,
        enrollmentStore = enrollmentStore,
        reportDevicePostureUseCase = reportDevicePostureUseCase,
    )

    @Test
    fun `all 8 checks pass when fully configured and healthy`() = runTest {
        val serverUrl = server.url("/").toString().removeSuffix("/")
        baseUrlStore.save(serverUrl)
        baseUrlStore.saveExpectedEnvironment("test")
        credentialStore.save("op_secret_token_12345", environment = "test")

        enrollmentStore.recordPending(
            kid = "kid_1",
            keyAlias = "test-key-alias",
            publicKeyBase64 = "pub_key_base64_1",
            now = 1761840000L,
            authTier = AuthTier.HARDWARE_CONFIRM.wireValue,
            environment = "test",
        )
        enrollmentStore.activate("kid_1", now = 1761840000L, environment = "test")

        // Gateway /health endpoint response matching test environment and identical timestamp
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"ok","environment":"test","version":"1.0.0","timestamp":1761840000}""")
        )

        val useCase = createUseCase()
        val report = useCase()

        assertEquals(8, report.checks.size)
        assertTrue(report.overallPass == true)

        val configured = report.checks.first { it.id == PreflightCheckId.CONFIGURED }
        assertEquals(CheckStatus.PASS, configured.status)

        val reachable = report.checks.first { it.id == PreflightCheckId.REACHABLE }
        assertEquals(CheckStatus.PASS, reachable.status)

        val tls = report.checks.first { it.id == PreflightCheckId.TLS_PINNING }
        assertEquals(CheckStatus.WARN, tls.status) // Local loopback is exempt/warn in test

        val auth = report.checks.first { it.id == PreflightCheckId.AUTHENTICATION }
        assertEquals(CheckStatus.PASS, auth.status)

        val skew = report.checks.first { it.id == PreflightCheckId.CLOCK_SKEW }
        assertEquals(CheckStatus.PASS, skew.status)

        val contract = report.checks.first { it.id == PreflightCheckId.CONTRACT_VERSION }
        assertEquals(CheckStatus.PASS, contract.status)

        val enrolment = report.checks.first { it.id == PreflightCheckId.ENROLMENT_STATE }
        assertEquals(CheckStatus.PASS, enrolment.status)

        val posture = report.checks.first { it.id == PreflightCheckId.POSTURE_TIER }
        assertEquals(CheckStatus.PASS, posture.status)
    }

    @Test
    fun `not configured fails CONFIGURED and REACHABLE checks`() = runTest {
        // Leave baseUrlStore unconfigured ("")
        val useCase = createUseCase()
        val report = useCase()

        assertFalse(report.overallPass == true)

        val configured = report.checks.first { it.id == PreflightCheckId.CONFIGURED }
        assertEquals(CheckStatus.FAIL, configured.status)

        val reachable = report.checks.first { it.id == PreflightCheckId.REACHABLE }
        assertEquals(CheckStatus.FAIL, reachable.status)

        val tls = report.checks.first { it.id == PreflightCheckId.TLS_PINNING }
        assertEquals(CheckStatus.FAIL, tls.status)

        val skew = report.checks.first { it.id == PreflightCheckId.CLOCK_SKEW }
        assertEquals(CheckStatus.FAIL, skew.status)

        val contract = report.checks.first { it.id == PreflightCheckId.CONTRACT_VERSION }
        assertEquals(CheckStatus.FAIL, contract.status)
    }

    @Test
    fun `clock skew exceeding 10s tolerance fails CLOCK_SKEW`() = runTest {
        val serverUrl = server.url("/").toString().removeSuffix("/")
        baseUrlStore.save(serverUrl)

        // Gateway time is 1761840000 + 15 seconds (skew = 15s > 10s tolerance)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"ok","environment":"test","version":"1.0.0","timestamp":1761840015}""")
        )

        val useCase = createUseCase()
        val report = useCase()

        val skewCheck = report.checks.first { it.id == PreflightCheckId.CLOCK_SKEW }
        assertEquals(CheckStatus.FAIL, skewCheck.status)
        assertTrue(skewCheck.summary.contains("exceeds 10 s gateway tolerance"))
    }

    @Test
    fun `clock drift between 5s and 10s produces WARN on CLOCK_SKEW`() = runTest {
        val serverUrl = server.url("/").toString().removeSuffix("/")
        baseUrlStore.save(serverUrl)

        // Gateway time is 1761840000 + 7 seconds (5s < skew <= 10s)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"ok","environment":"test","version":"1.0.0","timestamp":1761840007}""")
        )

        val useCase = createUseCase()
        val report = useCase()

        val skewCheck = report.checks.first { it.id == PreflightCheckId.CLOCK_SKEW }
        assertEquals(CheckStatus.WARN, skewCheck.status)
        assertTrue(skewCheck.summary.contains("Clock drift detected"))
    }

    @Test
    fun `environment mismatch fails CONTRACT_VERSION check`() = runTest {
        val serverUrl = server.url("/").toString().removeSuffix("/")
        baseUrlStore.save(serverUrl)
        baseUrlStore.saveExpectedEnvironment("test")

        // Gateway reports "production", expected is "test"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"ok","environment":"production","version":"1.0.0","timestamp":1761840000}""")
        )

        val useCase = createUseCase()
        val report = useCase()

        val contractCheck = report.checks.first { it.id == PreflightCheckId.CONTRACT_VERSION }
        assertEquals(CheckStatus.FAIL, contractCheck.status)
        assertTrue(contractCheck.summary.contains("Environment mismatch"))
    }

    @Test
    fun `missing operator credential warns in test environment but fails in staging`() = runTest {
        val serverUrl = server.url("/").toString().removeSuffix("/")
        baseUrlStore.save(serverUrl)
        baseUrlStore.saveExpectedEnvironment("test")

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"ok","environment":"test","version":"1.0.0","timestamp":1761840000}""")
        )

        val useCase = createUseCase()
        val reportTest = useCase()
        val authCheckTest = reportTest.checks.first { it.id == PreflightCheckId.AUTHENTICATION }
        assertEquals(CheckStatus.WARN, authCheckTest.status)

        // Switch to staging
        baseUrlStore.saveExpectedEnvironment("staging")
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"ok","environment":"staging","version":"1.0.0","timestamp":1761840000}""")
        )

        val reportStaging = useCase()
        val authCheckStaging = reportStaging.checks.first { it.id == PreflightCheckId.AUTHENTICATION }
        assertEquals(CheckStatus.FAIL, authCheckStaging.status)
    }

    @Test
    fun `missing enrolment produces WARN on ENROLMENT_STATE`() = runTest {
        val serverUrl = server.url("/").toString().removeSuffix("/")
        baseUrlStore.save(serverUrl)

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"ok","environment":"test","version":"1.0.0","timestamp":1761840000}""")
        )

        val useCase = createUseCase()
        val report = useCase()

        val enrolmentCheck = report.checks.first { it.id == PreflightCheckId.ENROLMENT_STATE }
        assertEquals(CheckStatus.WARN, enrolmentCheck.status)
        assertTrue(enrolmentCheck.summary.contains("No active device key enrolled"))
    }

    @Test
    fun `unreachable gateway fails reachability, skew, and contract`() = runTest {
        val serverUrl = server.url("/").toString().removeSuffix("/")
        baseUrlStore.save(serverUrl)

        // Enqueue 500 error or shutdown server
        server.enqueue(MockResponse().setResponseCode(503).setBody("Service Unavailable"))

        val useCase = createUseCase()
        val report = useCase()

        assertFalse(report.overallPass == true)

        val reachable = report.checks.first { it.id == PreflightCheckId.REACHABLE }
        assertEquals(CheckStatus.FAIL, reachable.status)

        val skew = report.checks.first { it.id == PreflightCheckId.CLOCK_SKEW }
        assertEquals(CheckStatus.FAIL, skew.status)

        val contract = report.checks.first { it.id == PreflightCheckId.CONTRACT_VERSION }
        assertEquals(CheckStatus.FAIL, contract.status)
    }
}
