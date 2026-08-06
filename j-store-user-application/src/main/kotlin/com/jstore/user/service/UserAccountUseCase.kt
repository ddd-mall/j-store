package com.jstore.user.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.utils.Result
import com.jstore.user.domain.useraccount.AuthTokenPair
import com.jstore.user.domain.useraccount.Nickname
import com.jstore.user.domain.useraccount.PhoneVerificationChallenge
import com.jstore.user.domain.useraccount.PhoneVerificationProof
import com.jstore.user.domain.useraccount.UserAccount
import com.jstore.user.domain.useraccount.UserId
import com.jstore.user.domain.useraccount.command.UserRegisterCMD

interface UserAccountUseCase {
    fun requestPhoneVerification(
        phoneNumber: PhoneNumber
    ): Result<PhoneVerificationChallenge, BusinessError>

    fun register(
        cmd: UserRegisterCMD,
        verificationProof: PhoneVerificationProof,
    ): Result<UserAccount, BusinessError>

    fun login(phoneNumber: PhoneNumber, rawPassword: String): Result<AuthTokenPair, BusinessError>

    fun refreshToken(refreshToken: String): Result<AuthTokenPair, BusinessError>

    fun logout(userId: UserId, accessToken: String): Result<Unit, BusinessError>

    fun findById(userId: UserId): Result<UserAccount, BusinessError>

    fun changeNickname(userId: UserId, newNickname: Nickname): Result<Unit, BusinessError>

    fun changePassword(
        userId: UserId,
        oldPassword: String,
        newPassword: String,
    ): Result<Unit, BusinessError>

    fun disable(userId: UserId): Result<Unit, BusinessError>

    fun enable(userId: UserId): Result<Unit, BusinessError>

    fun forceOffline(userId: UserId): Result<Unit, BusinessError>
}
