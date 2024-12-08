package com.jstore.common.persistent.jpa.hibernate

import org.hibernate.annotations.IdGeneratorType

@IdGeneratorType(value = HibernateDefaultIdentifierGenerator::class)
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class SnowFlakeId
