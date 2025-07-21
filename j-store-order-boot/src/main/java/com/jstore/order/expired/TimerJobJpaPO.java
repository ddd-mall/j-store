package com.jstore.order.expired;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Date;
import java.util.Optional;

@Data
@Entity
@Table(
        name = "timer_job",
        indexes = {
                @Index(name = "idx_execute_time_status", columnList = "execute_time, status")
        }

)
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

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public TimerJobJpaPO(TimerJob timerJob, String status) {
        this.topic = timerJob.getTopic();
        this.content = timerJob.getContent();
        this.executeTime = timerJob.getExecuteTime();
        this.version = Optional.ofNullable(timerJob.getVersion()).orElse(0L);
        this.status = status;
    }

    public TimerJobJpaPO() {
    }
}