package com.jstore.authentication.config

interface AuthenticationConfigurer {

    fun authenticatedPathPatterns(): List<String> = emptyList()

    fun excludedPathPatterns(): List<String> = emptyList()
}
