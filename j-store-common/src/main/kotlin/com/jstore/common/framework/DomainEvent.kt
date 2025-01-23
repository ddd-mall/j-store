package com.jstore.common.framework

import org.springframework.context.ApplicationEvent

abstract class DomainEvent(source: Any) : ApplicationEvent(source)
