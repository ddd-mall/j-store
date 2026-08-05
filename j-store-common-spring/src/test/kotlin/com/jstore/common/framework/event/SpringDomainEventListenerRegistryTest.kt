package com.jstore.common.framework.event

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import com.jstore.common.framework.messaging.MessageConsumptionRepository
import org.springframework.context.support.GenericApplicationContext

class SpringDomainEventListenerRegistryTest :
    FunSpec({
        test("register fails when listener generic event type cannot be resolved") {
            GenericApplicationContext().use { context ->
                val registry =
                    SpringDomainEventListenerRegistry(
                        context,
                        MessageConsumptionRepository { _, _, _, _ -> true },
                    )

                shouldThrow<IllegalArgumentException> {
                    registry.register(UnresolvedGenericListener<DomainEvent>())
                }
            }
        }
    })

private class UnresolvedGenericListener<T : DomainEvent> : DomainEventListener<T> {
    override fun listenerId(): String = "test.unresolved-generic"

    override fun onDomainEvent(event: T) {}
}
