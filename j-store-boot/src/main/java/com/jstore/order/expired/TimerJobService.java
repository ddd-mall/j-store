package com.jstore.order.expired;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * 超时任务中心的业务门面。
 * <p>
 * 业务方通过此接口提交延迟任务，无需关心 slot 计算、Redis 写入等内部细节。
 * <p>
 * 使用示例：
 * <pre>
 * &#64;Autowired
 * private TimerJobService timerJobService;
 *
 * // 提交一个 30 分钟后执行的订单超时任务
 * timerJobService.submit("ORDER_EXPIRE", orderJson, Duration.ofMinutes(30));
 *
 * // 提交一个指定时间执行的任务
 * timerJobService.submitAt("COUPON_EXPIRE", couponJson, expireTime);
 * </pre>
 * <p>
 * 注意：对应 topic 的 {@link TimerJobHandler} 实现必须保证幂等性（至少一次语义）。
 */
@Slf4j
@Service
public class TimerJobService {

    private final TimerJobRepository timerJobRepository;
    private final JobLoader jobLoader;

    public TimerJobService(TimerJobRepository timerJobRepository, JobLoader jobLoader) {
        this.timerJobRepository = timerJobRepository;
        this.jobLoader = jobLoader;
    }

    /**
     * 提交一个延迟任务，在指定的延迟时间后执行。
     *
     * @param topic   任务主题，对应 {@link TimerJobHandler#topic()}
     * @param content 业务载荷（JSON 字符串）
     * @param delay   延迟时间
     * @return 创建的任务
     */
    public TimerJob submit(String topic, String content, Duration delay) {
        return submitAt(topic, content, Date.from(Instant.now().plus(delay)));
    }

    /**
     * 提交一个延迟任务，在指定的时间点执行。
     *
     * @param topic       任务主题，对应 {@link TimerJobHandler#topic()}
     * @param content     业务载荷（JSON 字符串）
     * @param executeTime 期望执行时间，必须是未来时间
     * @return 创建的任务
     */
    public TimerJob submitAt(String topic, String content, Date executeTime) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic 不能为空");
        }
        if (executeTime == null) {
            throw new IllegalArgumentException("executeTime 不能为空");
        }

        TimerJob timerJob = new TimerJob()
                .setTopic(topic)
                .setContent(content)
                .setExecuteTime(executeTime);

        long slot = jobLoader.slot(timerJob);
        TimerJob created = timerJobRepository.addNewJobAndEnqueue(timerJob, slot);

        log.debug("任务已提交: id={}, topic={}, executeTime={}, slot={}", created.getId(), topic, executeTime, slot);
        return created;
    }
}
