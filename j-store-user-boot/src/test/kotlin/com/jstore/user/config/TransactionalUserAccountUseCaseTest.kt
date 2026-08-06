package com.jstore.user.config

import com.jstore.common.properties.PhoneNumber
import com.jstore.common.utils.Success
import com.jstore.user.domain.useraccount.AuthTokenClaims
import com.jstore.user.domain.useraccount.AuthTokenPair
import com.jstore.user.domain.useraccount.TokenProvider
import com.jstore.user.domain.useraccount.TokenStore
import com.jstore.user.domain.useraccount.UserId
import com.jstore.user.service.RefreshTokenDigest
import com.jstore.user.service.UserAccountUseCase
import java.time.LocalDateTime
import kotlin.test.Test
import org.mockito.kotlin.*
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus

class TransactionalUserAccountUseCaseTest {
    private val delegate = mock<UserAccountUseCase>()
    private val tokenProvider = mock<TokenProvider>()
    private val tokenStore = mock<TokenStore>()
    private val transactionManager = mock<PlatformTransactionManager>()
    private val transactionStatus = mock<TransactionStatus>()
    private val userId = UserId(42)

    init {
        whenever(transactionManager.getTransaction(any())).thenReturn(transactionStatus)
    }

    @Test
    fun `login stores refresh digest only after database transaction commits`() {
        val phone = PhoneNumber("+8613800138000")
        val tokens = AuthTokenPair("access", LocalDateTime.now(), "refresh", LocalDateTime.now())
        val claims = AuthTokenClaims(userId, "session-1", 5L, "jti")
        whenever(delegate.login(phone, "password")).thenReturn(Success(tokens))
        whenever(tokenProvider.parseRefreshToken("refresh")).thenReturn(claims)

        subject().login(phone, "password")

        inOrder(transactionManager, tokenStore) {
            verify(transactionManager).commit(transactionStatus)
            verify(tokenStore).storeRefreshSession(
                userId,
                "session-1",
                RefreshTokenDigest.sha256("refresh"),
                5L,
                604800L,
            )
        }
    }

    @Test
    fun `password change disable and force offline revoke all sessions after commit`() {
        whenever(delegate.changePassword(userId, "old", "NewPass12")).thenReturn(Success(Unit))
        whenever(delegate.disable(userId)).thenReturn(Success(Unit))
        whenever(delegate.forceOffline(userId)).thenReturn(Success(Unit))

        subject().changePassword(userId, "old", "NewPass12")
        subject().disable(userId)
        subject().forceOffline(userId)

        verify(tokenStore, times(3)).revokeAllSessions(userId)
        verify(transactionManager, times(3)).commit(transactionStatus)
    }

    private fun subject() =
        TransactionalUserAccountUseCase(delegate, tokenProvider, tokenStore, transactionManager)
}
