package com.inscopelabs.buildengine.di

import com.inscopelabs.buildengine.core.network.ErrorMapper
import com.inscopelabs.buildengine.core.network.GatewayEndpoints
import com.inscopelabs.buildengine.core.network.GatewayHttpClient
import com.inscopelabs.buildengine.core.network.MtlsConfig
import com.inscopelabs.buildengine.core.security.CertificatePinning
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

    @Provides
    @Singleton
    fun provideGatewayHttpClient(client: HttpClient): GatewayHttpClient =
        GatewayHttpClient(client)

    @Provides
    @Singleton
    fun provideGatewayRepository(client: GatewayHttpClient): GatewayRepository =
        GatewayRepository(client)

    @Provides
    @Singleton
    fun provideErrorMapper(): ErrorMapper = ErrorMapper()
}
