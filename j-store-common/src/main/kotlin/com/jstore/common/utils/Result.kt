package com.jstore.common.utils

sealed class Result<out T, out E>

data class Success<out T>(val value: T) : Result<T, Nothing>()
data class Failure<out E>(val error: E) : Result<Nothing, E>()

internal class FailureToUnwrapper(message: String) : IllegalStateException(message)

internal fun Result<*, *>.throwOnFailure(message: String) {
    if (this is Failure) throw FailureToUnwrapper(message)
}

fun <T, E> Result<T, E>.unwrapper(): T {
    throwOnFailure("called `Result::unwrapper()` on an `Err` value")
    val success = this as Success<T>
    return success.value
}

inline fun <T, E> Result<T, E>.onSuccess(op: (T) -> Unit): Result<T, E> {
    if (this is Success) {
        op(this.value)
    }
    return this
}

inline fun <T, E> Result<T, E>.onFailure(op: (error: E) -> Unit): Result<T, E> {
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





