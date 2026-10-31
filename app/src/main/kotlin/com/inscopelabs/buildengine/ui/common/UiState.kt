package com.inscopelabs.buildengine.ui.common

/**
 * The state shape shared by every screen.
 *
 * Four states, and the split between them is the point. `Idle` is "nothing has
 * happened yet", `Loading` is "work is in flight", `Success` carries the data, and
 * `Error` is for a failure the screen cannot usefully continue past.
 *
 * A failure the operator can act on — a refused credential, a refused scope — is
 * not an `Error` in this sense. It is data: the screen shows it and stays usable.
 * `Error` is for the case where there is nothing to show.
 */
sealed interface UiState<out T> {

    data object Idle : UiState<Nothing>

    data object Loading : UiState<Nothing>

    data class Success<T>(val data: T) : UiState<T>

    data class Error(val message: String) : UiState<Nothing>
}
