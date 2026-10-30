package com.inscopelabs.buildengine.core.result

/**
 * The result type used across the data and domain layers.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 */
sealed interface AppResult<out T> {

    data class Success<T>(val value: T) : AppResult<T>

    data class Failure(
        val code: String,
        val message: String,
    ) : AppResult<Nothing>
}
