package com.jstore.order.service

import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.utils.Failure
import com.jstore.order.acl.UserService
import com.jstore.order.domain.order.OrderErrors
import com.jstore.order.domain.order.OrderFactory
import com.jstore.order.domain.order.OrderRepository
import com.jstore.order.domain.order.UserInfo
import com.jstore.order.domain.order.command.OrderCreateCMD
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class OrderUserProfileSnapshotTest {
    private val factory = mock<OrderFactory>()
    private val repository = mock<OrderRepository>()
    private val publisher = mock<DomainEventPublisher>()
    private val users = mock<UserService>()
    private val service = OrderService(factory, repository, publisher, users)

    @Test
    fun `create order passes authoritative buyer profile snapshot to the factory`() {
        val command = validCreateCommand()
        val buyer = UserInfo(42, PhoneNumber("+8613800138000"), "buyer")
        whenever(users.findUserInfo(42)).thenReturn(buyer)
        whenever(factory.create(command, buyer)).thenReturn(Failure(OrderErrors.ITEMS_EMPTY))

        service.createOrder(command)

        verify(factory).create(command, buyer)
    }

    @Test
    fun `missing or inactive user rejects order before factory and persistence`() {
        val command = validCreateCommand()
        whenever(users.findUserInfo(42)).thenReturn(null)

        val result = service.createOrder(command)

        assertEquals(Failure(OrderErrors.BUYER_INVALID), result)
        verifyNoInteractions(factory, repository, publisher)
    }

    private fun validCreateCommand() =
        OrderCreateCMD(
            buyerUid = 42,
            merchantId = 7,
            recipientInfo =
                OrderCreateCMD.RecipientInfoCMD(
                    consigneeName = "recipient",
                    countryCode = "CN",
                    consigneeContractInfo =
                        OrderCreateCMD.ContractInfoCMD(phoneNumber = PhoneNumber("+8613900139000")),
                    shippingDistrictCode = "110000",
                    shippingDetailAddress = "detail",
                ),
            items =
                listOf(
                    OrderCreateCMD.OrderItemCMD(
                        spuId = 1,
                        skuId = 2,
                        quantity = 1,
                        snapshotVersion = 3,
                    )
                ),
        )
}
