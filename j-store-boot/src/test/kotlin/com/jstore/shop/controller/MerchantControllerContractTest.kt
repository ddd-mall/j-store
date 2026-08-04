package com.jstore.shop.controller

import com.jstore.authentication.annotation.CurrentUserId
import com.jstore.shop.domain.merchant.Merchant
import com.jstore.shop.domain.merchant.MerchantId
import com.jstore.shop.domain.merchant.MerchantMembership
import com.jstore.shop.domain.merchant.MerchantMembershipId
import com.jstore.shop.domain.merchant.MerchantMembershipRepository
import com.jstore.shop.domain.merchant.MerchantRepository
import com.jstore.shop.service.MerchantService
import com.jstore.shop.service.UserAccountLookup
import com.jstore.user.domain.useraccount.UserId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

class MerchantControllerContractTest {
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        var sequence = 100L
        val membershipRepository = FakeMembershipRepository()
        val service =
            MerchantService(
                idGenerator = { ++sequence },
                merchantRepository = FakeMerchantRepository(membershipRepository),
                membershipRepository = membershipRepository,
                userAccountLookup = UserAccountLookup { it in setOf(10L, 20L) },
            )
        mvc =
            MockMvcBuilders.standaloneSetup(MerchantController(service))
                .setCustomArgumentResolvers(CurrentUserResolver())
                .build()
    }

    @Test
    fun `current account can create merchant and is returned as owner membership`() {
        mvc.perform(
                post("/api/merchants")
                    .header("X-Test-User", "10")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"示例商户"}""")
            )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(101))
            .andExpect(jsonPath("$.name").value("示例商户"))

        mvc.perform(get("/api/merchants").header("X-Test-User", "10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].merchantId").value(101))
            .andExpect(jsonPath("$[0].roles[0]").value("OWNER"))
    }

    @Test
    fun `owner can add another account as scoped merchant member`() {
        mvc.perform(
                post("/api/merchants")
                    .header("X-Test-User", "10")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"示例商户"}""")
            )
            .andExpect(status().isCreated)

        mvc.perform(
                post("/api/merchants/101/members")
                    .header("X-Test-User", "10")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":20,"roles":["ORDER_MANAGER"]}""")
            )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.userId").value(20))
            .andExpect(jsonPath("$.roles[0]").value("ORDER_MANAGER"))
    }

    private class CurrentUserResolver : HandlerMethodArgumentResolver {
        override fun supportsParameter(parameter: MethodParameter) =
            parameter.hasParameterAnnotation(CurrentUserId::class.java) &&
                parameter.parameterType == UserId::class.java

        override fun resolveArgument(
            parameter: MethodParameter,
            mavContainer: ModelAndViewContainer?,
            webRequest: NativeWebRequest,
            binderFactory: org.springframework.web.bind.support.WebDataBinderFactory?,
        ) = UserId(webRequest.getHeader("X-Test-User")!!.toLong())
    }

    private class FakeMerchantRepository(
        private val membershipRepository: MerchantMembershipRepository
    ) : MerchantRepository {
        private val values = linkedMapOf<MerchantId, Merchant>()

        override fun createWithOwner(
            merchant: Merchant,
            ownerMembership: MerchantMembership,
        ): Merchant {
            values[merchant.id] = merchant
            membershipRepository.save(ownerMembership)
            return merchant
        }

        override fun save(entity: Merchant): Merchant = entity.also { values[it.id] = it }

        override fun findById(id: MerchantId): Merchant? = values[id]
    }

    private class FakeMembershipRepository : MerchantMembershipRepository {
        private val values = linkedMapOf<MerchantMembershipId, MerchantMembership>()

        override fun save(entity: MerchantMembership): MerchantMembership = entity.also {
            values[it.id] = it
        }

        override fun findById(id: MerchantMembershipId): MerchantMembership? = values[id]

        override fun findByMerchantAndUser(merchantId: MerchantId, userId: Long) =
            values.values.firstOrNull { it.merchantId == merchantId && it.userId == userId }

        override fun findByUser(userId: Long) = values.values.filter { it.userId == userId }
    }
}
