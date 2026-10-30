package com.inscopelabs.buildengine.ui.common

/**
 * The state shape shared by every screen.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 */
sealed interface UiState<out T> {

    data object Idle : UiState<Nothing>

    data object Loading : UiState<Nothing>

    data class Success<T>(val data: T) : UiState<T>

    data class Error(val message: String) : UiState<Nothing>
}
