package com.jstore.common.utils

import java.io.Serializable
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

sealed class Result<out T, out E>: Serializable {
    /**
     * Returns `true` if this instance represents a successful outcome.
     * In this case [isFailure] returns `false`.
     */
    val isSuccess: Boolean get() = this is Success

    /**
     * Returns `true` if this instance represents a failed outcome.
     * In this case [isSuccess] returns `false`.
     */
    val isFailure: Boolean get() = this is Failure
}

data class Success<out T>(val value: T) : Result<T, Nothing>()
data class Failure<out E>(val error: E) : Result<Nothing, E>()

internal class FailureToUnwrapper(message: String) : IllegalStateException(message)

internal fun Result<*, *>.throwOnFailure(message: String) {
    if (this is Failure) throw FailureToUnwrapper(message)
}

fun <T, E> Result<T, E>.getOrThrow(): T {
    throwOnFailure("called `Result::unwrapper()` on an `Err` value")
    val success = this as Success<T>
    return success.value
}

@OptIn(ExperimentalContracts::class)
inline fun <T, E> Result<T, E>.onSuccess(op: (T) -> Unit): Result<T, E> {
    contract {
        callsInPlace(op, InvocationKind.AT_MOST_ONCE)
    }
    if (this is Success) {
        op(this.value)
    }
    return this
}


@OptIn(ExperimentalContracts::class)
inline fun <T, E> Result<T, E>.onFailure(op: (error: E) -> Unit): Result<T, E> {
    contract {
        callsInPlace(op, InvocationKind.AT_MOST_ONCE)
    }
    if (this is Failure) {
        op(this.error)
    }
    return this
}

inline fun <T, E, R> Result<T, E>.map(op: (T) -> R): Result<R, E> {
    return when (this) {
        is Success -> Success(op(this.value))
        is Failure -> this
    }
}

inline fun <T, E, R> Result<T, E>.mapError(op: (E) -> R): Result<T, R> {
    return when (this) {
        is Success -> this
        is Failure -> Failure(op(error))
    }
}

inline fun <T, E, R> Result<T, E>.mapOr(op: (T) -> R, default: R): R {
    return when (this) {
        is Success -> op(this.value)
        is Failure -> default
    }
}

inline fun <T, E, R> Result<T, E>.mapOrElse(op: (T) -> R, default: (E) -> R): R {
    return when (this) {
        is Success -> op(this.value)
        is Failure -> default(this.error)
    }
}

inline fun <R> runCatching(block: () -> R): Result<R, Throwable> {
    return try {
        Success(block())
    } catch (e: Throwable) {
        Failure(e)
    }
}

inline fun <T, R> T.runCatching(block: T.() -> R): Result<R, Throwable> {
    return try {
        Success(block())
    } catch (e: Throwable) {
        Failure(e)
    }
}

@OptIn(ExperimentalContracts::class)
inline fun <R, T, E> Result<T, E>.fold(
    onSuccess: (value: T) -> R,
    onFailure: (exception: E) -> R
): R {
    contract {
        callsInPlace(onSuccess, InvocationKind.AT_MOST_ONCE)
        callsInPlace(onFailure, InvocationKind.AT_MOST_ONCE)
    }
    return when (this) {
        is Success -> onSuccess(value)
        is Failure -> onFailure(error)
    }
}





