package com.jstore.common.framework.event.outbox

import java.time.Instant

/**
 * Outbox 条目仓储接口。
 *
 * 方法签名仅使用领域对象，不依赖任何框架类型。
 */
interface OutboxEntryRepository {

    fun save(entry: OutboxEntry): OutboxEntry

    fun findPendingAndRetryable(maxRetryCount: Int, batchSize: Int): List<OutboxEntry>

    fun deletePublishedBefore(before: Instant, batchSize: Int): Int
}
