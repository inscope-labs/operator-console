package com.inscopelabs.buildengine.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.inscopelabs.buildengine.data.local.PlanDraftStore
import com.inscopelabs.buildengine.data.repository.PlanDraftRepositoryImpl
import com.inscopelabs.buildengine.domain.repository.PlanDraftRepository
import com.inscopelabs.buildengine.domain.usecase.authoring.CreatePlanDraftUseCase
import com.inscopelabs.buildengine.domain.usecase.authoring.DeletePlanDraftUseCase
import com.inscopelabs.buildengine.domain.usecase.authoring.ListPlanDraftsUseCase
import com.inscopelabs.buildengine.domain.usecase.authoring.LoadPlanDraftUseCase
import com.inscopelabs.buildengine.domain.usecase.authoring.PreviewPlanDraftUseCase
import com.inscopelabs.buildengine.domain.usecase.authoring.SavePlanDraftUseCase
import com.inscopelabs.buildengine.domain.usecase.authoring.SubmitPlanDraftUseCase
import com.inscopelabs.buildengine.domain.usecase.authoring.ValidatePlanDraftUseCase
import com.inscopelabs.buildengine.domain.validation.PlanDraftValidator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * Plan Management Authoring — the bindings for the plan authoring interface.
 *
 * The authoring interface is bound here rather than folded into [UseCaseModule], because it is a
 * self-contained concern: it reads and writes **plan drafts on the device** and nothing else.
 * There is no gateway plan-authoring route yet, so this module binds no HTTP surface — the one
 * thing it deliberately does not provide is a way to write to a repository.
 */
@Module
@InstallIn(SingletonComponent::class)
object PlanAuthoringModule {

    /**
     * The plan draft store's DataStore.
     *
     * A DataStore may only be instantiated once per file, so it is provided here rather than
     * created inside [PlanDraftStore].
     */
    @Provides
    @Singleton
    @Named(PMA_DRAFT_STORE_FILE)
    fun providePlanDraftDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create {
        context.preferencesDataStoreFile(PMA_DRAFT_STORE_FILE)
    }

    @Provides
    @Singleton
    fun providePlanDraftStore(
        @Named(PMA_DRAFT_STORE_FILE) dataStore: DataStore<Preferences>,
    ): PlanDraftStore = PlanDraftStore(dataStore)

    @Provides
    @Singleton
    fun providePlanDraftRepository(
        impl: PlanDraftRepositoryImpl,
    ): PlanDraftRepository = impl

    @Provides
    @Singleton
    fun providePlanDraftValidator(): PlanDraftValidator = PlanDraftValidator()

    @Provides
    @Singleton
    fun provideCreatePlanDraftUseCase(
        repository: PlanDraftRepository,
    ): CreatePlanDraftUseCase = CreatePlanDraftUseCase(repository)

    @Provides
    @Singleton
    fun provideSavePlanDraftUseCase(
        repository: PlanDraftRepository,
    ): SavePlanDraftUseCase = SavePlanDraftUseCase(repository)

    @Provides
    @Singleton
    fun provideLoadPlanDraftUseCase(
        repository: PlanDraftRepository,
    ): LoadPlanDraftUseCase = LoadPlanDraftUseCase(repository)

    @Provides
    @Singleton
    fun provideListPlanDraftsUseCase(
        repository: PlanDraftRepository,
    ): ListPlanDraftsUseCase = ListPlanDraftsUseCase(repository)

    @Provides
    @Singleton
    fun provideDeletePlanDraftUseCase(
        repository: PlanDraftRepository,
    ): DeletePlanDraftUseCase = DeletePlanDraftUseCase(repository)

    @Provides
    @Singleton
    fun provideValidatePlanDraftUseCase(
        validator: PlanDraftValidator,
    ): ValidatePlanDraftUseCase = ValidatePlanDraftUseCase(validator)

    @Provides
    @Singleton
    fun providePreviewPlanDraftUseCase(): PreviewPlanDraftUseCase = PreviewPlanDraftUseCase()

    @Provides
    @Singleton
    fun provideSubmitPlanDraftUseCase(
        validator: PlanDraftValidator,
    ): SubmitPlanDraftUseCase = SubmitPlanDraftUseCase(validator)

    /** The file the plan drafts live in. Plain storage — a draft holds no secret. */
    private const val PMA_DRAFT_STORE_FILE: String = "operator_console_pma_drafts"
}
