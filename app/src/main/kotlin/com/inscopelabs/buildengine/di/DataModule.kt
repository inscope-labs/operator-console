package com.inscopelabs.buildengine.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.inscopelabs.buildengine.core.crypto.SecureRandomSource
import com.inscopelabs.buildengine.core.network.ErrorMapper
import com.inscopelabs.buildengine.core.time.Clock
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
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The data layer's bindings.
 *
 * Design review §4: OperatorCredentialStore holds a gateway-scoped credential only.
 * It must never hold a GitHub token of any kind.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    /**
     * The selection store's DataStore.
     *
     * Provided here rather than created inside [SelectionStore], because a DataStore
     * may only be instantiated once per file: two instances of one file is not a
     * style question, it is an error DataStore raises.
     */
    @Provides
    @Singleton
    fun provideSelectionDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create {
        context.preferencesDataStoreFile(SELECTION_STORE_FILE)
    }

    @Provides
    @Singleton
    fun provideSelectionStore(dataStore: DataStore<Preferences>): SelectionStore =
        SelectionStore(dataStore)

    @Provides
    @Singleton
    fun provideOperatorCredentialStore(
        @ApplicationContext context: Context,
    ): OperatorCredentialStore = OperatorCredentialStore(context)

    @Provides
    @Singleton
    fun provideDeviceEnrollmentStore(): DeviceEnrollmentStore = DeviceEnrollmentStore()

    @Provides
    @Singleton
    fun providePlanRepository(
        gateway: GatewayRepository,
        errorMapper: ErrorMapper,
    ): PlanRepository = PlanRepositoryImpl(gateway, errorMapper)

    @Provides
    @Singleton
    fun provideTaskRepository(
        gateway: GatewayRepository,
        errorMapper: ErrorMapper,
    ): TaskRepository = TaskRepositoryImpl(gateway, errorMapper)

    @Provides
    @Singleton
    fun provideOperationRepository(gateway: GatewayRepository): OperationRepository =
        OperationRepositoryImpl(gateway)

    /**
     * The session repository.
     *
     * The session is in-memory only (design review §9), so this binding holds no
     * state that outlives the process and writes nothing to disk.
     */
    @Provides
    @Singleton
    fun provideSessionRepository(
        clock: Clock,
        secureRandomSource: SecureRandomSource,
    ): SessionRepository = SessionRepositoryImpl(clock, secureRandomSource)

    /** The file the selection state lives in. Plain storage — it is not secret. */
    private const val SELECTION_STORE_FILE: String = "operator_console_selection"
}
