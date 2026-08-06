package com.jstore.user

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.user.domain.useraccount.*
import com.jstore.user.domain.useraccount.command.UserRegisterCMD
import com.jstore.user.domain.useraccount.event.UserAccountLoggedInEvent
import com.jstore.user.service.RefreshTokenDigest
import com.jstore.user.service.UserAccountService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.mockito.kotlin.*

class UserAccountServiceTest :
    FunSpec({
        lateinit var factory: UserAccountFactory
        lateinit var repository: UserAccountRepository
        lateinit var passwordHasher: PasswordHasher
        lateinit var tokenProvider: TokenProvider
        lateinit var tokenStore: TokenStore
        lateinit var verificationGateway: PhoneVerificationGateway
        lateinit var codeSender: PhoneVerificationCodeSender
        lateinit var loginGuard: LoginAttemptGuard
        lateinit var eventPublisher: DomainEventPublisher
        lateinit var service: UserAccountService

        val phone = PhoneNumber("+8613800000001")
        val userId = UserId(1L)

        fun activeAccount(status: UserAccountStatus = UserAccountStatus.ACTIVE) =
            UserAccountImpl(
                id = userId,
                phoneNumber = phone,
                nickname = Nickname("testuser"),
                passwordHash = Password("hashed_pw"),
                status = status,
            )

        beforeEach {
            factory = mock()
            repository = mock()
            passwordHasher = mock()
            tokenProvider = mock()
            tokenStore = mock()
            verificationGateway = mock()
            codeSender = mock()
            loginGuard = mock()
            eventPublisher = mock()
            whenever(loginGuard.isAllowed(any())).thenReturn(true)
            service =
                UserAccountService(
                    factory,
                    repository,
                    passwordHasher,
                    tokenProvider,
                    tokenStore,
                    verificationGateway,
                    codeSender,
                    loginGuard,
                    eventPublisher,
                )
        }

        test("request phone verification sends code but only returns public challenge") {
            val challenge =
                PhoneVerificationChallenge("challenge-1", java.time.Instant.now().plusSeconds(300))
            whenever(verificationGateway.createChallenge(phone))
                .thenReturn(IssuedPhoneVerificationChallenge(challenge, "123456"))

            val result = service.requestPhoneVerification(phone)

            result.shouldBeInstanceOf<Success<PhoneVerificationChallenge>>()
            result.value shouldBe challenge
            verify(codeSender).send(phone, "123456")
        }

        test("register consumes phone-bound proof before creating account") {
            val cmd = UserRegisterCMD(phone, "nick", "Pass1234")
            val proof = PhoneVerificationProof("challenge-1", "123456")
            val account = mock<UserAccount> { on { id } doReturn userId }
            whenever(verificationGateway.consumeChallenge(phone, proof)).thenReturn(true)
            whenever(repository.existsByPhoneNumber(phone)).thenReturn(false)
            whenever(factory.create(cmd, passwordHasher)).thenReturn(Success(account))

            val result = service.register(cmd, proof)

            result.shouldBeInstanceOf<Success<UserAccount>>()
            inOrder(verificationGateway, repository) {
                verify(verificationGateway).consumeChallenge(phone, proof)
                verify(repository).add(account)
            }
        }

        test("invalid phone proof cannot create an account") {
            val cmd = UserRegisterCMD(phone, "nick", "Pass1234")
            val proof = PhoneVerificationProof("challenge-1", "000000")
            whenever(verificationGateway.consumeChallenge(phone, proof)).thenReturn(false)

            val result = service.register(cmd, proof)

            result.shouldBeInstanceOf<Failure<BusinessError>>()
            result.error shouldBe UserAccountErrors.PHONE_VERIFICATION_INVALID
            verify(repository, never()).add(any())
        }

        test("login creates an independent session with current epoch") {
            whenever(repository.findByPhoneNumber(phone)).thenReturn(activeAccount())
            whenever(passwordHasher.matches("rawPw", "hashed_pw")).thenReturn(true)
            whenever(tokenStore.currentSessionEpoch(userId)).thenReturn(7L)
            whenever(tokenProvider.issueAccessToken(eq(userId), any(), eq(7L))).thenReturn("access")
            whenever(tokenProvider.issueRefreshToken(eq(userId), any(), eq(7L)))
                .thenReturn("refresh")

            val result = service.login(phone, "rawPw")

            result.shouldBeInstanceOf<Success<AuthTokenPair>>()
            result.value.accessToken shouldBe "access"
            verify(loginGuard).reset(phone)
            verify(eventPublisher).publishEvent(any<UserAccountLoggedInEvent>())
            val sessionIds = argumentCaptor<String>()
            verify(tokenProvider).issueAccessToken(eq(userId), sessionIds.capture(), eq(7L))
            sessionIds.firstValue.isNotBlank() shouldBe true
        }

        test("unknown phone and wrong password return the same credential error") {
            whenever(repository.findByPhoneNumber(phone)).thenReturn(null)
            val unknown = service.login(phone, "rawPw")

            whenever(repository.findByPhoneNumber(phone)).thenReturn(activeAccount())
            whenever(passwordHasher.matches("rawPw", "hashed_pw")).thenReturn(false)
            val wrong = service.login(phone, "rawPw")

            unknown.shouldBeInstanceOf<Failure<BusinessError>>().error shouldBe
                UserAccountErrors.INVALID_CREDENTIALS
            wrong.shouldBeInstanceOf<Failure<BusinessError>>().error shouldBe
                UserAccountErrors.INVALID_CREDENTIALS
            verify(loginGuard, times(2)).recordFailure(phone)
        }

        test("rate-limited login does not query account data") {
            whenever(loginGuard.isAllowed(phone)).thenReturn(false)

            val result = service.login(phone, "rawPw")

            result.shouldBeInstanceOf<Failure<BusinessError>>().error shouldBe
                UserAccountErrors.LOGIN_RATE_LIMITED
            verify(repository, never()).findByPhoneNumber(any())
        }

        test("refresh rotates the stored digest atomically") {
            val claims = AuthTokenClaims(userId, "session-1", 3L, "refresh-jti")
            whenever(tokenProvider.parseRefreshToken("old-refresh")).thenReturn(claims)
            whenever(repository.findById(userId)).thenReturn(activeAccount())
            whenever(tokenProvider.issueAccessToken(userId, "session-1", 3L))
                .thenReturn("new-access")
            whenever(tokenProvider.issueRefreshToken(userId, "session-1", 3L))
                .thenReturn("new-refresh")
            whenever(
                    tokenStore.rotateRefreshSession(
                        userId,
                        "session-1",
                        RefreshTokenDigest.sha256("old-refresh"),
                        RefreshTokenDigest.sha256("new-refresh"),
                        3L,
                        604800L,
                    )
                )
                .thenReturn(RefreshTokenRotationResult.ROTATED)

            val result = service.refreshToken("old-refresh")

            result.shouldBeInstanceOf<Success<AuthTokenPair>>()
            result.value.refreshToken shouldBe "new-refresh"
        }

        test("refresh replay returns revoked and never exposes replacement tokens") {
            val claims = AuthTokenClaims(userId, "session-1", 3L, "refresh-jti")
            whenever(tokenProvider.parseRefreshToken("replayed")).thenReturn(claims)
            whenever(repository.findById(userId)).thenReturn(activeAccount())
            whenever(tokenProvider.issueAccessToken(any(), any(), any()))
                .thenReturn("unused-access")
            whenever(tokenProvider.issueRefreshToken(any(), any(), any()))
                .thenReturn("unused-refresh")
            whenever(tokenStore.rotateRefreshSession(any(), any(), any(), any(), any(), any()))
                .thenReturn(RefreshTokenRotationResult.REPLAY_DETECTED)

            val result = service.refreshToken("replayed")

            result.shouldBeInstanceOf<Failure<BusinessError>>().error shouldBe
                UserAccountErrors.REFRESH_TOKEN_REVOKED
        }

        test("logout revokes only the authenticated session") {
            val claims = AuthTokenClaims(userId, "session-logout", 3L, "access-jti")
            whenever(tokenProvider.parseAccessToken("access-token")).thenReturn(claims)

            val result = service.logout(userId, "access-token")

            result.shouldBeInstanceOf<Success<Unit>>()
            verify(tokenStore).revokeSession(userId, "session-logout")
            verify(tokenStore, never()).revokeAllSessions(any())
        }
    })
