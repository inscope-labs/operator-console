package com.inscopelabs.buildengine.di

import com.inscopelabs.buildengine.data.local.DeviceEnrollmentStore
import com.inscopelabs.buildengine.data.local.OperatorCredentialStore
import com.inscopelabs.buildengine.data.local.SelectionStore
import com.inscopelabs.buildengine.data.remote.GatewayRepository
import com.inscopelabs.buildengine.data.repository.OperationRepositoryImpl
import com.inscopelabs.buildengine.data.repository.PlanRepositoryImpl
import com.inscopelabs.buildengine.data.repository.SessionRepositoryImpl
import com.inscopelabs.buildengine.data.repository.TaskRepositoryImpl
import com.inscopelabs.buildengine.domain.repository.OperationRepository
import com.inscopelabs.buildengine.domain.repository.PlanRepository
import com.inscopelabs.buildengine.domain.repository.SessionRepository
import com.inscopelabs.buildengine.domain.repository.TaskRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §4: OperatorCredentialStore holds a gateway-scoped credential only.
 * It must never hold a GitHub token of any kind.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideSelectionStore(): SelectionStore = SelectionStore()

    @Provides
    @Singleton
    fun provideOperatorCredentialStore(): OperatorCredentialStore = OperatorCredentialStore()

    @Provides
    @Singleton
    fun provideDeviceEnrollmentStore(): DeviceEnrollmentStore = DeviceEnrollmentStore()

    @Provides
    @Singleton
    fun providePlanRepository(gateway: GatewayRepository): PlanRepository =
        PlanRepositoryImpl(gateway)

    @Provides
    @Singleton
    fun provideTaskRepository(gateway: GatewayRepository): TaskRepository =
        TaskRepositoryImpl(gateway)

    @Provides
    @Singleton
    fun provideOperationRepository(gateway: GatewayRepository): OperationRepository =
        OperationRepositoryImpl(gateway)

    @Provides
    @Singleton
    fun provideSessionRepository(): SessionRepository = SessionRepositoryImpl()
}
