package com.jstore.user.domain.useraccount

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

/**
 * BCrypt 实现的 PasswordHasher
 * 使用 Spring Security Crypto 的 BCryptPasswordEncoder
 */
class BCryptPasswordHasher(
    strength: Int = 10,
) : PasswordHasher {

    private val encoder = BCryptPasswordEncoder(strength)

    override fun hash(rawPassword: String): String {
        return encoder.encode(rawPassword)!!
    }

    override fun matches(rawPassword: String, hashedPassword: String): Boolean {
        return encoder.matches(rawPassword, hashedPassword)
    }
}
