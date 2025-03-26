package com.jstore.goods.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.errors.CommonBusinessError
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.goods.domain.storage.*
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class InventoryService(
    private val inventoryRepository: InventoryRepository,
    private val reservationRecordRepository: ReservationRecordRepository,
    private val inventoryFactory: InventoryFactory,
    private val inventoryLock: InventoryLock,
    private val inventoryLockConfig: InventoryLockConfig,
    private val reservationRecordFactory: ReservationRecordFactory,
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class)
    }

    fun create(cmd: StorageCreateCMD): Result<Inventory, BusinessError> {
        cmd.verify()?.let { return Failure(it) }
        val storage = inventoryFactory.create(cmd)
        return Success(inventoryRepository.save(storage))
    }

    fun reserve(
        bizCode: String,
        commodityCode: CommodityCode,
        amount: BigDecimal
    ): Result<ReservationRecord, BusinessError> {
        reservationRecordRepository.findByBizCode(bizCode)?.let { return Success(it) }

        val lock = inventoryLock.lock(
            commodityCode,
            inventoryLockConfig.getLockTimeout(),
            inventoryLockConfig.getLockTimeUnit()
        )

        val defaultErrorMsg = " reserve inventory $commodityCode failed!"
        return when (lock) {
            is Success -> {
                lock.value.use {
                    val storage = inventoryRepository.findById(commodityCode)
                        ?: return Failure(StorageErrors.STORAGE_DOSE_NOT_EXIST.msg("inventory $commodityCode does not exist."))

                    val deductResult = storage.reserve(amount)
                    if (deductResult is Failure) {
                        return deductResult
                    }
                    inventoryRepository.save(storage)
                    val reservationRecord = reservationRecordFactory.create(bizCode, commodityCode, amount)

                    Success(reservationRecordRepository.save(reservationRecord))
                }
            }

            is Failure -> {
                log.error("[acquire lock failure] - ${lock.error}")
                Failure(StorageErrors.STORAGE_OPERATION_FAILED.msg(lock.error.message ?: defaultErrorMsg))
            }
        }
    }

    fun confirm(bizCode: String): Result<Boolean, BusinessError> {
        val reservationRecord = reservationRecordRepository.findByBizCode(bizCode)
            ?: return Failure(StorageErrors.RESERVATION_RECORD_NOT_FOUND)

        if (reservationRecord.status == ReservationStatus.CONFIRMED) {
            return Success(true)
        }

        if (reservationRecord.status == ReservationStatus.RELEASED) {
            return Failure(CommonBusinessError.ILLEGAL_STATE.msg("reservation already released!!"))
        }

        val inventory = inventoryRepository.findById(reservationRecord.commodityCode)!!
        val deductResult = inventory.deduct(reservationRecord.amount)
        if (deductResult is Failure) {
            return deductResult
        }
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

}