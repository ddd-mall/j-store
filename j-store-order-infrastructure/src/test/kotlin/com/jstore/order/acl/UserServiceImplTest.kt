package com.jstore.order.acl

import com.jstore.user.api.UserProfileInfo
import com.jstore.user.api.UserProfileQueryService
import com.jstore.user.api.UserProfileStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UserServiceImplTest {
    private val profiles = mock<UserProfileQueryService>()
    private val service = UserServiceImpl(profiles)

    @Test
    fun `active profile is translated into order local user info`() {
        whenever(profiles.findById(42))
            .thenReturn(UserProfileInfo(42, "buyer", "+8613800138000", UserProfileStatus.ACTIVE))

        val info = service.findUserInfo(42)!!

        assertEquals(42, info.uid)
        assertEquals("buyer", info.userName)
        assertEquals("+8613800138000", info.phoneNumber?.value)
    }

    @Test
    fun `disabled profile is not an eligible order buyer`() {
        whenever(profiles.findById(42))
            .thenReturn(UserProfileInfo(42, "buyer", "+8613800138000", UserProfileStatus.DISABLED))

        assertNull(service.findUserInfo(42))
    }
}
