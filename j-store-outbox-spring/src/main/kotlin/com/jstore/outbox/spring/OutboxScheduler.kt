/*
 * SPDX-FileCopyrightText: 2024-2026 潘少峰 (Peter Pan)
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jstore.outbox.spring

import com.jstore.outbox.*
import org.springframework.scheduling.annotation.Scheduled

/**
 * Outbox 调度器，负责定时触发轮询投递和清理任务。
 *
 * 将调度关注点从 OutboxPublisher/OutboxCleaner 中分离， 使业务逻辑类保持纯粹、易于单元测试。
 */
class OutboxScheduler(
    private val relayTrigger: OutboxRelayTrigger,
    private val outboxCleaner: OutboxCleaner,
) {

    @Scheduled(fixedDelayString = $$"${jstore.outbox.polling-interval:5000}")
    fun schedulePollAndPublish() {
        relayTrigger.requestDrain()
    }

    @Scheduled(cron = $$"${jstore.outbox.cleanup-cron:0 0 3 * * ?}")
    fun scheduleCleanup() {
        outboxCleaner.cleanup()
    }
}
