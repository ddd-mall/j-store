package com.jstore.common.framework.event

import kotlin.reflect.jvm.javaType

/**
 * 领域事件监听器工具类
 * 
 * 提供反射辅助方法，但将反射逻辑与接口定义分离。
 * 这样领域模型接口保持纯净，反射工具由框架层调用。
 * 
 * 设计目的：
 * - 保持 DomainEventListener 接口简洁清晰
 * - 在需要时才使用反射提取泛型信息
 * - 便于单元测试和模拟
 */
object DomainEventListenerUtils {
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
        return try {
            val supertype = listener::class.supertypes
                .find { it.classifier == DomainEventListener::class }
                ?: return null
            
            val eventTypeArgument = supertype.arguments.firstOrNull()
            eventTypeArgument?.type?.javaType as? Class<*>
        } catch (e: Exception) {
            // 反射失败时（例如匿名类），返回 null
            null
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
