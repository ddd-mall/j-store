package com.jstore.common.persistent

import com.jstore.common.errors.CommonErrors
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.common.utils.string.StringUtils
import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

open class SnowFlakSequence(private val workerId: Long, private val datacenterId: Long) {

    init {
        if (workerId > maxWorkerId || workerId < 0) {
            throw CommonErrors.INVALID_PARAM.withMsg("worker Id can't be greater than $maxWorkerId or less than 0")
        }
        if (datacenterId > maxDatacenterId || datacenterId < 0) {
            throw CommonErrors.INVALID_PARAM.withMsg("datacenter Id can't be greater than $maxDatacenterId or less than 0")
        }
    }


    companion object {
        fun SnowFlakSequence(): SnowFlakSequence {
            val datacenterId = getDatacenterId()
            val workerId = getDefaultWorkerId(datacenterId)
            return SnowFlakSequence(workerId, datacenterId)
        }

        private val logger: Logger = LoggerFactory.getLogger(SnowFlakSequence::class)
        /**
         * |-timestamp-|datacenterId|workerId|sequence|
         * |----41-----|------5-----|----5---|---12---|
         */

        /**
         * 基准
         */
        private val twepoch: Long = 1733587936970

        /**
         * 机器标识位
         */
        private val workerIdBits: Int = 5
        private val datacenterIdBits: Int = 5
        private val maxWorkerId: Long = (-1L shl workerIdBits).inv()
        private val maxDatacenterId: Long = (-1L shl datacenterIdBits).inv()

        /**
         * 毫秒内自增
         */
        private val sequenceBits: Int = 12
        private val workerIdShift: Int = sequenceBits
        private val datacenterShift = sequenceBits + workerIdShift

        /**
         * 时间戳左移位
         */
        private val timestampShift: Int = sequenceBits + workerIdShift + datacenterIdBits
        private val sequenceMask: Long = (-1L shl sequenceBits).inv()

        private fun getDefaultWorkerId(datacenterId: Long): Long {
            val mpid = StringBuilder()
            mpid.append(datacenterId)
            val name = ManagementFactory.getRuntimeMXBean().name
            if (StringUtils.isNotEmpty(name)) {
                mpid.append(name.split("@").dropLastWhile { it.isEmpty() }.toTypedArray()[0])
            }
            return (mpid.toString().hashCode() and 0xffff) % (maxWorkerId + 1)
        }

        private fun getDatacenterId(): Long {
            var id: Long = 0
            try {
                val ip: InetAddress = InetAddress.getLocalHost()
                val network: NetworkInterface = NetworkInterface.getByInetAddress(ip)
                val mac: ByteArray? = network.getHardwareAddress()
                id = 1
                mac?.let {
                    id = ((0x000000FFL and mac[mac.size - 1].toLong()) or (0x0000FF00L and ((mac[mac.size - 2].toLong()) shl 8))) shr 6
                    id %= (maxDatacenterId + 1)
                }
            } catch (t: Throwable) {
                logger.warn("error occurred when get datacenter id ${t.message}")
            }
            return id
        }
    }

    private var sequence: Long = 0
    private var lastTimestamp: Long = -1

    @Synchronized
    fun nextId(): Long {
        val lock = Object()
        var timeStamp = timeGen()
        if (timeStamp < lastTimestamp) {
            val offset = lastTimestamp - timeStamp
            if (offset <= 5) {
                try {
                    lock.wait(offset shl 1)
                    timeStamp = timeGen()
                    if (timeStamp < lastTimestamp) {
                        throw RuntimeException("Clock moved backwards.  Refusing to generate id for $offset milliseconds")
                    }
                } catch (e: Exception) {
                    throw RuntimeException(e)
                }
            } else {
                throw java.lang.RuntimeException("Clock moved backwards.  Refusing to generate id for $offset milliseconds")
            }
        }

        if (lastTimestamp == timeStamp) {
            sequence = (sequence + 1) and sequenceMask
            if (sequence == 0L) {
                timeStamp = tilNextMillis(lastTimestamp)
            }
        } else {
            sequence = ThreadLocalRandom.current().nextLong(1, 3)
        }

        lastTimestamp = timeStamp
        // 时间戳部分 | 数据中心部分 | 机器标识部分 | 序列号部分
        return (((timeStamp - twepoch) shl timestampShift)
                or (datacenterId shl datacenterShift)
                or (workerId shl workerIdShift)
                or sequence)
    }

    protected fun tilNextMillis(lastTimeStamp: Long): Long {
        var timestamp = timeGen()
        while (timestamp <= lastTimeStamp) {
            timestamp = timeGen()
        }
        return timestamp
    }


    protected fun timeGen(): Long {
        return SystemLock.now()
    }
}

class SystemLock(private val period: Long) {
    private val now: AtomicLong = AtomicLong(System.currentTimeMillis())

    init {
        scheduleClockUpdating()
    }

    companion object {
        private object InstanceHolder {
            val INSTANCE: SystemLock = SystemLock(1L)
        }

        private fun instance(): SystemLock {
            return InstanceHolder.INSTANCE
        }

        fun now(): Long {
            return instance().currentTimeMillis()
        }
    }

    private fun scheduleClockUpdating() {
        val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable: Runnable ->
            run {
                val thread = Thread(runnable, "System lock")
                thread.isDaemon = true
                thread
            }
        }
        executor.scheduleAtFixedRate({ now.set(System.currentTimeMillis()) }, period, period, TimeUnit.SECONDS)
    }

    fun currentTimeMillis(): Long {
        return now.get()
    }
}

