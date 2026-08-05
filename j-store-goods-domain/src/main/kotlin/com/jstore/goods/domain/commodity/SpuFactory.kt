package com.jstore.goods.domain.commodity

import com.jstore.common.errors.BusinessError
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.goods.domain.commodity.comand.CommodityCreateCmd
import com.jstore.goods.domain.commodity.comand.SkuCreateCmd

interface SpuFactory {
    fun create(createCmd: CommodityCreateCmd): Spu

    fun update(createCmd: CommodityCreateCmd, old: Spu): Spu

    fun createSku(cmd: SkuCreateCmd): Sku

    fun createDraftCopy(source: Spu): Result<Spu, BusinessError>
}

class SpuFactoryImpl(private val snowFlakSequence: SnowFlakSequence) : SpuFactory {

    override fun create(createCmd: CommodityCreateCmd): Spu {
        return SpuImpl(
            id = SpuId(snowFlakSequence.nextId()),
            merchantId = MerchantId(createCmd.merchantId),
            name = createCmd.spuName,
            description = createCmd.description,
            _status = CommodityStatus.DRAFT,
            _skus = ArrayList(),
        )
    }

    override fun update(createCmd: CommodityCreateCmd, old: Spu): Spu {
        require(createCmd.merchantId == old.merchantId.value) { "商品不能转移到其他商户" }
        return SpuImpl(
            id = old.id,
            merchantId = old.merchantId,
            name = createCmd.spuName,
            description = createCmd.description,
            _status = old.status,
            _skus = old.skus.toMutableList(),
            _version = old.version,
            sourceSpuId = old.sourceSpuId,
        )
    }

    override fun createSku(cmd: SkuCreateCmd): Sku {
        return SkuImpl(
            id = SkuId(snowFlakSequence.nextId()),
            skuName = cmd.skuName,
            attributes = cmd.attributes,
            merchantCode = cmd.merchantCode,
            barcode = cmd.barcode,
        )
    }

    /** 从已发布商品创建草稿副本。 */
    override fun createDraftCopy(source: Spu): Result<Spu, BusinessError> {
        if (source.status != CommodityStatus.PUBLISHED) {
            return Failure(CommodityErrors.ONLY_PUBLISHED_NEEDS_DRAFT)
        }
        val draft =
            SpuImpl(
                id = SpuId(snowFlakSequence.nextId()),
                merchantId = source.merchantId,
                name = source.name,
                description = source.description,
                _status = CommodityStatus.DRAFT,
                _skus = source.skus.toMutableList(),
                _version = source.version,
                sourceSpuId = source.id,
            )
        return Success(draft)
    }
}
