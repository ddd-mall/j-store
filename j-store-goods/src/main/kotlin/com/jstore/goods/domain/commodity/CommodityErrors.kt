package com.jstore.goods.domain.commodity

import com.jstore.common.errors.BusinessError

object CommodityErrors {
    val SPU_NOT_FOUND = BusinessError("商品不存在", "Goods.SpuNotFound", 404)
    val SKU_NOT_FOUND = BusinessError("SKU不存在", "Goods.SkuNotFound", 404)
    val INVALID_STATUS_TRANSITION = BusinessError("非法的状态变更", "Goods.InvalidStatusTransition", 400)
    val DRAFT_CANNOT_ON_SALE = BusinessError("草稿状态不能直接上架，请先发布", "Goods.DraftCannotOnSale", 400)
    val ALREADY_ON_SALE = BusinessError("商品已在售", "Goods.AlreadyOnSale", 400)
    val ALREADY_OFF_SALE = BusinessError("商品已下架", "Goods.AlreadyOffSale", 400)
    val NO_SKU_FOR_PUBLISH = BusinessError("商品至少需要一个SKU才能发布", "Goods.NoSkuForPublish", 400)
    val DUPLICATE_SKU_ATTRIBUTES = BusinessError("SKU属性组合重复", "Goods.DuplicateSkuAttributes", 400)
    val SNAPSHOT_SPU_MUST_BE_ON_SALE = BusinessError("只有在售商品才能创建快照", "Goods.SnapshotSpuMustBeOnSale", 400)
}
