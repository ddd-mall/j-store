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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jstore.common.utils.json.JsonUtils;
import java.util.Date;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/** 定时任务 */
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class TimerJob {
    private Long id;
    private String topic;
    private Date executeTime;
    private String content;

    /** 可重试的次数 */
    @JsonIgnore public int ttl;

    public TimerJob(TimerJobJpaPO po) {
        this.id = po.getId();
        this.topic = po.getTopic();
        this.content = po.getContent();
        this.executeTime = po.getExecuteTime();
    }

    public enum TimerJobStatus {
        UNHANDLED,
        HANDLING,
        HANDLED,
        FAILED
    }

    public static TimerJob fromJsonStr(String timerJobJsonStr) {
        return JsonUtils.INSTANCE.deserialize(timerJobJsonStr, TimerJob.class);
    }

    public static String toJsonStr(TimerJob job) {
        return JsonUtils.INSTANCE.toJsonString(job);
    }
}
