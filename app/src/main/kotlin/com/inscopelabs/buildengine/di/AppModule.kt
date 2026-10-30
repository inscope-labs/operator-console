package com.inscopelabs.buildengine.di

import com.inscopelabs.buildengine.core.crypto.SecureRandomSource
import com.inscopelabs.buildengine.core.time.Clock
import com.inscopelabs.buildengine.core.time.SystemClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = SystemClock()

    @Provides
    @Singleton
    fun provideSecureRandomSource(): SecureRandomSource = SecureRandomSource()
}
