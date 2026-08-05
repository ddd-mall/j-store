package com.jstore.common.framework.event

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import java.util.concurrent.ConcurrentHashMap

/**
 * 领域事件监听器工具类
 *
 * 提供反射辅助方法，但将反射逻辑与接口定义分离。 这样领域模型接口保持纯净，反射工具由框架层调用。
 *
 * 设计目的：
 * - 保持 DomainEventListener 接口简洁清晰
 * - 在需要时才使用反射提取泛型信息
 * - 便于单元测试和模拟
 */
object DomainEventListenerUtils {
    private val listeningTypeCache = ConcurrentHashMap<Class<*>, Class<out DomainEvent>>()

    /**
     * 获取监听器支持的事件类型（通过泛型参数）
     *
     * @param listener 具体的监听器实例
     * @return 该监听器通过泛型参数声明的事件类型，失败时返回 null
     *
     * 实现原理：
     * 1. 查找监听器类的所有超类型
     * 2. 找到 DomainEventListener 接口的实现
     * 3. 提取其泛型参数（即事件类型）
     */
    fun getListeningEventType(listener: DomainEventListener<*>): Class<*>? {
        return getListeningEventType(listener::class.java)
    }

    fun getListeningEventType(listenerClass: Class<*>): Class<*>? {
        listeningTypeCache[listenerClass]?.let {
            return it
        }
        val listeningType = extractListeningEventType(listenerClass) ?: return null
        listeningTypeCache[listenerClass] = listeningType
        return listeningType
    }

    fun requireListeningEventType(listener: DomainEventListener<*>): Class<out DomainEvent> {
        return requireListeningEventType(listener::class.java)
    }

    fun requireListeningEventType(listenerClass: Class<*>): Class<out DomainEvent> {
        @Suppress("UNCHECKED_CAST")
        return getListeningEventType(listenerClass) as? Class<out DomainEvent>
            ?: throw IllegalArgumentException(
                "Unable to resolve DomainEventListener event type: listenerClass=${listenerClass.name}. " +
                    "Declare the listener with a concrete DomainEvent generic type, e.g. DomainEventListener<OrderPaidEvent>."
            )
    }

    private fun extractListeningEventType(listenerClass: Class<*>): Class<out DomainEvent>? {
        val eventType = findDomainEventListenerArgument(listenerClass, emptyMap()) ?: return null
        val eventClass = resolveEventClass(eventType) ?: return null
        if (!DomainEvent::class.java.isAssignableFrom(eventClass)) return null

        @Suppress("UNCHECKED_CAST")
        return eventClass as Class<out DomainEvent>
    }

    private fun findDomainEventListenerArgument(
        type: Type,
        bindings: Map<TypeVariable<*>, Type>,
    ): Type? {
        return when (type) {
            is Class<*> -> findInClassHierarchy(type, bindings)
            is ParameterizedType -> {
                val rawClass = type.rawType as? Class<*> ?: return null
                val nextBindings =
                    bindings +
                        rawClass.typeParameters.zip(type.actualTypeArguments).associate {
                            (variable, actualType) ->
                            variable to substituteTypeVariables(actualType, bindings)
                        }

                if (rawClass == DomainEventListener::class.java) {
                    substituteTypeVariables(type.actualTypeArguments.first(), nextBindings)
                } else {
                    findInClassHierarchy(rawClass, nextBindings)
                }
            }
            else -> null
        }
    }

    private fun findInClassHierarchy(clazz: Class<*>, bindings: Map<TypeVariable<*>, Type>): Type? {
        clazz.genericInterfaces
            .asSequence()
            .mapNotNull { findDomainEventListenerArgument(it, bindings) }
            .firstOrNull()
            ?.let {
                return it
            }

        val genericSuperclass = clazz.genericSuperclass ?: return null
        return findDomainEventListenerArgument(genericSuperclass, bindings)
    }

    private fun substituteTypeVariables(type: Type, bindings: Map<TypeVariable<*>, Type>): Type {
        return if (type is TypeVariable<*>) {
            bindings[type]?.let { substituteTypeVariables(it, bindings) } ?: type
        } else {
            type
        }
    }

    private fun resolveEventClass(type: Type): Class<*>? {
        return when (type) {
            is Class<*> -> type
            is ParameterizedType -> type.rawType as? Class<*>
            is WildcardType ->
                type.upperBounds.asSequence().mapNotNull(::resolveEventClass).firstOrNull()
            is TypeVariable<*> -> null
            else -> null
        }
    }

    /**
     * 判断监听器是否支持处理给定的事件
     *
     * @param listener 具体的监听器实例
     * @param event 领域事件
     * @return true 表示监听器可以处理该事件
     *
     * 判断规则：
     * 1. 通过 getListeningEventType() 获取监听器声明的事件类型
     * 2. 检查给定事件是否该类型的实例或子类型
     */
    fun supportsEvent(listener: DomainEventListener<*>, event: DomainEvent): Boolean {
        val listeningType = getListeningEventType(listener) ?: return false
        return listeningType.isAssignableFrom(event::class.java)
    }
}
