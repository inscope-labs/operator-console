package com.inscopelabs.buildengine.di

import android.content.Context
import com.inscopelabs.buildengine.capability.NonceSource
import com.inscopelabs.buildengine.capability.DeviceNonceSource
import com.inscopelabs.buildengine.capability.signing.CapabilitySigner
import com.inscopelabs.buildengine.capability.signing.DeviceKeyCapabilitySigner
import com.inscopelabs.buildengine.core.crypto.DeviceKeyManager
import com.inscopelabs.buildengine.core.crypto.KeystoreSigner
import com.inscopelabs.buildengine.core.crypto.SecureRandomSource
import com.inscopelabs.buildengine.core.crypto.StrongBoxSupport
import com.inscopelabs.buildengine.core.security.DeviceAttestation
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Design review §5: Model B is the default — the console signs capabilities with its
 * device key and the gateway verifies. HmacCapabilitySigner (Model A) stays behind the
 * CapabilitySigner interface until decision D-a is closed.
 */
@Module
@InstallIn(SingletonComponent::class)
object CryptoModule {

    @Provides
    @Singleton
    fun provideCapabilitySigner(): CapabilitySigner = DeviceKeyCapabilitySigner()

    @Provides
    @Singleton
    fun provideNonceSource(
        secureRandomSource: SecureRandomSource,
    ): NonceSource = DeviceNonceSource(secureRandomSource)

    @Provides
    @Singleton
    fun provideDeviceKeyManager(
        @ApplicationContext context: Context,
    ): DeviceKeyManager = DeviceKeyManager(context)

    @Provides
    @Singleton
    fun provideStrongBoxSupport(
        @ApplicationContext context: Context,
    ): StrongBoxSupport = StrongBoxSupport(context)

    @Provides
    @Singleton
    fun provideKeystoreSigner(
        deviceKeyManager: DeviceKeyManager,
    ): KeystoreSigner = KeystoreSigner(deviceKeyManager)

    @Provides
    @Singleton
    fun provideDeviceAttestation(
        deviceKeyManager: DeviceKeyManager,
    ): DeviceAttestation = DeviceAttestation(deviceKeyManager)
}
