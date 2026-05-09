package com.jstore.common.framework.event.outbox

/**
 * Outbox 序列化/反序列化异常。
 */
class OutboxSerializationException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
