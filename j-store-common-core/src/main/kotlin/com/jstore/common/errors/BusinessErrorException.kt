package com.jstore.common.errors

/**
 * Explicit adapter for infrastructure boundaries that must turn a business failure into an
 * exception, for example to trigger integration-message redelivery.
 */
class BusinessErrorException(val error: BusinessError) :
    RuntimeException("${error.errorCode}: ${error.message}")
