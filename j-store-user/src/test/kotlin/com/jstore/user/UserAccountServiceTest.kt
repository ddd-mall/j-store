package com.jstore.user

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.user.domain.useraccount.*
import com.jstore.user.domain.useraccount.command.UserRegisterCMD
import com.jstore.user.domain.useraccount.event.UserAccountForcedOfflineEvent
import com.jstore.user.domain.useraccount.event.UserAccountLoggedInEvent
import com.jstore.user.domain.useraccount.event.UserAccountRegisteredEvent
import com.jstore.user.service.UserAccountService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.mockito.kotlin.*
import java.util.*

class UserAccountServiceTest : FunSpec({

    // --- Shared mocks ---
    lateinit var factory: UserAccountFactory
    lateinit var repository: UserAccountRepository
    lateinit var passwordHasher: PasswordHasher
    lateinit var tokenProvider: TokenProvider
    lateinit var tokenStore: TokenStore
    lateinit var eventPublisher: DomainEventPublisher
    lateinit var service: UserAccountService

    beforeEach {
        factory = mock()
        repository = mock()
        passwordHasher = mock()
        tokenProvider = mock()
        tokenStore = mock()
        eventPublisher = mock()
        service = UserAccountService(factory, repository, passwordHasher, tokenProvider, tokenStore, eventPublisher)
    }

    val phone = PhoneNumber("13800000001")
    val userId = UserId(1L)

    fun activeAccount(
        id: UserId = userId,
        phoneNumber: PhoneNumber = phone,
        nickname: Nickname = Nickname("testuser"),
        passwordHash: Password = Password("hashed_pw"),
        status: UserAccountStatus = UserAccountStatus.ACTIVE,
    ): UserAccountImpl = UserAccountImpl(
        id = id,
        phoneNumber = phoneNumber,
        nickname = nickname,
        passwordHash = passwordHash,
        status = status,
    )

    // ==================== Register ====================

    test("register - success") {
        val cmd = UserRegisterCMD(phone, "nick", "Pass1234")
        val account = activeAccount()
        account.publishEvent(UserAccountRegisteredEvent(source = account, userId = userId, phoneNumber = phone))

        whenever(repository.existsByPhoneNumber(phone)).thenReturn(false)
        whenever(factory.create(eq(cmd), eq(passwordHasher))).thenReturn(Success(account))

        val result = service.register(cmd)

        result.shouldBeInstanceOf<Success<UserAccount>>()
        result.value.id shouldBe userId
        verify(repository).add(account)
        verify(eventPublisher, atLeastOnce()).publishEvent(any<DomainEvent>())
    }

    test("register - phone duplicate rejection") {
        val cmd = UserRegisterCMD(phone, "nick", "Pass1234")
        whenever(repository.existsByPhoneNumber(phone)).thenReturn(true)

        val result = service.register(cmd)

        result.shouldBeInstanceOf<Failure<BusinessError>>()
        result.error shouldBe UserAccountErrors.PHONE_ALREADY_REGISTERED
        verify(repository, never()).add(any())
    }

    // ==================== Login ====================

    test("login - success") {
        val account = activeAccount()
        whenever(repository.findByPhoneNumber(phone)).thenReturn(account)
        whenever(passwordHasher.matches("rawPw", "hashed_pw")).thenReturn(true)
        whenever(tokenProvider.issueAccessToken(userId)).thenReturn("access_token")
        whenever(tokenProvider.issueRefreshToken(userId)).thenReturn("refresh_token")

        val result = service.login(phone, "rawPw")

        result.shouldBeInstanceOf<Success<AuthTokenPair>>()
        result.value.accessToken shouldBe "access_token"
        result.value.refreshToken shouldBe "refresh_token"
        verify(tokenStore).storeRefreshToken(eq(userId), eq("refresh_token"), eq(604800L))
        verify(eventPublisher).publishEvent(any<UserAccountLoggedInEvent>())
    }

    test("login - user not found") {
        whenever(repository.findByPhoneNumber(phone)).thenReturn(null)

        val result = service.login(phone, "rawPw")

        result.shouldBeInstanceOf<Failure<BusinessError>>()
        result.error shouldBe UserAccountErrors.USER_NOT_FOUND
    }

    test("login - password mismatch") {
        val account = activeAccount()
        whenever(repository.findByPhoneNumber(phone)).thenReturn(account)
        whenever(passwordHasher.matches("wrongPw", "hashed_pw")).thenReturn(false)

        val result = service.login(phone, "wrongPw")

        result.shouldBeInstanceOf<Failure<BusinessError>>()
        result.error shouldBe UserAccountErrors.PASSWORD_MISMATCH
    }

    test("login - account disabled") {
        val account = activeAccount(status = UserAccountStatus.DISABLED)
        whenever(repository.findByPhoneNumber(phone)).thenReturn(account)
        whenever(passwordHasher.matches("rawPw", "hashed_pw")).thenReturn(true)

        val result = service.login(phone, "rawPw")

        result.shouldBeInstanceOf<Failure<BusinessError>>()
        result.error shouldBe UserAccountErrors.ACCOUNT_DISABLED
    }

    // ==================== RefreshToken ====================

    test("refreshToken - success") {
        whenever(tokenProvider.parseRefreshToken("old_refresh")).thenReturn(userId)
        whenever(tokenStore.getRefreshToken(userId)).thenReturn("old_refresh")
        whenever(repository.findById(userId)).thenReturn(activeAccount())
        whenever(tokenProvider.issueAccessToken(userId)).thenReturn("new_access")
        whenever(tokenProvider.issueRefreshToken(userId)).thenReturn("new_refresh")

        val result = service.refreshToken("old_refresh")

        result.shouldBeInstanceOf<Success<AuthTokenPair>>()
        result.value.accessToken shouldBe "new_access"
        result.value.refreshToken shouldBe "new_refresh"
        verify(tokenStore).storeRefreshToken(eq(userId), eq("new_refresh"), eq(604800L))
    }

    test("refreshToken - token invalid") {
        whenever(tokenProvider.parseRefreshToken("bad_token")).thenReturn(null)

        val result = service.refreshToken("bad_token")

        result.shouldBeInstanceOf<Failure<BusinessError>>()
        result.error shouldBe UserAccountErrors.TOKEN_INVALID
    }

    test("refreshToken - token mismatch removes and returns error") {
        whenever(tokenProvider.parseRefreshToken("stolen_token")).thenReturn(userId)
        whenever(tokenStore.getRefreshToken(userId)).thenReturn("real_token")

        val result = service.refreshToken("stolen_token")

        result.shouldBeInstanceOf<Failure<BusinessError>>()
        result.error shouldBe UserAccountErrors.REFRESH_TOKEN_REVOKED
        verify(tokenStore).removeRefreshToken(userId)
    }

    test("refreshToken - account disabled") {
        val disabledAccount = activeAccount(status = UserAccountStatus.DISABLED)
        whenever(tokenProvider.parseRefreshToken("refresh")).thenReturn(userId)
        whenever(tokenStore.getRefreshToken(userId)).thenReturn("refresh")
        whenever(repository.findById(userId)).thenReturn(disabledAccount)

        val result = service.refreshToken("refresh")

        result.shouldBeInstanceOf<Failure<BusinessError>>()
        result.error shouldBe UserAccountErrors.ACCOUNT_DISABLED
        verify(tokenStore).removeRefreshToken(userId)
    }

    // ==================== ChangePassword ====================

    test("changePassword - success") {
        val account = activeAccount()
        whenever(repository.findById(userId)).thenReturn(account)
        whenever(passwordHasher.matches("oldPw", "hashed_pw")).thenReturn(true)
        whenever(passwordHasher.hash("NewPass12")).thenReturn("hashed_new")

        val result = service.changePassword(userId, "oldPw", "NewPass12")

        result.shouldBeInstanceOf<Success<Unit>>()
        account.passwordHash shouldBe Password("hashed_new")
        verify(repository).save(account)
    }

    test("changePassword - old password wrong") {
        val account = activeAccount()
        whenever(repository.findById(userId)).thenReturn(account)
        whenever(passwordHasher.matches("wrongOld", "hashed_pw")).thenReturn(false)

        val result = service.changePassword(userId, "wrongOld", "NewPass12")

        result.shouldBeInstanceOf<Failure<BusinessError>>()
        result.error shouldBe UserAccountErrors.OLD_PASSWORD_MISMATCH
    }

    test("changePassword - new password weak") {
        val account = activeAccount()
        whenever(repository.findById(userId)).thenReturn(account)
        whenever(passwordHasher.matches("oldPw", "hashed_pw")).thenReturn(true)

        val result = service.changePassword(userId, "oldPw", "weak")

        result.shouldBeInstanceOf<Failure<BusinessError>>()
        result.error shouldBe UserAccountErrors.PASSWORD_STRENGTH_INSUFFICIENT
    }

    // ==================== ForceOffline ====================

    test("forceOffline - blacklists AccessToken and removes RefreshToken") {
        val account = activeAccount()
        whenever(repository.findById(userId)).thenReturn(account)
        whenever(tokenProvider.getAccessTokenJti("access_tok")).thenReturn("jti-123")
        whenever(tokenProvider.getAccessTokenRemainingSeconds("access_tok")).thenReturn(600L)

        val result = service.forceOffline(userId, "access_tok")

        result.shouldBeInstanceOf<Success<Unit>>()
        verify(tokenStore).blacklistAccessToken("jti-123", 600L)
        verify(tokenStore).removeRefreshToken(userId)
        verify(eventPublisher).publishEvent(any<UserAccountForcedOfflineEvent>())
    }

    // ==================== Disable ====================

    test("disable - auto-executes forceOffline after disabling") {
        val account = activeAccount()
        whenever(repository.findById(userId)).thenReturn(account)

        val result = service.disable(userId)

        result.shouldBeInstanceOf<Success<Unit>>()
        account.status shouldBe UserAccountStatus.DISABLED
        verify(repository).save(account)
        verify(tokenStore).removeRefreshToken(userId)
        verify(eventPublisher).publishEvent(any<UserAccountForcedOfflineEvent>())
    }
})
