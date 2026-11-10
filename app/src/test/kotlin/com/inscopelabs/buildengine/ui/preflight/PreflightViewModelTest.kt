package com.inscopelabs.buildengine.ui.preflight

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.inscopelabs.buildengine.core.crypto.StrongBoxSupport
import com.inscopelabs.buildengine.core.network.ErrorMapper
import com.inscopelabs.buildengine.core.network.GatewayHttpClient
import com.inscopelabs.buildengine.core.security.CertificatePinning
import com.inscopelabs.buildengine.core.time.FixedClock
import com.inscopelabs.buildengine.data.enrollment.DeviceEnrollmentStore
import com.inscopelabs.buildengine.data.local.GatewayBaseUrlStore
import com.inscopelabs.buildengine.data.local.OperatorCredentialStore
import com.inscopelabs.buildengine.data.remote.GatewayRepository
import com.inscopelabs.buildengine.domain.model.preflight.CheckStatus
import com.inscopelabs.buildengine.domain.model.preflight.PreflightCheckId
import com.inscopelabs.buildengine.domain.usecase.enrollment.ReportDevicePostureUseCase
import com.inscopelabs.buildengine.domain.usecase.preflight.RunPreflightUseCase
import com.inscopelabs.buildengine.testutil.FakeDeviceAttestation
import com.inscopelabs.buildengine.testutil.FakeDeviceKeyManager
import com.inscopelabs.buildengine.testutil.FakeSharedPreferences
import com.inscopelabs.buildengine.testutil.MainDispatcherRule
import app.cash.turbine.test
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PreflightViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

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
    private lateinit var runPreflightUseCase: RunPreflightUseCase

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
        baseUrlStore = GatewayBaseUrlStore(
            dataStore = dataStore,
            debugDefault = server.url("/").toString().removeSuffix("/"),
        )

        credentialStore = OperatorCredentialStore(context = null, preferences = FakeSharedPreferences())
        enrollmentStore = DeviceEnrollmentStore(preferences = FakeSharedPreferences())
        clock = FixedClock(1761840000L)
        certificatePinning = CertificatePinning(clock = clock)

        val deviceKeyManager = FakeDeviceKeyManager()
        val deviceAttestation = FakeDeviceAttestation(deviceKeyManager)
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

        runPreflightUseCase = RunPreflightUseCase(
            baseUrlStore = baseUrlStore,
            gatewayRepository = gatewayRepository,
            certificatePinning = certificatePinning,
            credentialStore = credentialStore,
            clock = clock,
            enrollmentStore = enrollmentStore,
            reportDevicePostureUseCase = reportDevicePostureUseCase,
        )
    }

    @After
    fun tearDown() {
        httpClient.close()
        server.shutdown()
    }

    @Test
    fun `init triggers runChecks and updates report state`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"ok","environment":"test","version":"1.0.0","timestamp":1761840000}""")
        )

        val viewModel = PreflightViewModel(runPreflightUseCase)
        viewModel.report.test {
            var latest = awaitItem()
            while (latest.checks.isEmpty() || latest.isRunning) {
                latest = awaitItem()
            }
            assertFalse(latest.isRunning)
            assertEquals(8, latest.checks.size)
            assertNotNull(latest.overallPass)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `runChecks handles failing checks and updates state`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setBody("Service Unavailable")
        )

        val viewModel = PreflightViewModel(runPreflightUseCase)
        viewModel.report.test {
            var latest = awaitItem()
            while (latest.checks.isEmpty() || latest.isRunning) {
                latest = awaitItem()
            }
            assertFalse(latest.isRunning)
            assertEquals(false, latest.overallPass)
            val reachable = latest.checks.first { it.id == PreflightCheckId.REACHABLE }
            assertEquals(CheckStatus.FAIL, reachable.status)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
