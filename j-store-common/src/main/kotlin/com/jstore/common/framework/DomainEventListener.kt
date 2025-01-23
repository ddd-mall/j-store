package com.jstore.common.framework

import org.springframework.context.ApplicationListener

interface DomainEventListener<T : DomainEvent> : ApplicationListener<T>