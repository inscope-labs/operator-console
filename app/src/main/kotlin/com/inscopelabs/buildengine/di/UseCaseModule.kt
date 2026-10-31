package com.inscopelabs.buildengine.di

import com.inscopelabs.buildengine.capability.CapabilityMinter
import com.inscopelabs.buildengine.core.security.BiometricGate
import com.inscopelabs.buildengine.domain.repository.OperationRepository
import com.inscopelabs.buildengine.domain.repository.PlanRepository
import com.inscopelabs.buildengine.domain.repository.TaskRepository
import com.inscopelabs.buildengine.domain.usecase.common.ConfirmAndMintCapabilityUseCase
import com.inscopelabs.buildengine.domain.usecase.common.VerifyBranchHeadUseCase
import com.inscopelabs.buildengine.domain.usecase.enrollment.EnrollDeviceUseCase
import com.inscopelabs.buildengine.domain.usecase.enrollment.ReportDevicePostureUseCase
import com.inscopelabs.buildengine.domain.usecase.enrollment.RotateDeviceKeyUseCase
import com.inscopelabs.buildengine.domain.usecase.read.GetNextTasksUseCase
import com.inscopelabs.buildengine.domain.usecase.read.GetPlansUseCase
import com.inscopelabs.buildengine.domain.usecase.read.GetStatusUseCase
import com.inscopelabs.buildengine.domain.usecase.read.RenderCheckUseCase
import com.inscopelabs.buildengine.domain.usecase.read.ValidatePlanUseCase
import com.inscopelabs.buildengine.domain.usecase.write.CreateWorkOrderUseCase
import com.inscopelabs.buildengine.domain.usecase.write.DecideFrameworkUseCase
import com.inscopelabs.buildengine.domain.usecase.write.HaltUseCase
import com.inscopelabs.buildengine.domain.usecase.write.PublishTaskUseCase
import com.inscopelabs.buildengine.domain.usecase.write.ReconcileUseCase
import com.inscopelabs.buildengine.domain.usecase.write.ResumeUseCase
import com.inscopelabs.buildengine.domain.usecase.write.SetTaskStatusUseCase
import com.inscopelabs.buildengine.domain.usecase.write.UploadSubmissionUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The use cases, one per operation.
 *
 * Design review §12: one use case per privileged operation, and each write use
 * case mints its own scoped capability — none of them may widen their scope, and
 * none of them may mint their own (that is
 * `domain.usecase.common.ConfirmAndMintCapabilityUseCase`'s job alone).
 *
 * The read use cases are here now and the write ones are bound here ahead of
 * their implementations; the bindings are the module's, and the bodies behind
 * them are their own stages' work.
 */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    @Singleton
    fun provideConfirmAndMintCapabilityUseCase(
        capabilityMinter: CapabilityMinter,
        biometricGate: BiometricGate,
    ): ConfirmAndMintCapabilityUseCase =
        ConfirmAndMintCapabilityUseCase(capabilityMinter, biometricGate)

    @Provides
    @Singleton
    fun provideVerifyBranchHeadUseCase(
        taskRepository: TaskRepository,
    ): VerifyBranchHeadUseCase = VerifyBranchHeadUseCase(taskRepository)

    @Provides
    @Singleton
    fun provideGetPlansUseCase(planRepository: PlanRepository): GetPlansUseCase =
        GetPlansUseCase(planRepository)

    @Provides
    @Singleton
    fun provideGetNextTasksUseCase(taskRepository: TaskRepository): GetNextTasksUseCase =
        GetNextTasksUseCase(taskRepository)

    @Provides
    @Singleton
    fun provideValidatePlanUseCase(planRepository: PlanRepository): ValidatePlanUseCase =
        ValidatePlanUseCase(planRepository)

    @Provides
    @Singleton
    fun provideRenderCheckUseCase(planRepository: PlanRepository): RenderCheckUseCase =
        RenderCheckUseCase(planRepository)

    @Provides
    @Singleton
    fun provideGetStatusUseCase(planRepository: PlanRepository): GetStatusUseCase =
        GetStatusUseCase(planRepository)

    @Provides
    @Singleton
    fun providePublishTaskUseCase(
        operationRepository: OperationRepository,
        confirmAndMint: ConfirmAndMintCapabilityUseCase,
    ): PublishTaskUseCase = PublishTaskUseCase(operationRepository, confirmAndMint)

    @Provides
    @Singleton
    fun provideSetTaskStatusUseCase(
        taskRepository: TaskRepository,
        confirmAndMint: ConfirmAndMintCapabilityUseCase,
    ): SetTaskStatusUseCase = SetTaskStatusUseCase(taskRepository, confirmAndMint)

    @Provides
    @Singleton
    fun provideCreateWorkOrderUseCase(
        operationRepository: OperationRepository,
        confirmAndMint: ConfirmAndMintCapabilityUseCase,
    ): CreateWorkOrderUseCase = CreateWorkOrderUseCase(operationRepository, confirmAndMint)

    @Provides
    @Singleton
    fun provideUploadSubmissionUseCase(
        operationRepository: OperationRepository,
        confirmAndMint: ConfirmAndMintCapabilityUseCase,
    ): UploadSubmissionUseCase = UploadSubmissionUseCase(operationRepository, confirmAndMint)

    @Provides
    @Singleton
    fun provideReconcileUseCase(
        operationRepository: OperationRepository,
        confirmAndMint: ConfirmAndMintCapabilityUseCase,
    ): ReconcileUseCase = ReconcileUseCase(operationRepository, confirmAndMint)

    @Provides
    @Singleton
    fun provideDecideFrameworkUseCase(
        operationRepository: OperationRepository,
        confirmAndMint: ConfirmAndMintCapabilityUseCase,
    ): DecideFrameworkUseCase = DecideFrameworkUseCase(operationRepository, confirmAndMint)

    @Provides
    @Singleton
    fun provideHaltUseCase(
        operationRepository: OperationRepository,
        confirmAndMint: ConfirmAndMintCapabilityUseCase,
    ): HaltUseCase = HaltUseCase(operationRepository, confirmAndMint)

    @Provides
    @Singleton
    fun provideResumeUseCase(
        operationRepository: OperationRepository,
        confirmAndMint: ConfirmAndMintCapabilityUseCase,
    ): ResumeUseCase = ResumeUseCase(operationRepository, confirmAndMint)

    @Provides
    @Singleton
    fun provideEnrollDeviceUseCase(
        deviceKeyManager: com.inscopelabs.buildengine.core.crypto.DeviceKeyManager,
    ): EnrollDeviceUseCase = EnrollDeviceUseCase(deviceKeyManager)

    @Provides
    @Singleton
    fun provideRotateDeviceKeyUseCase(
        deviceKeyManager: com.inscopelabs.buildengine.core.crypto.DeviceKeyManager,
    ): RotateDeviceKeyUseCase = RotateDeviceKeyUseCase(deviceKeyManager)

    @Provides
    @Singleton
    fun provideReportDevicePostureUseCase(
        strongBoxSupport: com.inscopelabs.buildengine.core.crypto.StrongBoxSupport,
        deviceAttestation: com.inscopelabs.buildengine.core.security.DeviceAttestation,
    ): ReportDevicePostureUseCase =
        ReportDevicePostureUseCase(strongBoxSupport, deviceAttestation)
}
