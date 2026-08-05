package com.jstore.authentication.context

import com.jstore.user.domain.useraccount.UserId
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch

// Feature: authentication-sdk, Property 4: AuthenticatedUserContext 存取 round-trip
@OptIn(ExperimentalKotest::class)
class AuthenticatedUserContextPropertyTest :
    FunSpec({

        // **Validates: Requirements 5.1, 5.3, 5.5**
        test(
            "set → getCurrentUserId → getCurrentUserIdOrNull → clear → getCurrentUserIdOrNull round-trip"
        ) {
            val userIdArb = Arb.long(min = 1L).map { UserId(it) }

            checkAll(PropTestConfig(iterations = 100), userIdArb) { userId ->
                try {
                    AuthenticatedUserContext.set(userId)
                    AuthenticatedUserContext.getCurrentUserId() shouldBe userId
                    AuthenticatedUserContext.getCurrentUserIdOrNull() shouldBe userId
                    AuthenticatedUserContext.clear()
                    AuthenticatedUserContext.getCurrentUserIdOrNull().shouldBeNull()
                } finally {
                    AuthenticatedUserContext.clear()
                }
            }
        }

        // Feature: authentication-sdk, Property 5: AuthenticatedUserContext 线程隔离
        // **Validates: Requirements 5.2**
        test("thread isolation — each thread sees only its own UserId") {
            val userIdPairArb = Arb.long(min = 1L).map { UserId(it) }

            checkAll(PropTestConfig(iterations = 100), userIdPairArb, userIdPairArb) {
                userIdA,
                userIdB ->
                val barrier = CountDownLatch(2)

                val futureA = CompletableFuture.supplyAsync {
                    try {
                        AuthenticatedUserContext.set(userIdA)
                        barrier.countDown()
                        barrier.await()
                        AuthenticatedUserContext.getCurrentUserId()
                    } finally {
                        AuthenticatedUserContext.clear()
                    }
                }

                val futureB = CompletableFuture.supplyAsync {
                    try {
                        AuthenticatedUserContext.set(userIdB)
                        barrier.countDown()
                        barrier.await()
                        AuthenticatedUserContext.getCurrentUserId()
                    } finally {
                        AuthenticatedUserContext.clear()
                    }
                }

                futureA.get() shouldBe userIdA
                futureB.get() shouldBe userIdB
            }
        }
    })
