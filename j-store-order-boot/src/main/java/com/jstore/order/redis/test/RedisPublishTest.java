package com.jstore.order.redis.test;

import com.jstore.common.logging.Logger;
import com.jstore.common.logging.LoggerFactory;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Service
@Configuration
@RestController
@RequestMapping("/redis")
public class RedisPublishTest {
    private final Logger logger = LoggerFactory.INSTANCE.getLogger(this.getClass());
    private final RedisTemplate<Object, Object> redisTemplate;
    private final ThreadPoolTaskExecutor executorService;

    public RedisPublishTest(
            RedisTemplate<Object, Object> redisTemplate,
            @Qualifier("businessExecutor") ThreadPoolTaskExecutor executorService
    ) {
        this.redisTemplate = redisTemplate;
        this.executorService = executorService;
    }



    @GetMapping("/publish/{message}")
    public String publishMessage(@PathVariable String message) {
        logger.info("Publishing message to ch1: {}", message);
        redisTemplate.convertAndSend("ch1", message);
        return "Message published: " + message;
    }

    @Component
    public static class Subscriber implements MessageListener {
        private final Logger logger = LoggerFactory.INSTANCE.getLogger(this.getClass());

        @Override
        public void onMessage(@NotNull Message message, byte[] pattern) {
            String channel = new String(pattern);
            String messageBody = new String(message.getBody());
            logger.info("Received message on channel '{}': {}", channel, messageBody);
        }
    }

    @Bean
    public Subscriber subscriber() {
        return new Subscriber();
    }

    @Bean
    public MessageListenerAdapter messageListenerAdapter(Subscriber subscriber) {
        return new MessageListenerAdapter(subscriber, "onMessage");
    }

    @Bean
    public RedisMessageListenerContainer container(
            RedisConnectionFactory redisConnectionFactory,
            MessageListenerAdapter messageListenerAdapter
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.setTaskExecutor(executorService);
        container.addMessageListener(messageListenerAdapter, new PatternTopic("ch1"));
        logger.info("Redis message listener container initialized for channel 'ch1'");
        return container;
    }
}
