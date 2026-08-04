package com.jstore.user.domain.useraccount

/** 密码哈希服务接口 定义在领域层，实现在基础设施层（BCrypt） */
interface PasswordHasher {
    /** 将明文密码哈希 */
    fun hash(rawPassword: String): String

    /** 验证明文密码与哈希值是否匹配 */
    fun matches(rawPassword: String, hashedPassword: String): Boolean
}
