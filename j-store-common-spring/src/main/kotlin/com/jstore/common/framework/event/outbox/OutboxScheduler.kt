package com.jstore.common.framework.event.outbox

import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock

/**
 * Outbox 调度器，负责定时触发轮询投递和清理任务。
 *
 * 将调度关注点从 OutboxPublisher/OutboxCleaner 中分离，
 * 使业务逻辑类保持纯粹、易于单元测试。
 */
class OutboxScheduler(
    private val outboxPublisher: OutboxPublisher,
    private val outboxCleaner: OutboxCleaner,
    private val outboxMonitor: OutboxMonitor = NoopOutboxMonitor,
    private val clock: Clock = Clock.systemUTC(),
    private val schedulerState: SchedulerExecutionState = SchedulerExecutionState(),
) {

    @Scheduled(fixedDelayString = "\${jstore.outbox.polling-interval:5000}")
    fun schedulePollAndPublish() {
        try {
            outboxPublisher.pollAndPublish()
            clock.instant().also {
                schedulerState.recordSuccess(it)
                outboxMonitor.recordSchedulerSuccess(it)
            }
        } catch (failure: RuntimeException) {
            clock.instant().also {
                schedulerState.recordFailure(it)
                outboxMonitor.recordSchedulerFailure(it)
            }
            throw failure
        }
    }

    @Scheduled(cron = "\${jstore.outbox.cleanup-cron:0 0 3 * * ?}")
    fun scheduleCleanup() {
        outboxCleaner.cleanup()
    }
}
