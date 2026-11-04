package com.inscopelabs.buildengine.di

import com.inscopelabs.buildengine.core.network.ErrorMapper
import com.inscopelabs.buildengine.core.network.GatewayBaseUrlProvider
import com.inscopelabs.buildengine.core.network.GatewayEndpoints
import com.inscopelabs.buildengine.core.network.GatewayActivityLog
import com.inscopelabs.buildengine.core.network.GatewayHttpClient
import com.inscopelabs.buildengine.core.network.MtlsConfig
import com.inscopelabs.buildengine.core.network.OperatorCredentialProvider
import com.inscopelabs.buildengine.core.security.CertificatePinning
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
import javax.inject.Singleton

/**
 * Network bindings.
 *
 * Design review §13 / ADR 0003: the transport to the gateway must be mutually
 * authenticated, not merely TLS. The client certificate and the pin set belong here.
 * mTLS remains deferred (ADR 0006); the bearer credential is the console's identity.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideCertificatePinning(): CertificatePinning = CertificatePinning()

    @Provides
    @Singleton
    fun provideMtlsConfig(baseUrlStore: GatewayBaseUrlStore): MtlsConfig = MtlsConfig(
        host = baseUrlStore.current().ifEmpty { GatewayEndpoints.BASE_URL },
        pins = emptyList(),
    )

    @Provides
    @Singleton
    fun provideHttpClient(config: MtlsConfig): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        // SCAFFOLD: mTLS socket factory, certificate pinner and attestation header
        // go here. See docs/adr/0003-mtls-to-gateway.md.
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
        credentials = OperatorCredentialProvider { credentialStore.read() },
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
