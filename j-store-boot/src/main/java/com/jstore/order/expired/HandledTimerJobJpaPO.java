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

import jakarta.persistence.*;
import java.util.Date;
import lombok.Data;

@Data
@Entity
@Table(
        name = "handled_timer_job",
        indexes = {@Index(name = "idx_execute_time_topic", columnList = "execute_time, topic")},
        uniqueConstraints = {@UniqueConstraint(name = "uk_job_id", columnNames = "timer_job_id")})
public class HandledTimerJobJpaPO {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "timer_job_id", nullable = false)
    public Long jobId;

    @Column(name = "topic", nullable = false)
    public String topic;

    @Column(columnDefinition = "TEXT", nullable = false)
    public String content;

    @Column(name = "status", columnDefinition = "varchar(255)", nullable = false)
    public String status;

    @Column(name = "remind_ttl", columnDefinition = "smallint", nullable = false)
    public Integer remindTtl;

    @Column(name = "execute_time", nullable = false)
    public Date executeTime;

    @Column(name = "create_time", columnDefinition = "timestamp", updatable = false)
    public Date createTime;

    public HandledTimerJobJpaPO() {}

    public HandledTimerJobJpaPO(TimerJob timerJob) {
        this.jobId = timerJob.getId();
        this.topic = timerJob.getTopic();
        this.content = timerJob.getContent();
        this.executeTime = timerJob.getExecuteTime();
        this.remindTtl = timerJob.ttl;
        this.status = TimerJob.TimerJobStatus.HANDLED.name();
        this.createTime = new Date();
    }
}
