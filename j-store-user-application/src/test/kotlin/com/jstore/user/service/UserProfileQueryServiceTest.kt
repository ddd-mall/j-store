package com.jstore.user.service

import com.jstore.common.properties.PhoneNumber
import com.jstore.user.api.UserProfileStatus
import com.jstore.user.domain.useraccount.Nickname
import com.jstore.user.domain.useraccount.UserAccount
import com.jstore.user.domain.useraccount.UserAccountRepository
import com.jstore.user.domain.useraccount.UserAccountStatus
import com.jstore.user.domain.useraccount.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UserProfileReaderTest {
    private val repository = mock<UserAccountRepository>()
    private val reader = UserProfileReader(repository)

    @Test
    fun `existing account is exposed as a scalar profile contract`() {
        val account = mock<UserAccount>()
        whenever(account.id).thenReturn(UserId(42))
        whenever(account.nickname).thenReturn(Nickname("buyer"))
        whenever(account.phoneNumber).thenReturn(PhoneNumber("+8613800138000"))
        whenever(account.status).thenReturn(UserAccountStatus.ACTIVE)
        whenever(repository.findById(UserId(42))).thenReturn(account)

        val profile = reader.findById(42)!!

        assertEquals(42, profile.userId)
        assertEquals("buyer", profile.nickname)
        assertEquals("+8613800138000", profile.phoneNumber)
        assertEquals(UserProfileStatus.ACTIVE, profile.status)
    }

    @Test
    fun `missing account returns null`() {
        whenever(repository.findById(UserId(404))).thenReturn(null)

        assertNull(reader.findById(404))
    }

    @Test
    fun `disabled account status remains visible to consumers`() {
        val account = mock<UserAccount>()
        whenever(account.id).thenReturn(UserId(7))
        whenever(account.nickname).thenReturn(Nickname("disabled"))
        whenever(account.phoneNumber).thenReturn(PhoneNumber("+8613900139000"))
        whenever(account.status).thenReturn(UserAccountStatus.DISABLED)
        whenever(repository.findById(UserId(7))).thenReturn(account)

        assertEquals(UserProfileStatus.DISABLED, reader.findById(7)?.status)
    }
}
