package com.jstore.user.config

import com.jstore.common.properties.PhoneNumber
import com.jstore.common.utils.Success
import com.jstore.user.domain.useraccount.AuthTokenPair
import com.jstore.user.domain.useraccount.TokenProvider
import com.jstore.user.domain.useraccount.TokenStore
import com.jstore.user.domain.useraccount.UserId
import com.jstore.user.service.UserAccountUseCase
import java.time.LocalDateTime
import kotlin.test.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
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
    fun `login stores refresh token only after database transaction commits`() {
        val phone = PhoneNumber("+8613800138000")
        val tokens =
            AuthTokenPair(
                "access",
                LocalDateTime.now().plusMinutes(15),
                "refresh",
                LocalDateTime.now().plusDays(7),
            )
        whenever(delegate.login(phone, "password")).thenReturn(Success(tokens))
        whenever(tokenProvider.parseRefreshToken("refresh")).thenReturn(userId)
        val useCase = subject()

        useCase.login(phone, "password")

        inOrder(transactionManager, tokenStore) {
            verify(transactionManager).commit(transactionStatus)
            verify(tokenStore).storeRefreshToken(userId, "refresh", 604800L)
        }
    }

    @Test
    fun `disable revokes refresh token only after database transaction commits`() {
        whenever(delegate.disable(userId)).thenReturn(Success(Unit))
        val useCase = subject()

        useCase.disable(userId)

        inOrder(transactionManager, tokenStore) {
            verify(transactionManager).commit(transactionStatus)
            verify(tokenStore).removeRefreshToken(userId)
        }
    }

    private fun subject() =
        TransactionalUserAccountUseCase(
            delegate,
            tokenProvider,
            tokenStore,
            transactionManager,
        )
}
