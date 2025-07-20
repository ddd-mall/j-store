package com.jstore.order.expired;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Date;

@Data
@Entity
@Table(name = "handled_timer_job")
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
