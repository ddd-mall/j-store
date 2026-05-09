package com.jstore.order.expired.handler;


import com.jstore.order.expired.TimerJob;
import com.jstore.order.expired.TimerJobHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TestHandler implements TimerJobHandler {
    public static final String topic = "TEST";
    @Override
    public String topic() {
        return topic;
    }

    @Override
    public boolean handle(TimerJob job) {
        log.info("[timer job test] - 定时任务测试： {}, id: {}", job.getContent(), job.getId());
        return true;
    }
}
