package com.jstore.goods.domain.commodity

import com.jstore.common.utils.json.JsonUtils
import com.jstore.goods.domain.commodity.persistence.GoodsStylePO
import com.jstore.goods.domain.commodity.persistence.GoodsStylePOJpaRepository
import java.time.LocalDateTime
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class GoodsStyleRepositoryImpl(private val jpaRepository: GoodsStylePOJpaRepository) :
    GoodsStyleRepository {

    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(entity: GoodsStyle): GoodsStyle {
        val po = Converter.toPO(entity)
        po.updateTime = LocalDateTime.now()
        val saved = jpaRepository.save(po)
        return Converter.toDomain(saved)
    }

    override fun findById(id: GoodsStyleId): GoodsStyle? {
        return jpaRepository.findById(id.value).orElse(null)?.let { Converter.toDomain(it) }
    }

    override fun findBySpuId(spuId: SpuId): GoodsStyle? {
        return jpaRepository.findBySpuId(spuId.value)?.let { Converter.toDomain(it) }
    }

    internal object Converter {

        fun toPO(goodsStyle: GoodsStyle): GoodsStylePO {
            val skuImagesMap: Map<String, List<String>> =
                goodsStyle.skuImages
                    .map { (skuId, images) ->
                        skuId.value.toString() to images
                    }
                    .toMap()

            return GoodsStylePO(
                id = goodsStyle.id.value,
                spuId = goodsStyle.spuId.value,
                mainImages = JsonUtils.toJsonString(goodsStyle.mainImages),
                detailHtml = goodsStyle.detailHtml,
                skuImages = JsonUtils.toJsonString(skuImagesMap),
            )
        }

        fun toDomain(po: GoodsStylePO): GoodsStyle {
            val mainImages: List<String> = JsonUtils.deserialize(po.mainImages)
            val skuImagesRaw: Map<String, List<String>> = JsonUtils.deserialize(po.skuImages)
            val skuImages: MutableMap<SkuId, List<String>> =
                skuImagesRaw
                    .map { (key, images) ->
                        SkuId(key.toLong()) to images
                    }
                    .toMap()
                    .toMutableMap()

            return GoodsStyleImpl(
                id = GoodsStyleId(po.id),
                spuId = SpuId(po.spuId),
                _mainImages = mainImages.toMutableList(),
                _detailHtml = po.detailHtml,
                _skuImages = skuImages,
            )
        }
    }
}
