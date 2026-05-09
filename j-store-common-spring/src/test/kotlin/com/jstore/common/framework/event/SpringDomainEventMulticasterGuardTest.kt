package com.jstore.common.framework.event

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import org.springframework.context.event.SimpleApplicationEventMulticaster
import org.springframework.context.support.AbstractApplicationContext
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.task.SyncTaskExecutor
import java.util.function.Supplier

class SpringDomainEventMulticasterGuardTest : FunSpec({

    test("async multicaster guard only warns by default") {
        GenericApplicationContext().use { context ->
            context.registerBean(
                AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME,
                SimpleApplicationEventMulticaster::class.java,
                Supplier {
                    SimpleApplicationEventMulticaster().apply {
                        setTaskExecutor(SyncTaskExecutor())
                    }
                },
            )
            context.refresh()

            SpringDomainEventMulticasterGuard(context).afterSingletonsInstantiated()
        }
    }

    test("async multicaster guard fails fast when configured") {
        GenericApplicationContext().use { context ->
            context.registerBean(
                AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME,
                SimpleApplicationEventMulticaster::class.java,
                Supplier {
                    SimpleApplicationEventMulticaster().apply {
                        setTaskExecutor(SyncTaskExecutor())
                    }
                },
            )
            context.refresh()

            shouldThrow<IllegalStateException> {
                SpringDomainEventMulticasterGuard(context, failFast = true).afterSingletonsInstantiated()
            }
        }
    }
})
