package com.jstore.common.framework.event.outbox

/**
 * Outbox 条目状态
 */
enum class OutboxEntryStatus {
    /** 待投递 */
    PENDING,
    /** 已被 relay 领取，正在投递 */
    IN_PROGRESS,
    /** 已投递 */
    PUBLISHED,
    /** 投递失败，待重试 */
    FAILED,
    /** 死信，超过最大重试次数 */
    DEAD_LETTER
}
