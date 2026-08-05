package com.jstore.goods.domain.commodity

import com.jstore.common.errors.BusinessError

object CommodityErrors {
    val SPU_NOT_FOUND = BusinessError("商品不存在", "Goods.SpuNotFound", 404)
    val SKU_NOT_FOUND = BusinessError("SKU不存在", "Goods.SkuNotFound", 404)
    val INVALID_STATUS_TRANSITION = BusinessError("非法的状态变更", "Goods.InvalidStatusTransition", 400)
    val NO_SKU_FOR_PUBLISH = BusinessError("商品至少需要一个SKU才能发布", "Goods.NoSkuForPublish", 400)
    val DUPLICATE_SKU_ATTRIBUTES = BusinessError("SKU属性组合重复", "Goods.DuplicateSkuAttributes", 400)
    val SNAPSHOT_SPU_MUST_BE_PUBLISHED =
        BusinessError("只有已发布商品才能创建快照", "Catalog.Snapshot.RequiresPublished", 400)
    val DUPLICATE_IMAGE_KEY = BusinessError("图片标识重复", "Goods.DuplicateImageKey", 400)

    // 草稿流程相关错误
    val DRAFT_ALREADY_EXISTS = BusinessError("该商品已存在草稿副本", "Goods.Draft.AlreadyExists", 409)
    val PUBLISHED_DIRECT_EDIT_REJECTED =
        BusinessError("已发布商品不允许直接编辑，请通过草稿流程修改", "Catalog.Draft.PublishedDirectEditRejected", 400)
    val NOT_A_DRAFT_COPY = BusinessError("该商品不是草稿副本", "Goods.Draft.NotADraftCopy", 400)
    val ONLY_PUBLISHED_NEEDS_DRAFT =
        BusinessError("只有已发布商品需要通过草稿编辑", "Catalog.Draft.OnlyPublishedNeedsDraft", 400)
    val DRAFT_NO_SKU_FOR_PUBLISH =
        BusinessError("草稿至少需要一个SKU才能发布", "Goods.Draft.NoSkuForPublish", 400)
}
