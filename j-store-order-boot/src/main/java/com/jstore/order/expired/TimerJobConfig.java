package com.jstore.order.expired;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;


@Data
@Configuration
@ConfigurationProperties(prefix = "timer.job")
public class TimerJobConfig {
    /**
     * 槽位数量
     */
    private Integer slotAmount = 8;
    /**
     * worker的数量（一个worker一个线程）
     */
    private Integer workersAmount = 2;
    /**
     * 任务的time to live，也就是能重试的次数（包括第一次执行）
     */
    private Integer initialTtl = 5;

    @Bean(name = "moveToPrepare")
    public RedisScript<List> moveToPrepare() {
        DefaultRedisScript<List> redisScript = new DefaultRedisScript<>();
        redisScript.setLocation(new ClassPathResource("/script/MoveToPrepare.lua"));
        redisScript.setResultType(List.class);
        return redisScript;
    }


    @Bean(name = "rollback")
    public RedisScript<Boolean> rollback() {
        DefaultRedisScript<Boolean> redisScript = new DefaultRedisScript<>();
        redisScript.setLocation(new ClassPathResource("/script/Rollback.lua"));
        redisScript.setResultType(Boolean.class);
        return redisScript;
    }


    @Bean(name = "rollbackExpired")
    public RedisScript<Boolean> rollbackExpired() {
        DefaultRedisScript<Boolean> redisScript = new DefaultRedisScript<>();
        redisScript.setLocation(new ClassPathResource("/script/RollbackExpired.lua"));
        redisScript.setResultType(Boolean.class);
        return redisScript;
    }

    public static final String EXPIRE_CENTER_POOL = "expireCenterPool";

    @Bean(name = EXPIRE_CENTER_POOL)
    public ThreadPoolTaskExecutor expireCenterPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 最大线程数
        executor.setMaxPoolSize(10);
        // 核心线程数
        executor.setCorePoolSize(3);
        // 线程前缀名
        executor.setThreadNamePrefix(EXPIRE_CENTER_POOL + "-");
        executor.initialize();
        return executor;
    }
}
