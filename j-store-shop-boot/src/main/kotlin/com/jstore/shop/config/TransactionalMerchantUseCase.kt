package com.jstore.shop.config

import com.jstore.shop.domain.merchant.MerchantId
import com.jstore.shop.domain.merchant.MerchantRole
import com.jstore.shop.service.MerchantUseCase
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

class TransactionalMerchantUseCase(
    private val delegate: MerchantUseCase,
    transactionManager: PlatformTransactionManager,
) : MerchantUseCase {
    private val write = TransactionTemplate(transactionManager)
    private val read = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    override fun create(creatorUserId: Long, name: String) = tx {
        delegate.create(creatorUserId, name)
    }

    override fun listForUser(userId: Long) = query { delegate.listForUser(userId) }

    override fun addMember(
        actorUserId: Long,
        merchantId: MerchantId,
        userId: Long,
        roles: Set<MerchantRole>,
    ) = tx { delegate.addMember(actorUserId, merchantId, userId, roles) }

    override fun changeMemberRoles(
        actorUserId: Long,
        merchantId: MerchantId,
        memberUserId: Long,
        roles: Set<MerchantRole>,
    ) = tx { delegate.changeMemberRoles(actorUserId, merchantId, memberUserId, roles) }

    override fun disableMember(actorUserId: Long, merchantId: MerchantId, memberUserId: Long) = tx {
        delegate.disableMember(actorUserId, merchantId, memberUserId)
    }

    private fun <T> tx(block: () -> T): T = requireNotNull(write.execute { block() })

    private fun <T> query(block: () -> T): T = requireNotNull(read.execute { block() })
}
