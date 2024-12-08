package com.jstore.common.logging

import com.jstore.common.logging.slf4j.Slf4jSimpleImpl
import java.lang.reflect.Constructor


class LoggerFactory private constructor() {
    companion object {
        const val MARKER: String = "JSTORE"
        private var logConstructor: Constructor<out Log>? = null
        init {
            tryImplementation(Companion::useSlf4jLogging)
        }

        fun <T> getLogger(clazz: Class<T>): Log {
            return getLogger(clazz.name)
        }

        fun getLogger(name: String): Log {
            if (null == logConstructor) {
                throw LogException("Error creating logger for logger $name.  Cause: can not find Logger implementation")
            }
            try {
                return logConstructor!!.newInstance(name)
            } catch (t: Throwable) {
                throw LogException("Error creating logger for logger $name.  Cause: $t", t)
            }
        }

        private fun useSlf4jLogging() {
            setImplementation(Slf4jSimpleImpl::class.java)
        }

        private fun tryImplementation(r: Runnable) {
            if (null == logConstructor) {
                try {
                    r.run()
                } catch (t: Throwable) {
                    // ignore
                }
            }
        }

        private fun setImplementation(implClass: Class<out Log>) {
            try {

                val con = implClass.getConstructor(String::class.java)
                val clazzName = LoggerFactory::class.java.name

                val log = con.newInstance(clazzName)
                if (log.isDebugEnabled()) {
                    log.debug("Logging initialized using '$implClass' adapter.")
                }
                logConstructor = con
            } catch (e: Throwable) {
                throw LogException("Error setting Log implementation.  Cause: $e", e)
            }
        }

    }

}