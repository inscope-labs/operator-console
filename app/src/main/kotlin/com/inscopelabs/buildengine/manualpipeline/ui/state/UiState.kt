package com.inscopelabs.buildengine.manualpipeline.ui.state

import com.inscopelabs.buildengine.manualpipeline.domain.model.FailureCategory

/**
 * Sealed UI state wrapper used across manual pipeline ViewModels.
 */
sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(
        val message: String,
        val category: FailureCategory = FailureCategory.UNKNOWN,
        val code: String? = null,
    ) : UiState<Nothing>
}
