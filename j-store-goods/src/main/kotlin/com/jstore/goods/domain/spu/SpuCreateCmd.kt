package com.jstore.goods.domain.spu

import org.springframework.stereotype.Component

data class SpuCreateCmd(
    val spuName: String,
    val goodsCategory: GoodsCategory
)

@Component
class SpuCreateCmdHandler (
    private val spuFactory: SpuFactory,
    private val spuRepository: SpuRepository
){
    fun handle(spuCreateCmd: SpuCreateCmd) {
        val spu = spuFactory.create(spuCreateCmd)
        spuRepository.save(spu)
    }
}