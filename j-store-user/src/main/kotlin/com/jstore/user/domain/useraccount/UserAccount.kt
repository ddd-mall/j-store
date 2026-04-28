package com.jstore.user.domain.useraccount

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.AgreeGate
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.utils.Result
import java.time.LocalDateTime

/**
 * UserAccount 聚合根接口
 * 封装用户账号的生命周期行为：昵称修改、密码修改、状态管理
 * TODO: 补充最后登陆时间,最后登陆地点,最后登陆设备等信息
 */
interface UserAccount : AgreeGate<UserId> {
    override val id: UserId
    val phoneNumber: PhoneNumber
    val nickname: Nickname
    val passwordHash: Password
    val status: UserAccountStatus
    val createTime: LocalDateTime
    val updateTime: LocalDateTime

    /** 修改昵称 */
    fun changeNickname(newNickname: Nickname): Result<Unit, BusinessError>

    /** 修改密码（需传入新的哈希密文） */
    fun changePassword(newPasswordHash: Password): Result<Unit, BusinessError>

    /** 禁用账号 */
    fun disable(): Result<Unit, BusinessError>

    /** 启用账号 */
    fun enable(): Result<Unit, BusinessError>
}
