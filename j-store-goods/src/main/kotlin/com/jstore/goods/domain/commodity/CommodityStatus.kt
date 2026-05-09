package com.jstore.goods.domain.commodity

enum class CommodityStatus {
    /** 草稿 — 新建未发布 */
    DRAFT,
    /** 已发布/下架 — 可上架 */
    OFF_SALE,
    /** 在售 — 可下架 */
    ON_SALE,
}