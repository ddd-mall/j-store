package com.jstore.order.expired;

public interface TimerJobHandler {
    String topic();
    boolean handle(TimerJob job);
}
