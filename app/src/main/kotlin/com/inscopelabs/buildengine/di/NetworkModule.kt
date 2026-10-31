package com.inscopelabs.buildengine.di

import com.inscopelabs.buildengine.core.network.ErrorMapper
import com.inscopelabs.buildengine.core.network.GatewayEndpoints
import com.inscopelabs.buildengine.core.network.GatewayHttpClient
import com.inscopelabs.buildengine.core.network.MtlsConfig
import com.inscopelabs.buildengine.core.network.OperatorCredentialProvider
import com.inscopelabs.buildengine.core.security.CertificatePinning
import com.inscopelabs.buildengine.data.local.OperatorCredentialStore
import com.inscopelabs.buildengine.data.remote.GatewayRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import javax.inject.Singleton

/**
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §13 / ADR 0003: the transport to the gateway must be mutually
 * authenticated, not merely TLS. The client certificate and the pin set belong here.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideCertificatePinning(): CertificatePinning = CertificatePinning()

    @Provides
    @Singleton
    fun provideMtlsConfig(): MtlsConfig = MtlsConfig(
        host = GatewayEndpoints.BASE_URL,
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
     * The transport, with the operator credential attached.
     *
     * The credential is gateway-scoped and holds no repository authority (ADR 0002).
     * It is read from the encrypted store per request rather than held in a field,
     * so that clearing it in settings takes effect on the next call and not at the
     * next launch — and so that the credential's lifetime is the store's, not the
     * client's.
     *
     * With mTLS deferred (ADR 0006), this bearer header is the console's only
     * identity on the gateway.
     */
    @Provides
    @Singleton
    fun provideGatewayHttpClient(
        client: HttpClient,
        credentialStore: OperatorCredentialStore,
    ): GatewayHttpClient = GatewayHttpClient(
        client = client,
        credentials = OperatorCredentialProvider { credentialStore.read() },
    )

    @Provides
    @Singleton
    fun provideGatewayRepository(
        client: GatewayHttpClient,
        errorMapper: ErrorMapper,
    ): GatewayRepository = GatewayRepository(client, errorMapper)

    @Provides
    @Singleton
    fun provideErrorMapper(): ErrorMapper = ErrorMapper()
}
