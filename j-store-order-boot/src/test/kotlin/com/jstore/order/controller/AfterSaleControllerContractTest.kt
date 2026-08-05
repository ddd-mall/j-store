package com.jstore.order.controller

import com.jstore.authentication.annotation.CurrentUserId
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.order.domain.aftersale.*
import com.jstore.order.domain.aftersale.command.AfterSaleApproveCMD
import com.jstore.order.domain.order.OrderId
import com.jstore.order.service.AfterSaleUseCase
import com.jstore.order.service.AfterSaleOrderAccess
import com.jstore.shop.domain.merchant.MerchantId
import com.jstore.shop.domain.merchant.MerchantPermission
import com.jstore.shop.service.MerchantAuthorizationService
import com.jstore.user.domain.useraccount.UserId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

class AfterSaleControllerContractTest {
    private lateinit var service: AfterSaleUseCase
    private lateinit var authorization: MerchantAuthorizationService
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        service = mock(AfterSaleUseCase::class.java)
        authorization = mock(MerchantAuthorizationService::class.java)
        `when`(service.findById(AfterSaleId(9))).thenReturn(Failure(AfterSaleErrors.NOT_FOUND))
        `when`(service.listByOrderForAccess(OrderId(8)))
            .thenReturn(Success(AfterSaleOrderAccess(41, MerchantActorId(70), emptyList())))
        mvc =
            MockMvcBuilders.standaloneSetup(AfterSaleController(service, authorization))
                .setCustomArgumentResolvers(CurrentUserResolver())
                .build()
    }

    @Test
    fun `all six routes use authenticated current user and required idempotency key`() {
        mvc.perform(get("/api/after-sales/9").header("X-Test-User", "41"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(AfterSaleErrors.NOT_FOUND.errorCode))
        verify(service).findById(AfterSaleId(9))
        mvc.perform(get("/api/after-sales").param("orderId", "8").header("X-Test-User", "41"))
            .andExpect(status().isOk)
            .andExpect(content().json("[]"))
        listOf(
                post("/api/after-sales")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"orderId":8,"category":"OTHER","description":"x","items":[{"orderItemId":1,"quantity":1,"amount":100}]}"""
                    ),
                post("/api/after-sales/9/approve"),
                post("/api/after-sales/9/reject")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"rejectionReason":"x"}"""),
                post("/api/after-sales/9/cancel"),
            )
            .forEach { request ->
                mvc.perform(request.header("X-Test-User", "41")).andExpect(status().isBadRequest)
            }
    }

    @Test
    fun `create validates nested positive quantity and amount before service invocation`() {
        mvc.perform(
                post("/api/after-sales")
                    .header("X-Test-User", "41")
                    .header("Idempotency-Key", "key")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"orderId":8,"category":"OTHER","description":"x","items":[{"orderItemId":1,"quantity":0,"amount":0}]}"""
                    )
            )
            .andExpect(status().isBadRequest)
        verifyNoInteractions(service)
    }

    @Test
    fun `reject validates reason and route JSON contract`() {
        mvc.perform(
                post("/api/after-sales/9/reject")
                    .header("X-Test-User", "41")
                    .header("Idempotency-Key", "key")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"rejectionReason":""}""")
            )
            .andExpect(status().isBadRequest)
        verifyNoInteractions(service)
    }

    @Test
    fun `merchant staff approval authorizes membership and uses merchant actor id`() {
        val afterSale = mock(AfterSale::class.java)
        `when`(afterSale.merchantId).thenReturn(MerchantActorId(70))
        `when`(service.findById(AfterSaleId(9))).thenReturn(Success(afterSale))
        `when`(
                authorization.hasPermission(
                    900,
                    MerchantId(70),
                    MerchantPermission.AFTER_SALE_MANAGE,
                )
            )
            .thenReturn(true)
        `when`(service.approve(AfterSaleApproveCMD(AfterSaleId(9), MerchantActorId(70), "key")))
            .thenReturn(Failure(AfterSaleErrors.ILLEGAL_STATE))

        mvc.perform(
                post("/api/after-sales/9/approve")
                    .header("X-Test-User", "900")
                    .header("Idempotency-Key", "key")
            )
            .andExpect(status().isConflict)

        verify(service).approve(AfterSaleApproveCMD(AfterSaleId(9), MerchantActorId(70), "key"))
    }

    private class CurrentUserResolver : HandlerMethodArgumentResolver {
        override fun supportsParameter(parameter: MethodParameter) =
            parameter.hasParameterAnnotation(CurrentUserId::class.java)

        override fun resolveArgument(
            parameter: MethodParameter,
            mavContainer: ModelAndViewContainer?,
            webRequest: NativeWebRequest,
            binderFactory: org.springframework.web.bind.support.WebDataBinderFactory?,
        ) = UserId(webRequest.getHeader("X-Test-User")!!.toLong())
    }
}
