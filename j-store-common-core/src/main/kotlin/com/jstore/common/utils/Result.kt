package com.jstore.common.utils

import java.io.Serializable
import java.util.concurrent.CancellationException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

sealed class Result<out T, out E> : Serializable {
    /**
     * Returns `true` if this instance represents a successful outcome. In this case [isFailure]
     * returns `false`.
     */
    val isSuccess: Boolean
        get() = this is Success

    /**
     * Returns `true` if this instance represents a failed outcome. In this case [isSuccess] returns
     * `false`.
     */
    val isFailure: Boolean
        get() = this is Failure
}

data class Success<out T>(val value: T) : Result<T, Nothing>()

data class Failure<out E>(val error: E) : Result<Nothing, E>()

/** Java-friendly factory methods for Result. Usage: Results.ok(42), Results.err("boom") */
object Results {
    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T, E> ok(value: T): Result<T, E> = Success(value) as Result<T, E>

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T, E> err(error: E): Result<T, E> = Failure(error) as Result<T, E>
}

class ResultUnwrapException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

/** Returns the successful value or throws the exception produced by [exceptionMapper]. */
@OptIn(ExperimentalContracts::class)
inline fun <T, E> Result<T, E>.getOrThrow(exceptionMapper: (E) -> Throwable): T {
    contract {
        callsInPlace(exceptionMapper, InvocationKind.AT_MOST_ONCE)
    }
    return when (this) {
        is Success -> value
        is Failure -> throw exceptionMapper(error)
    }
}

/** Rust: `unwrap_err()` — panics with value context on Success. */
fun <T, E> Result<T, E>.getErrorOrThrow(): E =
    when (this) {
        is Success ->
            throw ResultUnwrapException("called Result::unwrap_err() on a Success value: $value")
        is Failure -> error
    }

/** Rust: `expect(msg)` — panics with a custom message on Failure. */
fun <T, E> Result<T, E>.expect(message: String): T =
    when (this) {
        is Success -> value
        is Failure -> throw ResultUnwrapException("$message: $error", error as? Throwable)
    }

/** Rust: `unwrap_or(default)` — returns default on Failure, never throws. */
fun <T, E> Result<T, E>.getOrDefault(default: @UnsafeVariance T): T =
    when (this) {
        is Success -> value
        is Failure -> default
    }

/** Rust: `unwrap_or_else(op)` — computes default from error on Failure. */
@OptIn(ExperimentalContracts::class)
inline fun <T, E> Result<T, E>.getOrElse(default: (E) -> @UnsafeVariance T): T {
    contract {
        callsInPlace(default, InvocationKind.AT_MOST_ONCE)
    }
    return when (this) {
        is Success -> value
        is Failure -> default(error)
    }
}

/** Rust: `is_ok_and(f)` — returns `true` if Success and the predicate holds. */
@OptIn(ExperimentalContracts::class)
inline fun <T, E> Result<T, E>.isSuccessAnd(predicate: (T) -> Boolean): Boolean {
    contract {
        callsInPlace(predicate, InvocationKind.AT_MOST_ONCE)
    }
    return this is Success && predicate(this.value)
}

/** Rust: `is_err_and(f)` — returns `true` if Failure and the predicate holds. */
@OptIn(ExperimentalContracts::class)
inline fun <T, E> Result<T, E>.isFailureAnd(predicate: (E) -> Boolean): Boolean {
    contract {
        callsInPlace(predicate, InvocationKind.AT_MOST_ONCE)
    }
    return this is Failure && predicate(this.error)
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

@OptIn(ExperimentalContracts::class)
inline fun <T, E, R> Result<T, E>.map(op: (T) -> R): Result<R, E> {
    contract {
        callsInPlace(op, InvocationKind.AT_MOST_ONCE)
    }
    return when (this) {
        is Success -> Success(op(this.value))
        is Failure -> this
    }
}

@OptIn(ExperimentalContracts::class)
inline fun <T, E, R> Result<T, E>.mapError(op: (E) -> R): Result<T, R> {
    contract {
        callsInPlace(op, InvocationKind.AT_MOST_ONCE)
    }
    return when (this) {
        is Success -> this
        is Failure -> Failure(op(error))
    }
}

@OptIn(ExperimentalContracts::class)
inline fun <T, E, R> Result<T, E>.mapOr(op: (T) -> R, default: R): R {
    contract {
        callsInPlace(op, InvocationKind.AT_MOST_ONCE)
    }
    return when (this) {
        is Success -> op(this.value)
        is Failure -> default
    }
}

@OptIn(ExperimentalContracts::class)
inline fun <T, E, R> Result<T, E>.mapOrElse(op: (T) -> R, default: (E) -> R): R {
    contract {
        callsInPlace(op, InvocationKind.AT_MOST_ONCE)
        callsInPlace(default, InvocationKind.AT_MOST_ONCE)
    }
    return when (this) {
        is Success -> op(this.value)
        is Failure -> default(this.error)
    }
}

/**
 * Rust: `and_then(op)` — chains operations that return Result, avoids nesting. This is the most
 * critical combinator for composing fallible operations.
 */
@OptIn(ExperimentalContracts::class)
inline fun <T, E, R> Result<T, E>.flatMap(op: (T) -> Result<R, @UnsafeVariance E>): Result<R, E> {
    contract {
        callsInPlace(op, InvocationKind.AT_MOST_ONCE)
    }
    return when (this) {
        is Success -> op(this.value)
        is Failure -> this
    }
}

/** Rust: `or_else(op)` — on Failure, attempt recovery with a new Result. */
@OptIn(ExperimentalContracts::class)
inline fun <T, E, R> Result<T, E>.orElse(op: (E) -> Result<@UnsafeVariance T, R>): Result<T, R> {
    contract {
        callsInPlace(op, InvocationKind.AT_MOST_ONCE)
    }
    return when (this) {
        is Success -> this
        is Failure -> op(this.error)
    }
}

/** Rust: `and(other)` — returns [other] if this is Success, otherwise propagates Failure. */
fun <T, E, R> Result<T, E>.and(other: Result<R, @UnsafeVariance E>): Result<R, E> =
    when (this) {
        is Success -> other
        is Failure -> this
    }

/** Rust: `or(other)` — returns this if Success, otherwise returns [other]. */
fun <T, E, R> Result<T, E>.or(other: Result<@UnsafeVariance T, R>): Result<T, R> =
    when (this) {
        is Success -> this
        is Failure -> other
    }

/** Rust: `flatten()` — unwraps a nested Result<Result<T, E>, E> into Result<T, E>. */
fun <T, E> Result<Result<T, E>, E>.flatten(): Result<T, E> =
    when (this) {
        is Success -> this.value
        is Failure -> this
    }

/**
 * Rust: `transpose()` — converts Result<T?, E> into Result<T, E>?. Returns `null` if Success(null),
 * otherwise wraps non-null value.
 */
fun <T : Any, E> Result<T?, E>.transpose(): Result<T, E>? =
    when (this) {
        is Success -> value?.let { Success(it) }
        is Failure -> this
    }

/** Wraps a block execution into a Result, catching exceptions. */
inline fun <R> resultOf(block: () -> R): Result<R, Exception> {
    return try {
        Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw e
    } catch (e: Exception) {
        Failure(e)
    }
}

/** Wraps a block execution with receiver into a Result, catching exceptions. */
inline fun <T, R> T.runResultOf(block: T.() -> R): Result<R, Exception> {
    return try {
        Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw e
    } catch (e: Exception) {
        Failure(e)
    }
}

@OptIn(ExperimentalContracts::class)
inline fun <R, T, E> Result<T, E>.fold(
    onSuccess: (value: T) -> R,
    onFailure: (exception: E) -> R,
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
