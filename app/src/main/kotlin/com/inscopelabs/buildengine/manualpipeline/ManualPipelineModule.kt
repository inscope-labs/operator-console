package com.inscopelabs.buildengine.manualpipeline

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Root Hilt marker for the isolated `manualpipeline` feature module.
 * Host application code outside `manualpipeline` may import ONLY from `manualpipeline.public`.
 */
@Module
@InstallIn(SingletonComponent::class)
object ManualPipelineModule
