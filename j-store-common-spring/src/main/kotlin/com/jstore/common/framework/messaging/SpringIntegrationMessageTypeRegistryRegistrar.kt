package com.jstore.common.framework.messaging

import com.jstore.common.framework.event.outbox.IntegrationMessageType
import com.jstore.common.framework.event.outbox.IntegrationMessageTypeRegistry
import java.lang.reflect.Modifier
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AnnotationTypeFilter

class SpringIntegrationMessageTypeRegistryRegistrar(
    private val registry: IntegrationMessageTypeRegistry,
    private val scanPackages: List<String>,
) : SmartInitializingSingleton {
    override fun afterSingletonsInstantiated() {
        val scanner =
            object : ClassPathScanningCandidateComponentProvider(false) {
                override fun isCandidateComponent(
                    beanDefinition:
                        org.springframework.beans.factory.annotation.AnnotatedBeanDefinition
                ): Boolean = beanDefinition.metadata.isIndependent
            }
        scanner.addIncludeFilter(AnnotationTypeFilter(IntegrationMessageType::class.java))
        scanPackages
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .flatMap { scanner.findCandidateComponents(it).asSequence() }
            .forEach { candidate ->
                val className = checkNotNull(candidate.beanClassName)
                val clazz = Class.forName(className)
                require(IntegrationMessage::class.java.isAssignableFrom(clazz)) {
                    "@IntegrationMessageType class must implement IntegrationMessage: $className"
                }
                require(!clazz.isInterface && !Modifier.isAbstract(clazz.modifiers)) {
                    "@IntegrationMessageType class must be concrete: $className"
                }
                val annotation = clazz.getAnnotation(IntegrationMessageType::class.java)
                @Suppress("UNCHECKED_CAST")
                registry.register(
                    annotation.name,
                    annotation.version,
                    clazz as Class<out IntegrationMessage>,
                )
            }
    }
}
