package com.jstore.common.framework.event

/**
 * 领域事件监听器接口
 * 
 * 设计原则：
 * 1. 纯领域模型，完全脱离框架依赖
 * 2. 泛型约束：T 为该监听器处理的具体事件类型
 * 3. 具体实现类通过泛型参数声明支持的事件类型
 */
interface DomainEventListener<T : DomainEvent> {
    /**
     * 监听器是否支持异步执行
     * @return true 表示可以异步处理该事件，false 表示必须同步处理
     */
    fun supportsAsyncExecution(): Boolean = false

    /**
     * 处理领域事件
     * @param event 具体的领域事件实例
     */
    fun onDomainEvent(event: T)
}