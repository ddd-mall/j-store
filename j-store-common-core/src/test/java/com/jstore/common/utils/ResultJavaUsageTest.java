package com.jstore.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 展示如何在 Java 中使用 Kotlin 的 Result&lt;T, E&gt;。
 *
 * <p>核心要点：
 *
 * <ol>
 *   <li>使用 Results.ok(value) / Results.err(error) 工厂方法构造，避免 raw type 警告
 *   <li>Kotlin 扩展函数在 Java 中变成 ResultKt.xxx(result, ...) 静态调用
 *   <li>Java 21 pattern matching 可以直接 instanceof 匹配 Success/Failure
 *   <li>Lambda 传参用 Java 的 Function/Predicate 接口
 * </ol>
 */
@DisplayName("Result — Java 互操作用法展示")
class ResultJavaUsageTest {

    // ========== 构造 ==========

    @Nested
    @DisplayName("构造 Success / Failure")
    class Construction {

        @Test
        void createSuccess_viaFactory() {
            Result<Integer, String> result = Results.ok(42);
            assertTrue(result.isSuccess());
            assertFalse(result.isFailure());
        }

        @Test
        void createFailure_viaFactory() {
            Result<Integer, String> result = Results.err("something went wrong");
            assertTrue(result.isFailure());
            assertFalse(result.isSuccess());
        }
    }

    // ========== Pattern Matching (Java 21) ==========

    @Nested
    @DisplayName("Java 21 Pattern Matching")
    class PatternMatching {

        @Test
        void instanceofPatternMatchingOnSuccess() {
            Result<String, String> result = Results.ok("hello");

            if (result instanceof Success<?> s) {
                assertEquals("HELLO", ((String) s.getValue()).toUpperCase());
            } else {
                fail("Expected Success");
            }
        }

        @Test
        void instanceofPatternMatchingOnFailure() {
            Result<String, String> result = Results.err("not found");

            String output;
            if (result instanceof Success<?> s) {
                output = (String) s.getValue();
            } else if (result instanceof Failure<?> f) {
                output = "ERROR: " + f.getError();
            } else {
                throw new IllegalStateException("unreachable");
            }

            assertEquals("ERROR: not found", output);
        }
    }

    // ========== 扩展函数调用方式 ==========

    @Nested
    @DisplayName("Kotlin 扩展函数 → Java 静态方法调用")
    class ExtensionFunctions {

        @Test
        void getOrThrow_onSuccess() {
            Result<Integer, String> result = Results.ok(42);
            assertEquals(42, ResultKt.getOrThrow(result, IllegalStateException::new));
        }

        @Test
        void getOrThrow_onFailure_usesExplicitMapper() {
            Result<Integer, String> result = Results.err("boom");
            var ex =
                    assertThrows(
                            IllegalStateException.class,
                            () -> ResultKt.getOrThrow(result, IllegalStateException::new));
            assertTrue(ex.getMessage().contains("boom"));
        }

        @Test
        void getOrDefault_onSuccess() {
            Result<Integer, String> result = Results.ok(10);
            assertEquals(10, ResultKt.getOrDefault(result, 0));
        }

        @Test
        void getOrDefault_onFailure() {
            Result<Integer, String> result = Results.err("err");
            assertEquals(0, ResultKt.getOrDefault(result, 0));
        }

        @Test
        void getOrElse_onFailure_computesDefault() {
            Result<Integer, String> result = Results.err("error");
            assertEquals(5, ResultKt.getOrElse(result, err -> err.length()));
        }
    }

    // ========== 谓词检查 ==========

    @Nested
    @DisplayName("isSuccessAnd / isFailureAnd")
    class PredicateChecks {

        @Test
        void isSuccessAnd_trueWhenPredicateHolds() {
            assertTrue(ResultKt.isSuccessAnd(Results.ok(10), v -> v > 5));
        }

        @Test
        void isSuccessAnd_falseWhenPredicateFails() {
            assertFalse(ResultKt.isSuccessAnd(Results.ok(3), v -> v > 5));
        }

        @Test
        void isSuccessAnd_falseOnFailure() {
            assertFalse(ResultKt.isSuccessAnd(Results.<Integer, String>err("err"), v -> true));
        }

        @Test
        void isFailureAnd_trueWhenPredicateHolds() {
            assertTrue(ResultKt.isFailureAnd(Results.err("not found"), e -> e.contains("not")));
        }

        @Test
        void isFailureAnd_falseOnSuccess() {
            assertFalse(ResultKt.isFailureAnd(Results.<Integer, String>ok(1), e -> true));
        }
    }

    // ========== 转换操作 ==========

    @Nested
    @DisplayName("map / mapError / flatMap")
    class Transformations {

        @Test
        void map_onSuccess() {
            Result<Integer, String> result = Results.ok(5);
            Result<Integer, String> mapped = ResultKt.map(result, v -> v * 2);

            assertInstanceOf(Success.class, mapped);
            assertEquals(10, ((Success<?>) mapped).getValue());
        }

        @Test
        void map_onFailure_propagates() {
            Result<Integer, String> result = Results.err("err");
            Result<Integer, String> mapped = ResultKt.map(result, v -> v * 2);

            assertInstanceOf(Failure.class, mapped);
            assertEquals("err", ((Failure<?>) mapped).getError());
        }

        @Test
        void mapError_onFailure() {
            Result<Integer, String> result = Results.err("err");
            Result<Integer, Integer> mapped = ResultKt.mapError(result, String::length);

            assertInstanceOf(Failure.class, mapped);
            assertEquals(3, ((Failure<?>) mapped).getError());
        }

        @Test
        void flatMap_chainsOnSuccess() {
            Result<Integer, String> result = Results.ok(10);
            Result<Integer, String> chained =
                    ResultKt.flatMap(
                            result, v -> v > 5 ? Results.ok(v * 2) : Results.err("too small"));

            assertInstanceOf(Success.class, chained);
            assertEquals(20, ((Success<?>) chained).getValue());
        }

        @Test
        void flatMap_shortCircuitsOnFailure() {
            Result<Integer, String> result = Results.err("first error");
            Result<Integer, String> chained = ResultKt.flatMap(result, v -> Results.ok(v + 1));

            assertInstanceOf(Failure.class, chained);
            assertEquals("first error", ((Failure<?>) chained).getError());
        }

        @Test
        void flatMap_propagatesInnerFailure() {
            Result<Integer, String> result = Results.ok(3);
            Result<Integer, String> chained =
                    ResultKt.flatMap(
                            result, v -> v > 5 ? Results.ok(v) : Results.err("too small: " + v));

            assertInstanceOf(Failure.class, chained);
            assertEquals("too small: 3", ((Failure<?>) chained).getError());
        }
    }

    // ========== fold / mapOr / mapOrElse ==========

    @Nested
    @DisplayName("fold / mapOr / mapOrElse")
    class Extractors {

        @Test
        void fold_onSuccess() {
            Result<Integer, String> result = Results.ok(5);
            assertEquals("value=5", ResultKt.fold(result, v -> "value=" + v, e -> "error=" + e));
        }

        @Test
        void fold_onFailure() {
            Result<Integer, String> result = Results.err("oops");
            assertEquals("error=oops", ResultKt.fold(result, v -> "value=" + v, e -> "error=" + e));
        }

        @Test
        void mapOr_onSuccess() {
            assertEquals(15, ResultKt.mapOr(Results.<Integer, String>ok(5), v -> v * 3, 0));
        }

        @Test
        void mapOr_onFailure() {
            assertEquals(0, ResultKt.mapOr(Results.<Integer, String>err("err"), v -> v * 3, 0));
        }

        @Test
        void mapOrElse_onSuccess() {
            assertEquals(15, ResultKt.mapOrElse(Results.ok(5), v -> v * 3, String::length));
        }

        @Test
        void mapOrElse_onFailure() {
            assertEquals(
                    3,
                    ResultKt.mapOrElse(
                            Results.<Integer, String>err("abc"), v -> v * 3, String::length));
        }
    }

    // ========== 组合操作 ==========

    @Nested
    @DisplayName("and / or / orElse / flatten / transpose")
    class Combinators {

        @Test
        void and_onSuccess_returnsOther() {
            Result<String, String> combined = ResultKt.and(Results.ok(1), Results.ok("hello"));
            assertEquals(Results.ok("hello"), combined);
        }

        @Test
        void and_onFailure_propagates() {
            Result<String, String> combined = ResultKt.and(Results.err("err"), Results.ok("hello"));
            assertEquals(Results.err("err"), combined);
        }

        @Test
        void or_onSuccess_returnsThis() {
            Result<Integer, Integer> combined = ResultKt.or(Results.ok(1), Results.ok(2));
            assertEquals(Results.ok(1), combined);
        }

        @Test
        void or_onFailure_returnsOther() {
            Result<Integer, Integer> combined = ResultKt.or(Results.err("err"), Results.ok(2));
            assertEquals(Results.ok(2), combined);
        }

        @Test
        void orElse_recoversOnFailure() {
            Result<Integer, String> result = Results.err("err");
            Result<Integer, Integer> recovered = ResultKt.orElse(result, e -> Results.ok(0));
            assertEquals(Results.ok(0), recovered);
        }

        @Test
        void flatten_unwrapsNestedSuccess() {
            Result<Result<Integer, String>, String> nested = Results.ok(Results.ok(42));
            assertEquals(Results.ok(42), ResultKt.flatten(nested));
        }

        @Test
        void flatten_unwrapsNestedFailure() {
            Result<Result<Integer, String>, String> nested = Results.ok(Results.err("inner"));
            assertEquals(Results.err("inner"), ResultKt.flatten(nested));
        }

        @Test
        void transpose_successNonNull() {
            Result<String, String> result = Results.ok("hello");
            Result<String, String> transposed = ResultKt.transpose(result);
            assertNotNull(transposed);
            assertEquals(Results.ok("hello"), transposed);
        }

        @Test
        void transpose_successNull_returnsNull() {
            Result<String, String> result = Results.ok(null);
            assertNull(ResultKt.transpose(result));
        }

        @Test
        void transpose_failure_preserved() {
            Result<String, String> result = Results.err("err");
            assertEquals(Results.err("err"), ResultKt.transpose(result));
        }
    }

    // ========== resultOf ==========

    @Nested
    @DisplayName("resultOf — 异常捕获")
    class ResultOf {

        @Test
        void resultOf_wrapsSuccess() {
            Result<Integer, Exception> result = ResultKt.resultOf(() -> 1 + 1);
            assertEquals(Results.ok(2), result);
        }

        @Test
        void resultOf_wrapsException() {
            Result<Integer, Exception> result =
                    ResultKt.resultOf(
                            () -> {
                                throw new IllegalArgumentException("bad");
                            });
            assertInstanceOf(Failure.class, result);
            assertInstanceOf(IllegalArgumentException.class, ((Failure<?>) result).getError());
        }
    }

    // ========== 链式调用示例 ==========

    @Nested
    @DisplayName("链式调用 — 实际业务场景")
    class ChainingExample {

        Result<Integer, String> parse(String s) {
            try {
                return Results.ok(Integer.parseInt(s));
            } catch (NumberFormatException e) {
                return Results.err("not a number: " + s);
            }
        }

        Result<Integer, String> validatePositive(int n) {
            return n > 0 ? Results.ok(n) : Results.err("not positive: " + n);
        }

        @Test
        void chainingSuccess() {
            Result<Integer, String> result =
                    ResultKt.map(ResultKt.flatMap(parse("42"), this::validatePositive), v -> v * 2);
            assertEquals(Results.ok(84), result);
        }

        @Test
        void chainingFailsAtParse() {
            Result<Integer, String> result =
                    ResultKt.map(
                            ResultKt.flatMap(parse("abc"), this::validatePositive), v -> v * 2);
            assertEquals(Results.err("not a number: abc"), result);
        }

        @Test
        void chainingFailsAtValidation() {
            Result<Integer, String> result =
                    ResultKt.map(ResultKt.flatMap(parse("-1"), this::validatePositive), v -> v * 2);
            assertEquals(Results.err("not positive: -1"), result);
        }

        @Test
        @DisplayName("对比：Kotlin 链式 vs Java 静态调用 + pattern matching")
        void kotlinStyleVsJavaStyle() {
            // Kotlin: parse("42").flatMap { validatePositive(it) }.map { it * 2 }
            // Java:
            Result<Integer, String> result =
                    ResultKt.map(ResultKt.flatMap(parse("42"), this::validatePositive), v -> v * 2);
            assertEquals(Results.ok(84), result);

            // pattern matching 取值：
            if (result instanceof Success<?> s) {
                assertEquals(84, s.getValue());
            } else {
                fail("Expected Success");
            }
        }
    }

    // ========== data class 相等性 ==========

    @Nested
    @DisplayName("data class 相等性在 Java 中的表现")
    class Equality {

        @Test
        void successEquality() {
            assertEquals(Results.ok(42), Results.ok(42));
        }

        @Test
        void failureEquality() {
            assertEquals(Results.err("err"), Results.err("err"));
        }

        @Test
        void successNotEqualToFailure() {
            assertNotEquals(Results.ok(0), Results.err(0));
        }
    }
}
