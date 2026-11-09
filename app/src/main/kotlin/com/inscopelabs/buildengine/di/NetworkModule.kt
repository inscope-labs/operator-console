package com.inscopelabs.buildengine.di

import com.inscopelabs.buildengine.core.network.ErrorMapper
import com.inscopelabs.buildengine.core.network.GatewayBaseUrlProvider
import com.inscopelabs.buildengine.core.network.GatewayEndpoints
import com.inscopelabs.buildengine.core.network.GatewayActivityLog
import com.inscopelabs.buildengine.core.network.GatewayHttpClient
import com.inscopelabs.buildengine.core.network.MtlsConfig
import com.inscopelabs.buildengine.core.network.OperatorCredentialProvider
import com.inscopelabs.buildengine.core.security.CertificatePinning
import com.inscopelabs.buildengine.core.time.Clock
import com.inscopelabs.buildengine.data.local.GatewayBaseUrlStore
import com.inscopelabs.buildengine.data.local.OperatorCredentialStore
import com.inscopelabs.buildengine.data.remote.GatewayApi
import com.inscopelabs.buildengine.data.remote.GatewayRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Network bindings.
 *
 * Design review §13 / ADR 0003: the transport to the gateway must be mutually
 * authenticated, not merely TLS. The client certificate and the pin set belong here.
 * mTLS remains deferred (ADR 0006); the bearer credential is the console's identity.
 *
 * Stage 2.3: Certificate pinning (primary + backup pins, expiry check, release empty
 * refusal) enforced on the OkHttp engine via CertificatePinner and Interceptor.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** TCP connect budget (ms). */
    const val CONNECT_TIMEOUT_MS: Long = 15_000

    /** Socket read/write budget (ms). */
    const val SOCKET_TIMEOUT_MS: Long = 30_000

    /** Whole-call budget including connect + body (ms). */
    const val CALL_TIMEOUT_MS: Long = 45_000

    @Provides
    @Singleton
    fun provideCertificatePinning(clock: Clock): CertificatePinning = CertificatePinning(clock = clock)

    @Provides
    @Singleton
    fun provideMtlsConfig(
        baseUrlStore: GatewayBaseUrlStore,
        certificatePinning: CertificatePinning,
    ): MtlsConfig {
        val host = baseUrlStore.current().ifEmpty { GatewayEndpoints.BASE_URL }
        return MtlsConfig(
            host = host,
            pins = certificatePinning.pinsFor(host),
        )
    }

    @Provides
    @Singleton
    fun provideHttpClient(
        config: MtlsConfig,
        certificatePinning: CertificatePinning,
    ): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        followRedirects = false
        engine {
            config {
                followRedirects(false)
                followSslRedirects(false)
                connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                readTimeout(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                writeTimeout(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                callTimeout(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                certificatePinner(certificatePinning.buildCertificatePinner())
                addInterceptor(certificatePinning.asInterceptor())
            }
        }
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            socketTimeoutMillis = SOCKET_TIMEOUT_MS
            requestTimeoutMillis = CALL_TIMEOUT_MS
        }
    }

    /**
     * The transport, with base URL and operator credential resolved per request.
     *
     * The base URL is read from [GatewayBaseUrlStore] so Settings changes take effect
     * without process restart. The credential is gateway-scoped and holds no
     * repository authority (ADR 0002). With mTLS deferred (ADR 0006), the bearer
     * header is the console's only identity on the gateway.
     */
    @Provides
    @Singleton
    fun provideGatewayHttpClient(
        client: HttpClient,
        baseUrlStore: GatewayBaseUrlStore,
        credentialStore: OperatorCredentialStore,
        activityLog: GatewayActivityLog,
    ): GatewayHttpClient = GatewayHttpClient(
        client = client,
        baseUrlProvider = GatewayBaseUrlProvider { baseUrlStore.current() },
        credentials = OperatorCredentialProvider { credentialStore.read(baseUrlStore.expectedEnvironment()) },
        activityLog = activityLog,
    )

    @Provides
    @Singleton
    fun provideGatewayRepository(
        client: GatewayHttpClient,
        errorMapper: ErrorMapper,
    ): GatewayRepository = GatewayRepository(client, errorMapper)

    @Provides
    @Singleton
    fun provideGatewayApi(
        repository: GatewayRepository,
    ): GatewayApi = repository

    @Provides
    @Singleton
    fun provideErrorMapper(): ErrorMapper = ErrorMapper()
}
