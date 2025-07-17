package com.jstore.order.expired;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Date;

@Data
@Entity
@Table(name = "timer_job")
public class TimerJobPO {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String topic;

    public String content;

    public String status;

    public Date executeTime;

    public TimerJobPO(TimerJob timerJob, String status) {
        this.id = timerJob.getId();
        this.topic = timerJob.getTopic();
        this.content = timerJob.getContent();
        this.executeTime = timerJob.getExecuteTime();
        this.status = status;
    }

    public TimerJobPO() {}
}