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
package com.jstore.order.expired;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Worker {

    private final TimerJobRepository jobRepository;
    private final Map<String, TimerJobHandler> handlers;

    public Worker(TimerJobRepository jobRepository, Map<String, TimerJobHandler> handlers) {
        this.jobRepository = jobRepository;
        this.handlers = handlers;
    }

    public void handle(TimerJob job, Integer slot) {
        if (null == job) return;
        // 如果ttl已经耗尽，标记为处理失败
        if (job.getTtl() <= 0) {
            try {
                jobRepository.markAsFailure(job, slot);
                log.warn("任务 {} ttl 耗尽", job.getId());
            } catch (Exception e) {
                log.error("Failed to mark job as failure during shutdown", e);
            }
            return;
        }

        TimerJobHandler timerJobHandler = handlers.get(job.getTopic());
        if (null == timerJobHandler) {
            log.error("未能发现topic: {} 对应的handler", job.getTopic());
            return;
        }
        try {
            boolean handleSuccess = timerJobHandler.handle(job);
            if (handleSuccess) {
                jobRepository.markAsHandled(job, slot);
            } else {
                jobRepository.rollbackOnFailure(job, slot);
            }
        } catch (Exception e) {
            log.error("定时任务处理过程发生异常", e);
            jobRepository.rollbackOnFailure(job, slot);
        }
    }
}
