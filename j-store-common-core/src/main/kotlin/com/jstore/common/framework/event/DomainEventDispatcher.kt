package com.jstore.common.framework.event

/**
 * 领域事件分发器接口
 * 
 * 职责：
 * - 接收一个领域事件和一组监听器
 * - 根据事件类型匹配，将事件分发给支持的监听器
 * - 处理事件分发的策略（同步、异步等）
 */
interface DomainEventDispatcher {
    /**
     * 分发领域事件给所有支持的监听器
     * 
     * @param domainEvent 待分发的领域事件
     * @param listeners 所有注册的监听器
     */
    fun dispatch(domainEvent: DomainEvent, listeners: Iterable<DomainEventListener<*>>)
}

/**
 * 同步事件分发器实现
 * 
 * 分发逻辑：
 * 1. 遍历所有已注册的监听器
 * 2. 使用工具类检查监听器是否支持该事件类型
 * 3. 对支持的监听器调用 onDomainEvent()
 * 4. 事件处理完全同步，不支持异步
 */
class SyncDomainEventDispatcher : DomainEventDispatcher {
    override fun dispatch(domainEvent: DomainEvent, listeners: Iterable<DomainEventListener<*>>) {
        listeners.forEach { listener ->
            if (DomainEventListenerUtils.supportsEvent(listener, domainEvent)) {
                @Suppress("UNCHECKED_CAST")
                (listener as DomainEventListener<DomainEvent>).onDomainEvent(domainEvent)
            }
        }
    }
}
