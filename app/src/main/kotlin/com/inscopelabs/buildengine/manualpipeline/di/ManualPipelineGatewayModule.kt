package com.inscopelabs.buildengine.manualpipeline.di

import com.inscopelabs.buildengine.manualpipeline.data.gateway.GatewayAAuditReadAdapter
import com.inscopelabs.buildengine.manualpipeline.data.gateway.GatewayABranchHeadAdapter
import com.inscopelabs.buildengine.manualpipeline.data.gateway.GatewayAOperationLookupAdapter
import com.inscopelabs.buildengine.manualpipeline.data.gateway.GatewayAStatusTransitionAdapter
import com.inscopelabs.buildengine.manualpipeline.data.gateway.GatewayATaskReadAdapter
import com.inscopelabs.buildengine.manualpipeline.data.gateway.GatewayAUploadAdapter
import com.inscopelabs.buildengine.manualpipeline.domain.port.AuditReadPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.BranchHeadPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.OperationLookupPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.StatusTransitionPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.TaskGatewayPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.UploadPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Exposes Gateway A adapters as the sole gateway surface for `manualpipeline` (I-10).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ManualPipelineGatewayModule {

    @Binds
    @Singleton
    abstract fun bindTaskGatewayPort(impl: GatewayATaskReadAdapter): TaskGatewayPort

    @Binds
    @Singleton
    abstract fun bindBranchHeadPort(impl: GatewayABranchHeadAdapter): BranchHeadPort

    @Binds
    @Singleton
    abstract fun bindStatusTransitionPort(impl: GatewayAStatusTransitionAdapter): StatusTransitionPort

    @Binds
    @Singleton
    abstract fun bindUploadPort(impl: GatewayAUploadAdapter): UploadPort

    @Binds
    @Singleton
    abstract fun bindOperationLookupPort(impl: GatewayAOperationLookupAdapter): OperationLookupPort

    @Binds
    @Singleton
    abstract fun bindAuditReadPort(impl: GatewayAAuditReadAdapter): AuditReadPort
}
