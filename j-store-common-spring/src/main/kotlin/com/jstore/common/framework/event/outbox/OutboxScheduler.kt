package com.jstore.common.framework.event.outbox

import org.springframework.scheduling.annotation.Scheduled

/**
 * Outbox 调度器，负责定时触发轮询投递和清理任务。
 *
 * 将调度关注点从 OutboxPublisher/OutboxCleaner 中分离，
 * 使业务逻辑类保持纯粹、易于单元测试。
 */
class OutboxScheduler(
    private val outboxPublisher: OutboxPublisher,
    private val outboxCleaner: OutboxCleaner,
) {

    @Scheduled(fixedDelayString = "\${jstore.outbox.polling-interval:5000}")
    fun schedulePollAndPublish() {
        outboxPublisher.pollAndPublish()
    }

    @Scheduled(cron = "\${jstore.outbox.cleanup-cron:0 0 3 * * ?}")
    fun scheduleCleanup() {
        outboxCleaner.cleanup()
    }
}
