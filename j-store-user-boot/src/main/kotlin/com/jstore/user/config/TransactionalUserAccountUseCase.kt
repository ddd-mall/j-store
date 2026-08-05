package com.jstore.user.config

import com.jstore.common.properties.PhoneNumber
import com.jstore.common.utils.Success
import com.jstore.user.domain.useraccount.Nickname
import com.jstore.user.domain.useraccount.TokenProvider
import com.jstore.user.domain.useraccount.TokenStore
import com.jstore.user.domain.useraccount.UserId
import com.jstore.user.domain.useraccount.command.UserRegisterCMD
import com.jstore.user.service.UserAccountUseCase
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * 用户用例的部署层事务装饰器。
 *
 * 数据库与 Outbox 在事务内完成；Redis token 变更只在事务成功提交后执行。
 */
class TransactionalUserAccountUseCase(
    private val delegate: UserAccountUseCase,
    private val tokenProvider: TokenProvider,
    private val tokenStore: TokenStore,
    transactionManager: PlatformTransactionManager,
) : UserAccountUseCase {
    private val write = TransactionTemplate(transactionManager)
    private val read = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    override fun register(cmd: UserRegisterCMD) = tx { delegate.register(cmd) }

    override fun login(phoneNumber: PhoneNumber, rawPassword: String) =
        tx { delegate.login(phoneNumber, rawPassword) }
            .also { result ->
                if (result is Success) {
                    tokenProvider.parseRefreshToken(result.value.refreshToken)?.let { userId ->
                        tokenStore.storeRefreshToken(
                            userId,
                            result.value.refreshToken,
                            REFRESH_TOKEN_TTL_SECONDS,
                        )
                    }
                }
            }

    // 刷新令牌是 Redis 主导的外部状态交换，不伪装成数据库原子事务。
    override fun refreshToken(refreshToken: String) = delegate.refreshToken(refreshToken)

    override fun findById(userId: UserId) = query { delegate.findById(userId) }

    override fun changeNickname(userId: UserId, newNickname: Nickname) = tx {
        delegate.changeNickname(userId, newNickname)
    }

    override fun changePassword(userId: UserId, oldPassword: String, newPassword: String) = tx {
        delegate.changePassword(userId, oldPassword, newPassword)
    }

    override fun disable(userId: UserId) =
        tx { delegate.disable(userId) }
            .also { result ->
                if (result is Success) tokenStore.removeRefreshToken(userId)
            }

    override fun enable(userId: UserId) = tx { delegate.enable(userId) }

    override fun forceOffline(userId: UserId, accessToken: String?) =
        tx { delegate.forceOffline(userId, accessToken) }
            .also { result ->
                if (result is Success) {
                    blacklist(accessToken)
                    tokenStore.removeRefreshToken(userId)
                }
            }

    private fun blacklist(accessToken: String?) {
        if (accessToken == null) return
        val jti = tokenProvider.getAccessTokenJti(accessToken) ?: return
        val remainingSeconds = tokenProvider.getAccessTokenRemainingSeconds(accessToken)
        if (remainingSeconds > 0) tokenStore.blacklistAccessToken(jti, remainingSeconds)
    }

    private fun <T> tx(block: () -> T): T = requireNotNull(write.execute { block() })

    private fun <T> query(block: () -> T): T = requireNotNull(read.execute { block() })

    private companion object {
        const val REFRESH_TOKEN_TTL_SECONDS = 604800L
    }
}
