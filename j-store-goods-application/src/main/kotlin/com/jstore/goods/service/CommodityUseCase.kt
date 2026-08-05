package com.jstore.goods.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Result
import com.jstore.goods.domain.commodity.GoodsStyle
import com.jstore.goods.domain.commodity.Spu
import com.jstore.goods.domain.commodity.SpuId
import com.jstore.goods.domain.commodity.comand.CommodityCreateCmd
import com.jstore.goods.domain.commodity.comand.GoodsStyleSaveCmd
import com.jstore.goods.domain.commodity.comand.SkuCreateCmd
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshot

/** 商品上下文对外暴露的应用用例端口。 */
interface CommodityUseCase {
    fun createOrUpdate(cmd: CommodityCreateCmd): Result<Spu, BusinessError>

    fun addSku(cmd: SkuCreateCmd): Result<Spu, BusinessError>

    fun publish(spuId: SpuId): Result<Unit, BusinessError>

    fun putOnSale(spuId: SpuId): Result<SpuSnapshot, BusinessError>

    fun takeOffSale(spuId: SpuId): Result<Unit, BusinessError>

    fun getDraft(spuId: SpuId): Result<Spu, BusinessError>

    fun publishDraft(draftSpuId: SpuId): Result<SpuSnapshot, BusinessError>

    fun discardDraft(draftSpuId: SpuId): Result<Unit, BusinessError>

    fun saveGoodsStyle(cmd: GoodsStyleSaveCmd): Result<GoodsStyle, BusinessError>
}
