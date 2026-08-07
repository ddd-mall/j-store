/*
 * SPDX-FileCopyrightText: 2024-2026 潘少峰 (Peter Pan)
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

open class SnowFlakSequence {
    private val workerId: Long
    private val datacenterId: Long

    constructor(workerId: Long, datacenterId: Long) {
        if (workerId !in 0..MAX_WORKER_ID) {
            throw CommonErrors.INVALID_PARAM.msg(
                "worker Id can't be greater than $MAX_WORKER_ID or less than 0"
            )
        }
        if (datacenterId !in 0..MAX_DATACENTER_ID) {
            throw CommonErrors.INVALID_PARAM.msg(
                "datacenter Id can't be greater than $MAX_DATACENTER_ID or less than 0"
            )
        }
        this.workerId = workerId
        this.datacenterId = datacenterId
    }

    constructor() {
        this.datacenterId = getDatacenterId()
        this.workerId = getDefaultWorkerId(datacenterId)
    }

    companion object {

        private val logger: Logger = LoggerFactory.getLogger(SnowFlakSequence::class)

        /**
         * |-timestamp-|datacenterId|workerId|sequence| |----41-----|------5-----|----5---|---12---|
         */

        /** 基准 */
        private const val TWEPOCH: Long = 1733587936970

        /** 机器标识位 */
        private const val WORKER_ID_BITS: Int = 5
        private const val DATACENTER_ID_BITS: Int = 5
        private const val MAX_WORKER_ID: Long = (-1L shl WORKER_ID_BITS).inv()
        private const val MAX_DATACENTER_ID: Long = (-1L shl DATACENTER_ID_BITS).inv()

        /** 毫秒内自增 */
        private const val SEQUENCE_BITS: Int = 12
        private const val WORKER_ID_SHIFT: Int = SEQUENCE_BITS
        private const val DATACENTER_SHIFT = SEQUENCE_BITS + WORKER_ID_SHIFT

        /** 时间戳左移位 */
        private const val TIMESTAMP_SHIFT: Int =
            SEQUENCE_BITS + WORKER_ID_SHIFT + DATACENTER_ID_BITS
        private const val SEQUENCE_MASK: Long = (-1L shl SEQUENCE_BITS).inv()

        private fun getDefaultWorkerId(datacenterId: Long): Long {
            val mid = StringBuilder()
            mid.append(datacenterId)
            val name = ManagementFactory.getRuntimeMXBean().name
            if (StringUtils.isNotEmpty(name)) {
                mid.append(name.split("@").dropLastWhile { it.isEmpty() }.toTypedArray()[0])
            }
            return (mid.toString().hashCode() and 0xffff) % (MAX_WORKER_ID + 1)
        }

        private fun getDatacenterId(): Long {
            var id: Long = 0
            try {
                val ip: InetAddress = InetAddress.getLocalHost()
                val network: NetworkInterface = NetworkInterface.getByInetAddress(ip)
                val mac: ByteArray? = network.getHardwareAddress()
                id = 1
                mac?.let {
                    id =
                        ((0x000000FFL and mac[mac.size - 1].toLong()) or
                            (0x0000FF00L and ((mac[mac.size - 2].toLong()) shl 8))) shr 6
                    id %= (MAX_DATACENTER_ID + 1)
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
                        throw RuntimeException(
                            "Clock moved backwards.  Refusing to generate id for $offset milliseconds"
                        )
                    }
                } catch (e: Exception) {
                    throw RuntimeException(e)
                }
            } else {
                throw java.lang.RuntimeException(
                    "Clock moved backwards.  Refusing to generate id for $offset milliseconds"
                )
            }
        }

        if (lastTimestamp == timeStamp) {
            sequence = (sequence + 1) and SEQUENCE_MASK
            if (sequence == 0L) {
                timeStamp = tilNextMillis(lastTimestamp)
            }
        } else {
            sequence = ThreadLocalRandom.current().nextLong(1, 3)
        }

        lastTimestamp = timeStamp
        // 时间戳部分 | 数据中心部分 | 机器标识部分 | 序列号部分
        return (((timeStamp - TWEPOCH) shl TIMESTAMP_SHIFT) or
            (datacenterId shl DATACENTER_SHIFT) or
            (workerId shl WORKER_ID_SHIFT) or
            sequence)
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
        val executor: ScheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor { runnable: Runnable ->
                run {
                    val thread = Thread(runnable, "System lock")
                    thread.isDaemon = true
                    thread
                }
            }
        executor.scheduleAtFixedRate(
            { now.set(System.currentTimeMillis()) },
            period,
            period,
            TimeUnit.SECONDS,
        )
    }

    fun currentTimeMillis(): Long {
        return now.get()
    }
}
