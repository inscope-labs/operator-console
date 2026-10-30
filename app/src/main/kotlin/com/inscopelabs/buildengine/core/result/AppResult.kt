package com.inscopelabs.buildengine.core.result

/**
 * The result type used across the data and domain layers.
 */
sealed interface AppResult<out T> {

    data class Success<T>(val value: T) : AppResult<T>

    data class Failure(
        val code: String,
        val message: String,
    ) : AppResult<Nothing>

    val isSuccess: Boolean
        get() = this is Success

    val isFailure: Boolean
        get() = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> IllegalStateException("[$code] $message").let { throw it }
    }
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}

inline fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R> = when (this) {
    is AppResult.Success -> transform(value)
    is AppResult.Failure -> this
}

inline fun <T, R> AppResult<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (code: String, message: String) -> R,
): R = when (this) {
    is AppResult.Success -> onSuccess(value)
    is AppResult.Failure -> onFailure(code, message)
}
