package com.jstore.common.utils.logging

import com.jstore.common.utils.logging.slf4j.Slf4jSimpleImpl
import java.lang.reflect.Constructor


class LoggerFactory {
    companion object {
        val MARKER: String = "JSTORE"
        private lateinit var logConstructor: Constructor<out Log>
        init {
            useSlf4j()
        }

        fun getLogger(name: String): Log {
            return logConstructor.newInstance(name)
        }

        private fun useSlf4j() {
            setImplementation(Slf4jSimpleImpl::class.java)
        }

        private fun setImplementation(implClass: Class<out Log>) {
            try {

                var con = implClass.getConstructor(String::class.java)
                logConstructor = implClass.getConstructor(String::class.java)
            } catch (e: Exception) {
                throw LogException(e)
            }
        }

    }

}