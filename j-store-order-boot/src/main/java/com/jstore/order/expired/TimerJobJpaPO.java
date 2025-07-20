package com.jstore.order.expired;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Date;

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

    public TimerJobJpaPO(TimerJob timerJob, String status) {
        this.setTopic(timerJob.getTopic());
        this.setContent(timerJob.getContent());
        this.setExecuteTime(timerJob.getExecuteTime());
        this.setStatus(status);
    }

    public TimerJobJpaPO() {}
}