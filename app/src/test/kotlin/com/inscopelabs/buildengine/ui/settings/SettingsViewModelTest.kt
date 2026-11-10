package com.inscopelabs.buildengine.ui.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.inscopelabs.buildengine.core.crypto.SecureRandomSource
import com.inscopelabs.buildengine.core.network.ErrorMapper
import com.inscopelabs.buildengine.core.network.GatewayActivityLog
import com.inscopelabs.buildengine.core.network.GatewayHttpClient
import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.core.time.Clock
import com.inscopelabs.buildengine.data.local.GatewayBaseUrlStore
import com.inscopelabs.buildengine.data.local.HealthSnapshotStore
import com.inscopelabs.buildengine.data.local.OperatorCredentialStore
import com.inscopelabs.buildengine.data.remote.GatewayRepository
import com.inscopelabs.buildengine.data.repository.SessionRepositoryImpl
import com.inscopelabs.buildengine.domain.repository.SessionRepository
import com.inscopelabs.buildengine.testutil.FakeContext
import com.inscopelabs.buildengine.testutil.FakeGatewayApi
import com.inscopelabs.buildengine.testutil.FakeSharedPreferences
import com.inscopelabs.buildengine.testutil.MainDispatcherRule
import com.inscopelabs.buildengine.ui.common.UiState
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var httpClient: HttpClient
    private lateinit var context: FakeContext
    private lateinit var credentialPrefs: FakeSharedPreferences
    private lateinit var credentialStore: OperatorCredentialStore
    private lateinit var baseUrlStore: GatewayBaseUrlStore
    private lateinit var healthSnapshotStore: HealthSnapshotStore
    private lateinit var gatewayRepository: GatewayRepository
    private lateinit var sessionRepository: SessionRepository
    private lateinit var activityLog: GatewayActivityLog
    private lateinit var clock: Clock

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        httpClient = HttpClient(OkHttp) { expectSuccess = false }
        context = FakeContext()
        credentialPrefs = FakeSharedPreferences()
        credentialStore = OperatorCredentialStore(context, preferences = credentialPrefs)

        val urlFile = File(temporaryFolder.root, "gateway_url.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { urlFile })
        baseUrlStore = GatewayBaseUrlStore(dataStore)
        healthSnapshotStore = HealthSnapshotStore(dataStore)

        activityLog = GatewayActivityLog()
        val gatewayClient = GatewayHttpClient(
            client = httpClient,
            baseUrlProvider = { baseUrlStore.current().ifEmpty { server.url("/").toString() } },
            activityLog = activityLog,
        )
        gatewayRepository = GatewayRepository(gatewayClient, ErrorMapper())

        clock = com.inscopelabs.buildengine.core.time.FixedClock(1761840000L)
        val randomSource = SecureRandomSource()
        sessionRepository = SessionRepositoryImpl(clock, randomSource)
    }

    @After
    fun tearDown() {
        httpClient.close()
        server.shutdown()
    }

    private fun createViewModel(): SettingsViewModel = SettingsViewModel(
        appContext = context,
        credentialStore = credentialStore,
        gatewayBaseUrlStore = baseUrlStore,
        healthSnapshotStore = healthSnapshotStore,
        gatewayRepository = gatewayRepository,
        sessionRepository = sessionRepository,
        clock = clock,
        activityLog = activityLog,
    )

    @Test
    fun `initial state starts with unconfigured gateway and absent credential`() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            // First item may be Idle or Success from refresh
            var item = awaitItem()
            if (item is UiState.Idle) item = awaitItem()
            assertTrue(item is UiState.Success)
            val state = (item as UiState.Success).data
            assertFalse(state.gatewayConfigured)
            assertFalse(state.credentialConfigured)
            assertNull(state.healthSnapshot)
            assertNull(state.session)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveGatewayBaseUrl saves valid URL and sets configured`() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            skipItems(1) // initial
            viewModel.saveGatewayBaseUrl("https://gateway.example.com")

            val updated = awaitItem()
            assertTrue(updated is UiState.Success)
            val state = (updated as UiState.Success).data
            assertTrue(state.gatewayConfigured)
            assertEquals("https://gateway.example.com", state.gatewayBaseUrl)
            assertEquals(SettingsNotice.GATEWAY_URL_SAVED, state.notice)
            assertNull(state.error)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("https://gateway.example.com", baseUrlStore.read())
    }

    @Test
    fun `saveGatewayBaseUrl rejects credentials in URL`() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            skipItems(1) // initial
            viewModel.saveGatewayBaseUrl("https://user:pass@gateway.example.com")

            val updated = awaitItem()
            assertTrue(updated is UiState.Success)
            val state = (updated as UiState.Success).data
            assertEquals(SettingsError.GATEWAY_URL_INVALID, state.error)
            assertNotNull(state.customError)
            assertTrue(state.customError?.contains("credentials") == true)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("", baseUrlStore.read())
    }

    @Test
    fun `saveGatewayBaseUrl rejects cleartext HTTP in release mode`() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            skipItems(1) // initial
            viewModel.saveGatewayBaseUrl("http://10.0.2.2:8787", isDebug = false)

            val updated = awaitItem()
            assertTrue(updated is UiState.Success)
            val state = (updated as UiState.Success).data
            assertEquals(SettingsError.GATEWAY_URL_INVALID, state.error)
            assertNotNull(state.customError)
            assertTrue(state.customError?.contains("release", ignoreCase = true) == true)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("", baseUrlStore.read())
    }

    @Test
    fun `saveGatewayBaseUrl allows local cleartext HTTP in debug mode`() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            skipItems(1) // initial
            viewModel.saveGatewayBaseUrl("http://10.0.2.2:8787", isDebug = true)

            val updated = awaitItem()
            assertTrue(updated is UiState.Success)
            val state = (updated as UiState.Success).data
            assertTrue(state.gatewayConfigured)
            assertEquals(SettingsNotice.GATEWAY_URL_SAVED, state.notice)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("http://10.0.2.2:8787", baseUrlStore.read())
    }

    @Test
    fun `clearGatewayBaseUrl clears store and health snapshot`() = runTest {
        baseUrlStore.save("https://gateway.example.com")
        val viewModel = createViewModel()
        viewModel.uiState.test {
            viewModel.clearGatewayBaseUrl()

            var state: SettingsState? = null
            while (true) {
                val item = awaitItem()
                if (item is UiState.Success && item.data.notice == SettingsNotice.GATEWAY_URL_CLEARED) {
                    state = item.data
                    break
                }
            }
            assertNotNull(state)
            val nonNullState = requireNotNull(state)
            assertFalse(nonNullState.gatewayConfigured)
            assertEquals("", nonNullState.gatewayBaseUrl)
            assertNull(nonNullState.healthSnapshot)
            assertEquals(SettingsNotice.GATEWAY_URL_CLEARED, nonNullState.notice)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("", baseUrlStore.read())
    }

    @Test
    fun `testConnection success persists health snapshot`() = runTest {
        val serverUrl = server.url("/").toString().trimEnd('/')
        baseUrlStore.save(serverUrl)
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"status":"ok","environment":"staging","version":"1.5.0","timestamp":1761840000}"""
            )
        )

        val viewModel = createViewModel()
        viewModel.uiState.test {
            skipItems(1)
            viewModel.testConnection()

            // In-flight testing
            val testing = awaitItem()
            assertTrue(testing is UiState.Success)

            // Result
            val success = awaitItem()
            assertTrue(success is UiState.Success)
            val state = (success as UiState.Success).data
            assertFalse(state.healthTesting)
            assertEquals(SettingsNotice.HEALTH_OK, state.notice)
            assertNotNull(state.healthSnapshot)
            assertEquals("staging", state.healthSnapshot?.environment)
            assertEquals("1.5.0", state.healthSnapshot?.version)
            cancelAndIgnoreRemainingEvents()
        }

        val persisted = healthSnapshotStore.read()
        assertNotNull(persisted)
        assertEquals("staging", persisted?.environment)
        assertEquals("1.5.0", persisted?.version)
    }

    @Test
    fun `testConnection failure clears health snapshot and sets healthError`() = runTest {
        val serverUrl = server.url("/").toString().trimEnd('/')
        baseUrlStore.save(serverUrl)
        server.enqueue(MockResponse().setResponseCode(503).setBody("Service unavailable"))

        val viewModel = createViewModel()
        viewModel.uiState.test {
            skipItems(1)
            viewModel.testConnection()

            var state: SettingsState
            do {
                val item = awaitItem()
                assertTrue(item is UiState.Success)
                state = (item as UiState.Success).data
            } while (state.healthTesting)
            assertFalse(state.healthTesting)
            assertNull(state.healthSnapshot)
            assertNotNull(state.healthError)
            cancelAndIgnoreRemainingEvents()
        }

        val persisted = healthSnapshotStore.read()
        assertNull(persisted)
    }

    @Test
    fun `saveCredential and clearCredential update state`() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            skipItems(1)
            viewModel.saveCredential("gw_op_valid_secret_key")

            val saved = awaitItem()
            assertTrue(saved is UiState.Success)
            val state = (saved as UiState.Success).data
            assertTrue(state.credentialConfigured)
            assertEquals(SettingsNotice.CREDENTIAL_SAVED, state.notice)

            viewModel.clearCredential()
            val cleared = awaitItem()
            assertTrue(cleared is UiState.Success)
            val clearedState = (cleared as UiState.Success).data
            assertFalse(clearedState.credentialConfigured)
            assertEquals(SettingsNotice.CREDENTIAL_CLEARED, clearedState.notice)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveCredential rejects repository token`() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            skipItems(1)
            viewModel.saveCredential("ghp_" + "a".repeat(36))

            val rejected = awaitItem()
            assertTrue(rejected is UiState.Success)
            val state = (rejected as UiState.Success).data
            assertFalse(state.credentialConfigured)
            assertEquals(SettingsError.CREDENTIAL_REJECTED, state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `startSession and endSession manage operator session`() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            skipItems(1)
            viewModel.startSession()

            val started = awaitItem()
            assertTrue(started is UiState.Success)
            val state = (started as UiState.Success).data
            assertNotNull(state.session)
            assertEquals(SettingsNotice.SESSION_STARTED, state.notice)

            viewModel.endSession()
            val ended = awaitItem()
            assertTrue(ended is UiState.Success)
            val endedState = (ended as UiState.Success).data
            assertNull(endedState.session)
            assertEquals(SettingsNotice.SESSION_ENDED, endedState.notice)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Environment safety & credential isolation (T2-201, T2-202, T2-203) -----

    @Test
    fun `switching expected environment switches credential slot and never reuses credentials`() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            skipItems(1) // Initial state

            // 1. Switch to staging
            viewModel.saveExpectedEnvironment("staging")
            val stgState = awaitItem()
            assertTrue(stgState is UiState.Success)
            assertEquals("staging", (stgState as UiState.Success).data.expectedEnvironment)
            assertFalse(stgState.data.credentialConfigured)

            // 2. Save credential for staging
            viewModel.saveCredential("gw_op_staging_cred_123")
            val savedStg = awaitItem()
            assertTrue((savedStg as UiState.Success).data.credentialConfigured)

            // 3. Switch to production
            viewModel.saveExpectedEnvironment("production")
            val prodState = awaitItem()
            assertTrue(prodState is UiState.Success)
            assertEquals("production", (prodState as UiState.Success).data.expectedEnvironment)
            // MUST NOT reuse staging's credential!
            assertFalse((prodState as UiState.Success).data.credentialConfigured)

            // 4. Switch back to staging -> staging credential is still configured
            viewModel.saveExpectedEnvironment("staging")
            val backToStg = awaitItem()
            assertTrue((backToStg as UiState.Success).data.credentialConfigured)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `testConnection detects environment mismatch between expected and reported environment`() = runTest {
        val serverUrl = server.url("/").toString().trimEnd('/')
        baseUrlStore.save(serverUrl)
        baseUrlStore.saveExpectedEnvironment("production")

        // Gateway returns "test" instead of expected "production"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"ok","environment":"test","version":"2.0.0","timestamp":1700000000}""")
        )

        val viewModel = createViewModel()
        viewModel.uiState.test {
            viewModel.testConnection()

            var matchedState: SettingsState? = null
            while (true) {
                val item = awaitItem()
                if (item is UiState.Success && item.data.healthSnapshot?.environment == "test") {
                    matchedState = item.data
                    break
                }
            }

            assertNotNull(matchedState)
            val state = checkNotNull(matchedState)
            assertFalse(state.healthTesting)
            assertEquals("test", state.healthSnapshot?.environment)
            assertEquals("production", state.expectedEnvironment)

            // Environment mismatch must be flagged!
            assertTrue(state.environmentMismatch)
            assertNotNull(state.environmentMismatchMessage)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
