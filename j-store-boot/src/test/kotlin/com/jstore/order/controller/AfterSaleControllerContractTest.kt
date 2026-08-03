package com.jstore.order.controller

import com.jstore.authentication.annotation.CurrentUserId
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.order.domain.aftersale.*
import com.jstore.order.domain.order.FulfillmentStatus
import com.jstore.order.domain.order.OrderId
import com.jstore.order.service.AfterSaleApplicationService
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
    private lateinit var service: AfterSaleApplicationService
    private lateinit var mvc: MockMvc
    @BeforeEach fun setUp() {
        service = mock(AfterSaleApplicationService::class.java)
        `when`(service.get(AfterSaleId(9), 41)).thenReturn(Failure(AfterSaleErrors.NOT_FOUND))
        `when`(service.listByOrder(OrderId(8), 41)).thenReturn(Success(emptyList()))
        mvc = MockMvcBuilders.standaloneSetup(AfterSaleController(service)).setCustomArgumentResolvers(CurrentUserResolver()).build()
    }
    @Test fun `all six routes use authenticated current user and required idempotency key`() {
        mvc.perform(get("/api/after-sales/9").header("X-Test-User", "41")).andExpect(status().isNotFound).andExpect(jsonPath("$.errorCode").value(AfterSaleErrors.NOT_FOUND.errorCode))
        verify(service).get(AfterSaleId(9), 41)
        mvc.perform(get("/api/after-sales").param("orderId", "8").header("X-Test-User", "41")).andExpect(status().isOk).andExpect(content().json("[]"))
        listOf(post("/api/after-sales").contentType(MediaType.APPLICATION_JSON).content("""{"orderId":8,"category":"OTHER","description":"x","items":[{"orderItemId":1,"quantity":1,"amount":100}]}"""), post("/api/after-sales/9/approve"), post("/api/after-sales/9/reject").contentType(MediaType.APPLICATION_JSON).content("""{"rejectionReason":"x"}"""), post("/api/after-sales/9/cancel")).forEach { request ->
            mvc.perform(request.header("X-Test-User", "41")).andExpect(status().isBadRequest)
        }
    }
    @Test fun `create validates nested positive quantity and amount before service invocation`() {
        mvc.perform(post("/api/after-sales").header("X-Test-User", "41").header("Idempotency-Key", "key").contentType(MediaType.APPLICATION_JSON).content("""{"orderId":8,"category":"OTHER","description":"x","items":[{"orderItemId":1,"quantity":0,"amount":0}]}""")).andExpect(status().isBadRequest)
        verifyNoInteractions(service)
    }
    @Test fun `reject validates reason and route JSON contract`() {
        mvc.perform(post("/api/after-sales/9/reject").header("X-Test-User", "41").header("Idempotency-Key", "key").contentType(MediaType.APPLICATION_JSON).content("""{"rejectionReason":""}""")).andExpect(status().isBadRequest)
        verifyNoInteractions(service)
    }
    private class CurrentUserResolver : HandlerMethodArgumentResolver {
        override fun supportsParameter(parameter: MethodParameter) = parameter.hasParameterAnnotation(CurrentUserId::class.java)
        override fun resolveArgument(parameter: MethodParameter, mavContainer: ModelAndViewContainer?, webRequest: NativeWebRequest, binderFactory: org.springframework.web.bind.support.WebDataBinderFactory?) = webRequest.getHeader("X-Test-User")!!.toLong()
    }
}
