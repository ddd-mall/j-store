package com.jstore.user.domain.useraccount

import com.jstore.common.framework.Repository
import com.jstore.common.properties.PhoneNumber

/**
 * 用户账号仓储接口
 * 接口定义在领域层，实现在基础设施层
 */
interface UserAccountRepository : Repository<UserId, UserAccount> {

    /** 添加新用户账号 */
    fun add(userAccount: UserAccount)

    /** 保存已存在的用户账号（更新） */
    override fun save(entity: UserAccount): UserAccount

    /** 根据 ID 查询用户账号 */
    override fun findById(id: UserId): UserAccount?

    /** 根据手机号查询用户账号 */
    fun findByPhoneNumber(phoneNumber: PhoneNumber): UserAccount?

    /** 检查指定 ID 的用户账号是否存在 */
    fun existsById(id: UserId): Boolean

    /** 检查指定手机号的用户账号是否存在 */
    fun existsByPhoneNumber(phoneNumber: PhoneNumber): Boolean
}
