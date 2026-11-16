package com.inscopelabs.buildengine.manualpipeline.di

import com.inscopelabs.buildengine.manualpipeline.data.integration.SharedExecutorAdapter
import com.inscopelabs.buildengine.manualpipeline.domain.port.ExecutorPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ManualPipelineExecutorModule {

    @Binds
    @Singleton
    abstract fun bindExecutorPort(impl: SharedExecutorAdapter): ExecutorPort
}
