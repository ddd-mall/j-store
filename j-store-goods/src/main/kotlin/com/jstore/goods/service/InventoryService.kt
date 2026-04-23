package com.jstore.goods.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.errors.CommonBusinessError
import com.jstore.common.errors.CommonBusinessError.CONCURRENT_CONFLICT_EXCEPTION
import com.jstore.common.errors.CommonBusinessError.OBJECT_NOT_FOUNT
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.utils.*
import com.jstore.goods.domain.inventory.*
import java.math.BigDecimal
import java.time.LocalDateTime


class InventoryService(
    private val inventoryRepository: InventoryRepository,
    private val reservationRecordRepository: ReservationRecordRepository,
    private val inventoryFactory: InventoryFactory,
    private val inventoryLock: InventoryLock,
    private val inventoryLockConfig: InventoryLockConfig,
    private val snowFlakSequence: SnowFlakSequence,
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class)
    }

    fun create(cmd: StorageCreateCMD): Result<Inventory, BusinessError> {
        val verifyResult = cmd.verify()
        if (verifyResult is Failure) {
            return verifyResult
        }
        val storage = inventoryFactory.create(cmd)
        return Success(inventoryRepository.save(storage))
    }

    fun reserve(
        bizCode: String,
        commodityCode: CommodityCode,
        amount: BigDecimal,
    ): Result<ReservationRecord, BusinessError> {
        reservationRecordRepository.findByBizCode(bizCode)?.let { return Success(it) }

        val result = inventoryLock.lock(
            commodityCode,
            inventoryLockConfig.getLockTimeout(),
            inventoryLockConfig.getLockTimeUnit()
        ).map { lock ->
            lock.use {
                val storage = inventoryRepository.findById(commodityCode)
                    ?: return Failure(StorageErrors.STORAGE_DOSE_NOT_EXIST.msg("inventory $commodityCode does not exist."))
                val deductResult = storage.reserve(amount)
                if (deductResult is Failure) {
                    return deductResult
                }
                inventoryRepository.save(storage)
                val reservationRecord = createReservationRecord(bizCode, commodityCode, amount)
                reservationRecordRepository.save(reservationRecord)
            }
        }.mapError { error ->
            log.error("[acquire lock failure] - $error")
            CONCURRENT_CONFLICT_EXCEPTION
        }
        return result
    }

    fun confirm(bizCode: String): Result<Boolean, BusinessError> {
        val reservationRecord = reservationRecordRepository.findByBizCode(bizCode)
            ?: return Failure(StorageErrors.RESERVATION_RECORD_NOT_FOUND)

        if (reservationRecord.status == ReservationStatus.CONFIRMED) {
            return Success(true)
        }

        if (reservationRecord.status == ReservationStatus.RELEASED || reservationRecord.expiryTime < LocalDateTime.now()) {
            return Failure(CommonBusinessError.ILLEGAL_STATE.msg("reservation already released!!"))
        }

        val inventory = inventoryRepository.findById(reservationRecord.commodityCode)!!
        inventory.deduct(reservationRecord.amount).onFailure { return Failure(it) }
        inventoryRepository.save(inventory)

        reservationRecord.status = ReservationStatus.CONFIRMED
        reservationRecordRepository.save(reservationRecord)
        return Success(true)
    }

    fun release(bizCode: String): Result<Boolean, BusinessError> {
        val reservationRecord = reservationRecordRepository.findByBizCode(bizCode)
            ?: return Failure(StorageErrors.RESERVATION_RECORD_NOT_FOUND)
        if (reservationRecord.status == ReservationStatus.CONFIRMED) {
            return Failure(CommonBusinessError.ILLEGAL_STATE.msg("reservation already deducted!!"))
        }
        if (reservationRecord.status == ReservationStatus.RELEASED) {
            return Success(true)
        }

        val inventory = inventoryRepository.findById(reservationRecord.commodityCode)!!
        val releaseResult = inventory.release(reservationRecord.amount)
        if (releaseResult is Failure) {
            return releaseResult
        }

        reservationRecord.status = ReservationStatus.RELEASED
        reservationRecordRepository.save(reservationRecord)
        return Success(true)
    }

    private fun createReservationRecord(
        bizCode: String,
        commodityCode: CommodityCode,
        amount: BigDecimal,
    ): ReservationRecord {
        return ReservationRecord(
            id = ReservationId(snowFlakSequence.nextId()),
            bizCode = bizCode,
            commodityCode = commodityCode,
            amount = amount,
            status = ReservationStatus.RESERVED,
            expiryTime = LocalDateTime.now().plusMinutes(30)
        )
    }

    fun add(commodityCode: CommodityCode, quantity: BigDecimal): Result<Boolean, BusinessError> {
        val result = inventoryLock.lock(
            commodityCode,
            inventoryLockConfig.getLockTimeout(),
            inventoryLockConfig.getLockTimeUnit()
        ).map { lock ->
            lock.use {
                val inventory = inventoryRepository.findById(commodityCode) ?: return Failure(OBJECT_NOT_FOUNT)
                inventory.add(quantity)
                inventoryRepository.save(inventory)
                true
            }
        }.mapError { error -> CONCURRENT_CONFLICT_EXCEPTION.msg(error.message!!) }
        return result
    }

}