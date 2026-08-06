package com.jstore.goods.domain.commodity

enum class CommodityStatus {
    /** 商品资料草稿。 */
    DRAFT,
    /** 商品资料已经发布，可被 Store/Offer 引用。 */
    PUBLISHED,
    /** 商品资料已归档，不再允许建立新的销售要约。 */
    ARCHIVED,
}
