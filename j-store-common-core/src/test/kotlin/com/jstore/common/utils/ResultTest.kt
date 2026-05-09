package com.jstore.common.utils

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equality.shouldNotBeEqualToComparingFieldsExcept
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf


class ResultTest : FunSpec({

    // ========== isSuccess / isFailure ==========

    test("isSuccess returns true for Success") {
        val result: Result<Int, String> = Success(42)
        result.isSuccess shouldBe true
        result.isFailure shouldBe false
    }

    test("isFailure returns true for Failure") {
        val result: Result<Int, String> = Failure("error")
        result.isFailure shouldBe true
        result.isSuccess shouldBe false
    }

    // ========== getOrThrow ==========

    test("getOrThrow returns value on Success") {
        Success(42).getOrThrow() shouldBe 42
    }

    test("getOrThrow throws ResultUnwrapException with error context on Failure") {
        val ex = shouldThrow<ResultUnwrapException> {
            Failure("something broke").getOrThrow()
        }
        ex.message shouldContain "something broke"
    }

    // ========== expect ==========

    test("expect returns value on Success") {
        Success("ok").expect("should not fail") shouldBe "ok"
    }

    test("expect throws with custom message on Failure") {
        val ex = shouldThrow<ResultUnwrapException> {
            Failure(404).expect("resource not found")
        }
        ex.message shouldContain "resource not found"
        ex.message shouldContain "404"
    }

    // ========== getErrorOrThrow ==========

    test("getErrorOrThrow returns error on Failure") {
        Failure("oops").getErrorOrThrow() shouldBe "oops"
    }

    test("getErrorOrThrow throws ResultUnwrapException on Success") {
        val ex = shouldThrow<ResultUnwrapException> {
            Success(42).getErrorOrThrow()
        }
        ex.message shouldContain "42"
    }

    // ========== getOrDefault ==========

    test("getOrDefault returns value on Success") {
        Success(10).getOrDefault(0) shouldBe 10
    }

    test("getOrDefault returns default on Failure") {
        Failure("err").getOrDefault(0) shouldBe 0
    }

    // ========== getOrElse ==========

    test("getOrElse returns value on Success") {
        Success(10).getOrElse { -1 } shouldBe 10
    }

    test("getOrElse computes default from error on Failure") {
        Failure("len3").getOrElse { it.length } shouldBe 4
    }

    // ========== isSuccessAnd / isFailureAnd ==========

    test("isSuccessAnd returns true when Success and predicate holds") {
        Success(10).isSuccessAnd { it > 5 } shouldBe true
    }

    test("isSuccessAnd returns false when Success but predicate fails") {
        Success(3).isSuccessAnd { it > 5 } shouldBe false
    }

    test("isSuccessAnd returns false on Failure without evaluating predicate") {
        var evaluated = false
        Failure("err").isSuccessAnd { evaluated = true; true } shouldBe false
        evaluated shouldBe false
    }

    test("isFailureAnd returns true when Failure and predicate holds") {
        Failure("not found").isFailureAnd { it.contains("not") } shouldBe true
    }

    test("isFailureAnd returns false when Failure but predicate fails") {
        Failure("timeout").isFailureAnd { it.contains("not") } shouldBe false
    }

    test("isFailureAnd returns false on Success without evaluating predicate") {
        var evaluated = false
        Success(1).isFailureAnd { evaluated = true; true } shouldBe false
        evaluated shouldBe false
    }

    // ========== onSuccess / onFailure ==========

    test("onSuccess invokes callback on Success and returns same Result") {
        var captured = 0
        val result = Success(42).onSuccess { captured = it }
        captured shouldBe 42
        result shouldBe Success(42)
    }

    test("onSuccess does not invoke callback on Failure") {
        var invoked = false
        Failure("err").onSuccess { invoked = true }
        invoked shouldBe false
    }

    test("onFailure invokes callback on Failure and returns same Result") {
        var captured = ""
        val result = Failure("oops").onFailure { captured = it }
        captured shouldBe "oops"
        result shouldBe Failure("oops")
    }

    test("onFailure does not invoke callback on Success") {
        var invoked = false
        Success(1).onFailure { invoked = true }
        invoked shouldBe false
    }

    // ========== map ==========

    test("map transforms value on Success") {
        Success(5).map { it * 2 } shouldBe Success(10)
    }

    test("map propagates Failure unchanged") {
        val result: Result<Int, String> = Failure("err")
        result.map { it * 2 } shouldBe Failure("err")
    }

    // ========== mapError ==========

    test("mapError transforms error on Failure") {
        Failure("err").mapError { it.length } shouldBe Failure(3)
    }

    test("mapError propagates Success unchanged") {
        val result: Result<Int, String> = Success(42)
        result.mapError { it.length } shouldBe Success(42)
    }

    // ========== mapOr ==========

    test("mapOr applies op on Success") {
        Success(5).mapOr({ it * 3 }, 0) shouldBe 15
    }

    test("mapOr returns default on Failure") {
        Failure("err").mapOr({ 999 }, 0) shouldBe 0
    }

    // ========== mapOrElse ==========

    test("mapOrElse applies op on Success") {
        Success(5).mapOrElse({ it * 3 }, { -1 }) shouldBe 15
    }

    test("mapOrElse applies default on Failure") {
        Failure("abc").mapOrElse({ 999 }, { it.length }) shouldBe 3
    }

    // ========== flatMap (and_then) ==========

    test("flatMap chains on Success") {
        val result = Success(10).flatMap { Success(it + 5) }
        result shouldBe Success(15)
    }

    test("flatMap short-circuits on Failure") {
        val result: Result<Int, String> = Failure("first error")
        result.flatMap { Success(it + 5) } shouldBe Failure("first error")
    }

    test("flatMap propagates inner Failure") {
        val result = Success(10).flatMap { Failure("inner error") }
        result shouldBe Failure("inner error")
    }

    test("flatMap chains multiple operations") {
        fun parse(s: String): Result<Int, String> =
            s.toIntOrNull()?.let { Success(it) } ?: Failure("not a number: $s")

        fun positive(n: Int): Result<Int, String> =
            if (n > 0) Success(n) else Failure("not positive: $n")

        parse("42").flatMap { positive(it) } shouldBe Success(42)
        parse("abc").flatMap { positive(it) } shouldBe Failure("not a number: abc")
        parse("-1").flatMap { positive(it) } shouldBe Failure("not positive: -1")
    }

    // ========== orElse ==========

    test("orElse returns Success unchanged") {
        val result: Result<Int, String> = Success(42)
        result.orElse { Failure(it.length) } shouldBe Success(42)
    }

    test("orElse attempts recovery on Failure") {
        val result: Result<Int, String> = Failure("err")
        result.orElse { Success(0) } shouldBe Success(0)
    }

    test("orElse can transform error type") {
        val result: Result<Int, String> = Failure("err")
        result.orElse { Failure(it.length) } shouldBe Failure(3)
    }

    // ========== and ==========

    test("and returns other on Success") {
        val result: Result<Int, String> = Success(1)
        result.and(Success("hello")) shouldBe Success("hello")
    }

    test("and propagates Failure") {
        val result: Result<Int, String> = Failure("err")
        result.and(Success("hello")) shouldBe Failure("err")
    }

    // ========== or ==========

    test("or returns this on Success") {
        val result: Result<Int, String> = Success(1)
        result.or(Success(2)) shouldBe Success(1)
    }

    test("or returns other on Failure") {
        val result: Result<Int, String> = Failure("err")
        result.or(Success(2)) shouldBe Success(2)
    }

    // ========== flatten ==========

    test("flatten unwraps nested Success") {
        val nested: Result<Result<Int, String>, String> = Success(Success(42))
        nested.flatten() shouldBe Success(42)
    }

    test("flatten unwraps nested Failure") {
        val nested: Result<Result<Int, String>, String> = Success(Failure("inner"))
        nested.flatten() shouldBe Failure("inner")
    }

    test("flatten propagates outer Failure") {
        val nested: Result<Result<Int, String>, String> = Failure("outer")
        nested.flatten() shouldBe Failure("outer")
    }

    // ========== resultOf ==========

    test("resultOf wraps successful computation") {
        resultOf { 1 + 1 } shouldBe Success(2)
    }

    test("resultOf wraps exception as Failure") {
        val result = resultOf { throw IllegalArgumentException("bad") }
        result.shouldBeInstanceOf<Failure<Exception>>()
        result.error.message shouldBe "bad"
    }

    test("resultOf wraps null return as Success(null)") {
        val result = resultOf { null }
        result shouldBe Success(null)
    }

    test("resultOf captures RuntimeException subtypes") {
        val result = resultOf { throw NumberFormatException("not a number") }
        result.shouldBeInstanceOf<Failure<Exception>>()
        result.error.shouldBeInstanceOf<NumberFormatException>()
    }

    test("resultOf captures checked Exception subtypes") {
        val result = resultOf { throw java.io.IOException("disk full") }
        result.shouldBeInstanceOf<Failure<Exception>>()
        result.error.shouldBeInstanceOf<java.io.IOException>()
        result.error.message shouldBe "disk full"
    }

    test("resultOf does not catch Error (e.g. OutOfMemoryError, StackOverflowError)") {
        shouldThrow<StackOverflowError> {
            resultOf { throw StackOverflowError("boom") }
        }
    }

    test("resultOf preserves exception cause chain") {
        val cause = IllegalStateException("root cause")
        val result = resultOf { throw RuntimeException("wrapper", cause) }
        result.shouldBeInstanceOf<Failure<Exception>>()
        result.error.cause shouldBe cause
    }

    test("resultOf result is composable with map and flatMap") {
        val result = resultOf { "42".toInt() }
            .map { it * 2 }
            .flatMap { if (it > 0) Success(it) else Failure(Exception("non-positive")) }
        result shouldBe Success(84)
    }

    test("resultOf failed result is composable — map is skipped, flatMap is skipped") {
        val sideEffects = mutableListOf<String>()
        val result: Result<Any, Exception> = resultOf { throw IllegalArgumentException("fail") }
        val chained = result
            .map { sideEffects.add("map"); it }
            .flatMap { sideEffects.add("flatMap"); Success(it) }
        chained.shouldBeInstanceOf<Failure<Exception>>()
        sideEffects shouldBe emptyList()
    }

    test("extension runResultOf wraps receiver computation") {
        val result = "hello".runResultOf { uppercase() }
        result shouldBe Success("HELLO")
    }

    test("runResultOf wraps receiver exception as Failure") {
        val result = "not a number".runResultOf { toInt() }
        result.shouldBeInstanceOf<Failure<Exception>>()
        result.error.shouldBeInstanceOf<NumberFormatException>()
    }

    // ========== transpose ==========

    test("transpose converts Success(non-null) to non-null Result") {
        val result: Result<String?, String> = Success("hello")
        result.transpose() shouldBe Success("hello")
    }

    test("transpose converts Success(null) to null") {
        val result: Result<String?, String> = Success(null)
        result.transpose() shouldBe null
    }

    test("transpose preserves Failure as non-null") {
        val result: Result<String?, String> = Failure("err")
        result.transpose() shouldBe Failure("err")
    }

    // ========== fold ==========

    test("fold applies onSuccess for Success") {
        Success(5).fold({ it * 2 }, { -1 }) shouldBe 10
    }

    test("fold applies onFailure for Failure") {
        Failure("err").fold({ 0 }, { it.length }) shouldBe 3
    }

    // ========== chaining ==========

    test("chaining map + flatMap + onSuccess + onFailure") {
        var log = ""
        val result = Success(10)
            .map { it + 5 }
            .flatMap { if (it > 10) Success(it) else Failure("too small") }
            .onSuccess { log += "ok:$it" }
            .onFailure { log += "err:$it" }

        result shouldBe Success(15)
        log shouldBe "ok:15"
    }

    test("chaining short-circuits at first Failure") {
        var log = ""
        val result = Success(3)
            .map { it + 2 }
            .flatMap { if (it > 10) Success(it) else Failure("too small: $it") }
            .map { it * 100 } // should not execute
            .onSuccess { log += "ok" }
            .onFailure { log += "err:$it" }

        result shouldBe Failure("too small: 5")
        log shouldBe "err:too small: 5"
    }

    // ========== data class equality ==========

    test("Success equality by value") {
        Success(42) shouldBe Success(42)
    }

    test("Failure equality by error") {
        Failure("err") shouldBe Failure("err")
    }

    test("Success and Failure are never equal") {
        (Success(0) == Failure(0)) shouldBe false
    }

    test("test for ResultKt.mapOr") {
        val value = Results.ok<Int, String>(12)
    }
})
