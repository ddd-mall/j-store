package com.jstore.order.expired;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.Date;
import lombok.Data;

@Data
@Entity
@Table(
        name = "timer_job",
        indexes = {@Index(name = "idx_execute_time_status", columnList = "execute_time, status")})
public class TimerJobJpaPO {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "topic", nullable = false)
    public String topic;

    @Column(columnDefinition = "TEXT", nullable = false)
    public String content;

    @Column(name = "status", nullable = false)
    public String status;

    @Column(name = "execute_time", nullable = false)
    public Date executeTime;

    public TimerJobJpaPO(TimerJob timerJob, String status) {
        this.topic = timerJob.getTopic();
        this.content = timerJob.getContent();
        this.executeTime = timerJob.getExecuteTime();
        this.status = status;
    }

    public TimerJobJpaPO() {}
}
