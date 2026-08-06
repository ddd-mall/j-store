package com.jstore.shop.config

import com.jstore.user.api.UserProfileInfo
import com.jstore.user.api.UserProfileQueryService
import com.jstore.user.api.UserProfileStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MerchantUserAccountLookupConfigurationTest {
    private val profiles = mock<UserProfileQueryService>()
    private val lookup = MerchantBootConfiguration().merchantUserAccountLookup(profiles)

    @Test
    fun `merchant account lookup uses the published user profile contract`() {
        whenever(profiles.findById(42))
            .thenReturn(UserProfileInfo(42, "member", "+8613800138000", UserProfileStatus.ACTIVE))
        whenever(profiles.findById(404)).thenReturn(null)

        assertTrue(lookup.exists(42))
        assertFalse(lookup.exists(404))
    }
}
