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
        name = "timer_job_dead_queue",
        indexes = {@Index(name = "idx_execute_time_topic", columnList = "execute_time, topic")},
        uniqueConstraints = {@UniqueConstraint(name = "uk_job_id", columnNames = "timer_job_id")})
public class TimerJobDeadQueueJpaPO {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "timer_job_id", nullable = false)
    public Long jobId;

    @Column(name = "topic", columnDefinition = "varchar(255)", nullable = false)
    public String topic;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    public String content;

    @Column(name = "status", columnDefinition = "varchar(255)", nullable = false)
    public String status;

    @Column(name = "remind_ttl", columnDefinition = "smallint", nullable = false)
    public Integer remindTtl;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "execute_time", nullable = false)
    public Date executeTime;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "dead_time", columnDefinition = "timestamp", nullable = false)
    public Date deadTime;

    public TimerJobDeadQueueJpaPO(TimerJob timerJob) {
        this.jobId = timerJob.getId();
        this.topic = timerJob.getTopic();
        this.content = timerJob.getContent();
        this.status = TimerJob.TimerJobStatus.FAILED.name();
        this.remindTtl = timerJob.ttl;
        this.executeTime = timerJob.getExecuteTime();
        this.deadTime = new Date();
    }

    public TimerJobDeadQueueJpaPO() {}
}
