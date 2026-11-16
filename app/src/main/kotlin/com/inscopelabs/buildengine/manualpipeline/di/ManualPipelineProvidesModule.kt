package com.inscopelabs.buildengine.manualpipeline.di

import android.content.Context
import androidx.room.Room
import com.inscopelabs.buildengine.manualpipeline.data.local.ManualPipelineDatabase
import com.inscopelabs.buildengine.manualpipeline.data.local.dao.HandoffEvidenceDao
import com.inscopelabs.buildengine.manualpipeline.data.local.dao.OperatorActionDao
import com.inscopelabs.buildengine.manualpipeline.data.local.dao.WorkflowRecordDao
import com.inscopelabs.buildengine.manualpipeline.util.ZipBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ManualPipelineProvidesModule {

    @Provides
    @Singleton
    fun provideManualPipelineDatabase(
        @ApplicationContext context: Context,
    ): ManualPipelineDatabase = Room.databaseBuilder(
        context,
        ManualPipelineDatabase::class.java,
        ManualPipelineDatabase.DATABASE_NAME,
    ).build()

    @Provides
    @Singleton
    fun provideWorkflowRecordDao(
        database: ManualPipelineDatabase,
    ): WorkflowRecordDao = database.workflowRecordDao()

    @Provides
    @Singleton
    fun provideOperatorActionDao(
        database: ManualPipelineDatabase,
    ): OperatorActionDao = database.operatorActionDao()

    @Provides
    @Singleton
    fun provideHandoffEvidenceDao(
        database: ManualPipelineDatabase,
    ): HandoffEvidenceDao = database.handoffEvidenceDao()

    @Provides
    @Singleton
    fun provideZipBuilder(): ZipBuilder = ZipBuilder
}
