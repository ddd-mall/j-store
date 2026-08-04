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
