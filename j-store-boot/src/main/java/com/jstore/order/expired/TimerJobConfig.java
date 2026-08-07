/*
 * SPDX-FileCopyrightText: 2024-2026 潘少峰 (Peter Pan)
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jstore.order.expired;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Data
@Configuration
@ConfigurationProperties(prefix = "timer.job")
@SuppressWarnings("rawtypes")
public class TimerJobConfig {
    /** 槽位数量 */
    private Integer slotAmount = 8;

    public static final String EXPIRE_CENTER_POOL = "expireCenterPool";
    public static final String JOB_KEY_PREFIX = "timer:job:";

    @Bean(name = "pickOneOutAndPrepare")
    public RedisScript<List> moveToPrepare() {
        DefaultRedisScript<List> redisScript = new DefaultRedisScript<>();
        redisScript.setLocation(new ClassPathResource("/script/PickOneOutAndPrepare.lua"));
        redisScript.setResultType(List.class);
        return redisScript;
    }

    @Bean(name = "rollbackTimerJob")
    public RedisScript<Boolean> rollback() {
        DefaultRedisScript<Boolean> redisScript = new DefaultRedisScript<>();
        redisScript.setLocation(new ClassPathResource("/script/RollbackTimerJob.lua"));
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

    @Bean(name = EXPIRE_CENTER_POOL)
    public ThreadPoolTaskExecutor expireCenterPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 最大线程数
        executor.setMaxPoolSize(20);
        // 核心线程数
        executor.setCorePoolSize(3);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 线程前缀名
        executor.setThreadNamePrefix(EXPIRE_CENTER_POOL + "-");
        executor.initialize();
        return executor;
    }
}
