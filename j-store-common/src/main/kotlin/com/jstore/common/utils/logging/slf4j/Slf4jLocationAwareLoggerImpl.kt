package com.jstore.common.utils.logging.slf4j

import com.jstore.common.utils.logging.Log
import com.jstore.common.utils.logging.LoggerFactory

import org.slf4j.Marker
import org.slf4j.MarkerFactory
import org.slf4j.spi.LocationAwareLogger

class Slf4jLocationAwareLoggerImpl(private val logger: LocationAwareLogger): Log {
    companion object {
        private val MARKER: Marker = MarkerFactory.getMarker(LoggerFactory.MARKER)
        private val FQCN = Slf4jLocationAwareLoggerImpl::class.qualifiedName;
    }

    override fun isDebugEnabled(): Boolean {
        return logger.isDebugEnabled
    }

    override fun info(format: String, args: Array<out Any>) {
        logger.log(MARKER, FQCN, LocationAwareLogger.INFO_INT, format, args, null)
    }

    override fun info(format: String, arg: Any) {
        logger.log(MARKER, FQCN, LocationAwareLogger.INFO_INT, format, arrayOf(arg), null)
    }

    override fun info(format: String, throwable: Throwable) {
        logger.log(MARKER, FQCN, LocationAwareLogger.INFO_INT, format, null, throwable)
    }

    override fun info(msg: String) {
        logger.log(MARKER, FQCN, LocationAwareLogger.INFO_INT, msg, null, null)
    }

    override fun debug(msg: String) {
        logger.log(MARKER, FQCN, LocationAwareLogger.DEBUG_INT, msg, null, null)
    }

    override fun debug(format: String, arg: Any) {
        logger.log(MARKER, FQCN, LocationAwareLogger.DEBUG_INT, format, null, null)
    }

    override fun debug(format: String, throwable: Throwable) {
        logger.log(MARKER, FQCN, LocationAwareLogger.DEBUG_INT, format, null, throwable)
    }

    override fun debug(format: String, args: Array<out Any>) {
        logger.log(MARKER, FQCN, LocationAwareLogger.DEBUG_INT, format, args, null)
    }

    override fun warn(msg: String) {
        logger.log(MARKER, FQCN, LocationAwareLogger.WARN_INT, msg, null, null)
    }

    override fun warn(format: String, arg: Any) {
        logger.log(MARKER, FQCN, LocationAwareLogger.WARN_INT, format, arrayOf(arg), null)
    }

    override fun warn(format: String, throwable: Throwable) {
        logger.log(MARKER, FQCN, LocationAwareLogger.WARN_INT, format, null, throwable)
    }

    override fun warn(format: String, args: Array<out Any>) {
        logger.log(MARKER, FQCN, LocationAwareLogger.WARN_INT, format, args, null)
    }

    override fun error(msg: String) {
        logger.log(MARKER, FQCN, LocationAwareLogger.ERROR_INT, msg, null, null)
    }

    override fun error(format: String, arg: Any) {

        logger.log(MARKER, FQCN, LocationAwareLogger.ERROR_INT, format, arrayOf(arg), null)
    }

    override fun error(format: String, throwable: Throwable) {
        logger.log(MARKER, FQCN, LocationAwareLogger.ERROR_INT, format, null, throwable)
    }

    override fun error(format: String, args: Array<out Any>) {
        logger.log(MARKER, FQCN, LocationAwareLogger.ERROR_INT, format, args, null)
    }

}