package com.inscopelabs.buildengine.manualpipeline.di

import com.inscopelabs.buildengine.manualpipeline.data.journal.ExistingJournalAdapter
import com.inscopelabs.buildengine.manualpipeline.domain.port.JournalPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ManualPipelineJournalModule {

    @Binds
    @Singleton
    abstract fun bindJournalPort(impl: ExistingJournalAdapter): JournalPort
}
