package com.jstore.authentication.context

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.string.shouldNotBeEmpty

// **Validates: Requirements 5.4**
class AuthenticatedUserContextTest :
    FunSpec({
        beforeEach {
            AuthenticatedUserContext.clear()
        }

        afterEach {
            AuthenticatedUserContext.clear()
        }

        test("getCurrentUserId() throws AuthenticationException when no user is set") {
            val exception =
                shouldThrow<AuthenticationException> {
                    AuthenticatedUserContext.getCurrentUserId()
                }
            exception.message.shouldNotBeEmpty()
        }

        test("getCurrentUserIdOrNull() returns null when no user is set") {
            AuthenticatedUserContext.getCurrentUserIdOrNull().shouldBeNull()
        }
    })
