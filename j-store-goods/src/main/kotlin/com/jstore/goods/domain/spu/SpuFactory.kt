package com.jstore.goods.domain.spu

import com.jstore.common.persistent.SnowFlakSequence
import org.springframework.stereotype.Component

@Component
class SpuFactory(
    private val snowFlakSequence: SnowFlakSequence
) {
    fun create(createCmd: SpuCreateCmd): Spu {
        return SpuImpl(
            id = SpuId(snowFlakSequence.nextId()),
            spuName = createCmd.spuName,
        )
    }
}