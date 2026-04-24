package com.jstore.order.expired;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jstore.common.utils.json.JsonUtils;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * 定时任务
 */
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class TimerJob {
    private Long id;
    private String topic;
    private Date executeTime;
    private String content;


    /**
     * 可重试的次数
     */
    @JsonIgnore
    public int ttl;


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