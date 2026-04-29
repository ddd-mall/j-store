package com.jstore.goods.domain.commodity

import com.jstore.common.framework.Repository

interface SpuRepository : Repository<SpuId, Spu> {

    /** 根据源商品 ID 查询其草稿副本 */
    fun findDraftBySourceSpuId(sourceSpuId: SpuId): Spu?

    /** 删除 SPU（含关联 SKU），仅用于草稿副本清理 */
    fun delete(spu: Spu)
}