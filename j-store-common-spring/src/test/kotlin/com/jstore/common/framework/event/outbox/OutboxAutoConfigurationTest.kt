package com.jstore.common.framework.event.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventBus
import com.jstore.common.framework.event.DomainEventListener
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.framework.event.outbox.persistence.OutboxEntryPOJpaRepository
import com.jstore.common.persistent.SnowFlakSequence
import jakarta.persistence.EntityManager
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.mockito.kotlin.mock
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OutboxAutoConfiguration 集成测试
 *
 * 验证功能开关（jstore.outbox.enabled）控制 Bean 注册行为。
 *
 * Validates: Requirements 5.2, 5.4, 5.5
 */
class OutboxAutoConfigurationTest : FunSpec({

    val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(OutboxAutoConfiguration::class.java))
        .withUserConfiguration(TestInfrastructureConfig::class.java)

    test("enabled=true registers OutboxEventPublisher as DomainEventPublisher") {
        contextRunner
            .withPropertyValues("jstore.outbox.enabled=true")
            .run { context ->
                context.containsBean("domainEventPublisher") shouldBe true
                context.getBean(DomainEventPublisher::class.java)
                    .shouldBeInstanceOf<OutboxEventPublisher>()
                context.containsBean("outboxPublisher") shouldBe true
                context.containsBean("outboxCleaner") shouldBe true
                context.containsBean("eventSerializer") shouldBe true
                context.containsBean("outboxEntryRepository") shouldBe true
            }
    }

    test("enabled=false does not register OutboxAutoConfiguration beans") {
        contextRunner
            .withPropertyValues("jstore.outbox.enabled=false")
            .run { context ->
                context.containsBean("domainEventPublisher") shouldBe false
                context.containsBean("outboxPublisher") shouldBe false
                context.containsBean("outboxCleaner") shouldBe false
            }
    }

    test("no property configured does not register OutboxAutoConfiguration beans") {
        contextRunner
            .run { context ->
                context.containsBean("domainEventPublisher") shouldBe false
                context.containsBean("outboxPublisher") shouldBe false
                context.containsBean("outboxCleaner") shouldBe false
            }
    }
}) {
    /**
     * 提供 OutboxAutoConfiguration 所需的基础设施 Bean（mock 实现）。
     */
    @Configuration
    class TestInfrastructureConfig {
        @Bean
        fun objectMapper(): ObjectMapper = ObjectMapper()

        @Bean
        fun outboxEntryPOJpaRepository(): OutboxEntryPOJpaRepository = mock()

        @Bean
        fun entityManager(): EntityManager = mock()

        @Bean
        fun snowFlakSequence(): SnowFlakSequence = SnowFlakSequence(1, 1)

        @Bean
        fun domainEventBus(): DomainEventBus = object : DomainEventBus {
            override fun publishEvent(domainEvent: DomainEvent) {}
            override fun register(domainEventListener: DomainEventListener<*>) {}
            override fun unregister(domainEventListener: DomainEventListener<*>) {}
        }
    }
}
