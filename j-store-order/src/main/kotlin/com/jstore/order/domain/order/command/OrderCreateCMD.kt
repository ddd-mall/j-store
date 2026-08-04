package com.jstore.order.domain.order.command

import com.jstore.common.errors.BusinessError
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.onFailure
import com.jstore.order.domain.order.OrderErrors
import java.io.Serializable

/** 创建订单命令 */
data class OrderCreateCMD(
    val buyerUid: Long,
    val merchantId: Long,
    val buyerPhone: String?,
    val buyerName: String?,
    val recipientInfo: RecipientInfoCMD,
    val items: List<OrderItemCMD>,
) : Serializable {
    data class OrderItemCMD(
        val spuId: Long,
        val skuId: Long,
        val quantity: Int,
        val snapshotVersion: Long,
    )

    data class ContractInfoCMD(
        val phoneNumber: PhoneNumber? = null,
        val emailAddress: String? = null,
    ) {
        fun validate(): Result<ContractInfoCMD, BusinessError> {
            if (null == phoneNumber && null == emailAddress)
                return Failure(OrderErrors.CONTRACT_INFO_INVALID.msg("收货人联系方式不能全为空"))
            return Success(this)
        }
    }

    data class RecipientInfoCMD(
        val consigneeName: String,
        val countryCode: String? = null,
        val consigneeContractInfo: ContractInfoCMD,
        val shippingDistrictCode: String,
        val shippingDetailAddress: String,
    ) {
        fun validate(): Result<RecipientInfoCMD, BusinessError> {
            if (consigneeName.isBlank()) return Failure(OrderErrors.CONSIGNEE_NAME_BLANK)
            if (shippingDistrictCode.isBlank()) return Failure(OrderErrors.DISTRICT_CODE_BLANK)
            consigneeContractInfo.validate().onFailure {
                return Failure(it)
            }
            return Success(this)
        }
    }

    fun validate(): Result<OrderCreateCMD, BusinessError> {
        if (items.isEmpty()) return Failure(OrderErrors.ITEMS_EMPTY)
        if (buyerUid <= 0) return Failure(OrderErrors.BUYER_INVALID)
        if (merchantId <= 0) return Failure(OrderErrors.MERCHANT_INVALID)
        recipientInfo.validate().onFailure {
            return Failure(it)
        }

        return Success(this)
    }
}
