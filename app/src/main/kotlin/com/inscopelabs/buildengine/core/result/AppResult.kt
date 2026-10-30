package com.inscopelabs.buildengine.core.result

/**
 * The result type used across the data and domain layers.
 *
 * The console draws a hard line between "the operation failed" and "the operation
 * failed *in a way the operator must see differently*". A gateway refusal is not
 * one error: a scope mismatch, a replayed capability, a drifted head and an active
 * halt are four different situations with four different remedies, and collapsing
 * them into a single string would hide that. [Failure] therefore carries a stable
 * machine-readable [code] alongside the human-readable [message], so the UI can
 * branch on the code and the operator still gets a sentence.
 *
 * @param T the value carried by a success.
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

    /** The success value, or `null` when this is a failure. */
    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }

    /** The failure, or `null` when this is a success. */
    fun errorOrNull(): Failure? = when (this) {
        is Success -> null
        is Failure -> this
    }

    /** Transforms the success value, leaving a failure untouched. */
    fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    /** Transforms the failure, leaving a success untouched. */
    fun mapFailure(transform: (Failure) -> Failure): AppResult<T> = when (this) {
        is Success -> this
        is Failure -> transform(this)
    }

    /** Transforms the success value, turning a thrown exception into a failure. */
    fun <R> mapCatching(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> try {
            Success(transform(value))
        } catch (error: Exception) {
            Failure(code = CODE_UNEXPECTED, message = error.message ?: error::class.java.simpleName)
        }
        is Failure -> this
    }

    /** Runs [action] when this is a success, and returns this result unchanged. */
    fun onSuccess(action: (T) -> Unit): AppResult<T> {
        if (this is Success) action(value)
        return this
    }

    /** Runs [action] when this is a failure, and returns this result unchanged. */
    fun onFailure(action: (Failure) -> Unit): AppResult<T> {
        if (this is Failure) action(this)
        return this
    }

    /**
     * The success value, or the result of [defaultValue] for a failure.
     *
     * The result type is a method-level type parameter rather than [T] itself, and
     * that is not a style choice. `Failure` is an `AppResult<Nothing>`, so a member
     * that returns the class's own type parameter is specialised to `Void` in
     * `Failure` — the compiler emits a `checkcast java/lang/Void` around the
     * result, and a non-null fallback then fails with a `ClassCastException` the
     * moment it is used on a failure. A method-level parameter erases to `Object`,
     * which is why `kotlin.Result.getOrElse` is shaped the same way.
     */
    @Suppress("UNCHECKED_CAST")
    fun <R> getOrElse(defaultValue: (Failure) -> R): R = when (this) {
        is Success -> value as R
        is Failure -> defaultValue(this)
    }

    /** Collapses both branches into a single value. */
    fun <R> fold(onSuccess: (T) -> R, onFailure: (Failure) -> R): R = when (this) {
        is Success -> onSuccess(value)
        is Failure -> onFailure(this)
    }

    companion object {
        /** The code used for a failure that did not come from the gateway. */
        const val CODE_UNEXPECTED: String = "unexpected"
    }
}
