package com.jstore.user.service

import java.security.MessageDigest

object RefreshTokenDigest {
    fun sha256(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
