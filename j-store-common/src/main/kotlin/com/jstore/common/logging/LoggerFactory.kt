package com.jstore.common.logging

import com.jstore.common.logging.slf4j.Slf4jSimpleImpl
import java.lang.reflect.Constructor
import kotlin.reflect.KClass


class LoggerFactory private constructor() {
    companion object {
        const val MARKER: String = "JSTORE"
        private var loggerConstructor: Constructor<out Logger>? = null
        init {
            tryImplementation(Companion::useSlf4jLogging)
        }

        fun <T> getLogger(clazz: Class<T>): Logger {
            return getLogger(clazz.name)
        }

        fun getLogger(clazz: KClass<*>): Logger {
            return getLogger(clazz.java)
        }

        fun getLogger(name: String): Logger {
            if (null == loggerConstructor) {
                throw LogException("Error creating logger for logger $name.  Cause: can not find Logger implementation")
            }
            try {
                return loggerConstructor!!.newInstance(name)
            } catch (t: Throwable) {
                throw LogException("Error creating logger for logger $name.  Cause: $t", t)
            }
        }

        private fun useSlf4jLogging() {
            setImplementation(Slf4jSimpleImpl::class.java)
        }

        private fun tryImplementation(r: Runnable) {
            if (null == loggerConstructor) {
                try {
                    r.run()
                } catch (t: Throwable) {
                    // ignore
                }
            }
        }

        private fun setImplementation(implClass: Class<out Logger>) {
            try {

                val con = implClass.getConstructor(String::class.java)
                val clazzName = LoggerFactory::class.java.name

                val log = con.newInstance(clazzName)
                if (log.isDebugEnabled()) {
                    log.debug("Logging initialized using '$implClass' adapter.")
                }
                loggerConstructor = con
            } catch (e: Throwable) {
                throw LogException("Error setting Log implementation.  Cause: $e", e)
            }
        }

    }

}