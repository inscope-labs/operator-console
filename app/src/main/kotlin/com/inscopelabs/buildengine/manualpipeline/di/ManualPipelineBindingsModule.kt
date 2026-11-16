package com.inscopelabs.buildengine.manualpipeline.di

import com.inscopelabs.buildengine.manualpipeline.data.integration.ExistingMintingAdapter
import com.inscopelabs.buildengine.manualpipeline.data.integration.OperatorCredentialAdapter
import com.inscopelabs.buildengine.manualpipeline.data.integration.RedactorAdapter
import com.inscopelabs.buildengine.manualpipeline.data.integration.SystemClock
import com.inscopelabs.buildengine.manualpipeline.data.repository.HandoffRepositoryImpl
import com.inscopelabs.buildengine.manualpipeline.data.repository.OperatorActionRepositoryImpl
import com.inscopelabs.buildengine.manualpipeline.data.repository.WorkflowRecordRepositoryImpl
import com.inscopelabs.buildengine.manualpipeline.domain.port.AuthorityPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.Clock
import com.inscopelabs.buildengine.manualpipeline.domain.port.HandoffEvidenceStore
import com.inscopelabs.buildengine.manualpipeline.domain.port.MintingPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.OperatorActionStore
import com.inscopelabs.buildengine.manualpipeline.domain.port.RedactorPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.WorkflowRecordStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ManualPipelineBindingsModule {

    @Binds
    @Singleton
    abstract fun bindAuthorityPort(impl: OperatorCredentialAdapter): AuthorityPort

    @Binds
    @Singleton
    abstract fun bindMintingPort(impl: ExistingMintingAdapter): MintingPort

    @Binds
    @Singleton
    abstract fun bindClock(impl: SystemClock): Clock

    @Binds
    @Singleton
    abstract fun bindRedactorPort(impl: RedactorAdapter): RedactorPort

    @Binds
    @Singleton
    abstract fun bindWorkflowRecordStore(impl: WorkflowRecordRepositoryImpl): WorkflowRecordStore

    @Binds
    @Singleton
    abstract fun bindOperatorActionStore(impl: OperatorActionRepositoryImpl): OperatorActionStore

    @Binds
    @Singleton
    abstract fun bindHandoffEvidenceStore(impl: HandoffRepositoryImpl): HandoffEvidenceStore
}
