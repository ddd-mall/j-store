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

import com.fasterxml.jackson.annotation.JsonFormat;
import java.security.InvalidParameterException;
import java.util.Date;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/timer/job")
public class TimerJobTestController {

    private final TimerJobService timerJobService;
    private final TimerJobCoordinator coordinator;

    public TimerJobTestController(
            TimerJobService timerJobService, TimerJobCoordinator coordinator) {
        this.timerJobService = timerJobService;
        this.coordinator = coordinator;
    }

    @GetMapping("/start")
    public boolean start() {
        coordinator.start();
        return true;
    }

    @GetMapping("/stop")
    public boolean stop() {
        coordinator.stop();
        return true;
    }

    @PostMapping("/add/new")
    public TimerJob addNew(@RequestBody JobCreateParam jobCreateParam) {
        return timerJobService.submitAt(
                jobCreateParam.getTopic(),
                jobCreateParam.getContent(),
                jobCreateParam.getExecuteTime());
    }

    @Data
    public static class JobCreateParam {
        private String topic;
        private String content;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        private Date executeTime;

        public TimerJob toTimerJob() {
            if (null == topic || topic.isEmpty()) {
                throw new InvalidParameterException("topic不能为空");
            }
            if (null == this.executeTime) {
                throw new InvalidParameterException("执行时间不能为空");
            }

            return new TimerJob()
                    .setTopic(this.topic)
                    .setExecuteTime(this.executeTime)
                    .setContent(this.content);
        }
    }
}
