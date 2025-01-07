package com.jstore.common.utils

import com.jstore.common.errors.CommonErrors
import com.jstore.common.logging.LoggerFactory
import java.lang.reflect.Constructor

abstract class AbstractFactory<T : Any>(private val candidateClass: Collection<Class<out T>>) {
    private val log = LoggerFactory.getLogger(this::class)
    @Volatile private var lock = Any()
    private val candidateMap: MutableMap<String, Constructor<out T>> = HashMap()


    private fun getCandidateMapKey(args: Array<out Any>): String {
        return args.map { it::class.java.name }.toString()
    }

    private fun election(args: Array<out Any>): T {
        synchronized(lock) {
            var constructor: Constructor<out T>? = candidateMap[getCandidateMapKey(args)]
            constructor?.let {
                try {
                    return it.newInstance(*args)
                } catch (e: Exception) {
                    constructor = null
                }
            }
            for (candidateClazz in candidateClass) {
                try {
                    val candidateConstructor =
                        candidateClazz.getDeclaredConstructor(*args.map { it::class.java }.toTypedArray())
                    candidateConstructor.isAccessible = true
                    val newInstance = candidateConstructor.newInstance(*args)
                    candidateMap[getCandidateMapKey(args)] = candidateConstructor
                    log.info("candidate through election: $candidateClazz")
                    return newInstance
                } catch (e: Exception) {
                    // ignore
                }
            }
            throw IllegalStateException("No implementation were found")
        }
    }

    protected fun newInstance(vararg args: Any): T {
        candidateMap[getCandidateMapKey(args)]?.let {
            try {
                return it.newInstance(args)
            } catch (e: Exception) {
                // ignore
            }
        }
        return try {
            election(args)
        } catch (e: Exception) {
            log.error("Error creating instance. Cause: $e", e)
            throw CommonErrors.INTERNAL_ERROR.to("Error creating instance.  Cause: $e", e)
        }
    }
}