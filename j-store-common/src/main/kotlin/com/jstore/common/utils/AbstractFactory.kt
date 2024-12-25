package com.jstore.common.utils

import com.jstore.common.errors.CommonErrors
import com.jstore.common.logging.LoggerFactory
import java.lang.reflect.Constructor
import kotlin.reflect.KClass

abstract class AbstractFactory<T: Any>(private val candidateClass: Collection<Class<out T>>) {
    private val log = LoggerFactory.getLogger(this::class)
    @Volatile private var constructor: Constructor<out T>? = null
    @Volatile private var lock = Any()
    private val cache: MutableMap<String, MutableList<Constructor<out T>>> = HashMap()

    private fun getCandidateConstructors(vararg args: Any): MutableList<Constructor<out T>>? {
        val key = args.map { it::class.java.name }.toString()
        return cache[key]
    }

    private fun <I: T> election(clazz: Class<I>, vararg args: Any): T {
        synchronized(lock) {
            if (null != constructor && constructor!!.declaringClass == clazz) {
                try {
                    return constructor!!.newInstance(*args)
                } catch (e: Exception) {
                    constructor = null
                }
            }
            for (candidateClazz in candidateClass) {
                if (null == constructor && candidateClazz == clazz) {
                    try {
                        val argsType = args.map { it::class.java }.toTypedArray()

                        log.info("{}", argsType.map { it.name }.toString())
                        val candidateConstructor = candidateClazz.getDeclaredConstructor(*argsType)
                        candidateConstructor.isAccessible = true
                        val newInstance = candidateConstructor.newInstance(*args)
                        constructor = candidateConstructor
                        log.info("candidate supplier through election $candidateClazz")
                        return newInstance
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
            throw IllegalStateException("No implementation were found")
        }
    }

    fun <I: T> newInstance(clazz: Class<I>, vararg args: Any): T {
        if (null == constructor || clazz != constructor!!.declaringClass) {
            return election(clazz, *args)
        }
        return try {
            constructor!!.newInstance(*args)
        } catch (e: Exception) {
            try {
                election(clazz, args)
            } catch (e: Exception) {
                log.error("Error creating instance.  Cause: $e", e)
                throw CommonErrors.INTERNAL_ERROR.withMsg("Error creating instance.  Cause: $e")
            }
        }
    }

    fun <I: T> newInstance(clazz: KClass<I>, vararg args: Any): T {
        return newInstance(clazz.java, *args)
    }


}
