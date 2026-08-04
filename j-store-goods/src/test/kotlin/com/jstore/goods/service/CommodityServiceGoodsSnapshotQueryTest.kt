package com.jstore.goods.service

import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.properties.Price
import com.jstore.goods.api.GoodsSkuSnapshotInfo
import com.jstore.goods.domain.commodity.Attribute
import com.jstore.goods.domain.commodity.GoodsStyleFactory
import com.jstore.goods.domain.commodity.GoodsStyleRepository
import com.jstore.goods.domain.commodity.MerchantId
import com.jstore.goods.domain.commodity.SkuId
import com.jstore.goods.domain.commodity.SpuFactory
import com.jstore.goods.domain.commodity.SpuId
import com.jstore.goods.domain.commodity.SpuRepository
import com.jstore.goods.domain.commodity.snapshot.SkuSnapshot
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshot
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshotFactory
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshotId
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshotRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class CommodityServiceGoodsSnapshotQueryTest : FunSpec({

    test("queryLatestSnapshots maps goods domain snapshot to published API DTO") {
        val snapshotRepository = mock<SpuSnapshotRepository>()
        val service = CommodityService(
            spuFactory = mock<SpuFactory>(),
            spuRepository = mock<SpuRepository>(),
            domainEventPublisher = mock<DomainEventPublisher>(),
            snapshotFactory = mock<SpuSnapshotFactory>(),
            snapshotRepository = snapshotRepository,
            goodsStyleRepository = mock<GoodsStyleRepository>(),
            goodsStyleFactory = mock<GoodsStyleFactory>(),
        )
        val price = Price.ofFen(12800)
        val snapshot = SpuSnapshot(
            id = SpuSnapshotId(9001L),
            merchantId = MerchantId(7),
            spuId = SpuId(1001L),
            snapshotVersion = 3L,
            spuName = "Keyboard",
            description = "Mechanical keyboard",
            skuSnapshots = listOf(
                SkuSnapshot(
                    skuId = SkuId(2001L),
                    skuName = "Blue Switch",
                    attributes = listOf(Attribute("switch", "blue"), Attribute("layout", "87")),
                    price = price,
                )
            ),
            createdAt = LocalDateTime.parse("2026-05-08T10:15:30"),
        )
        whenever(snapshotRepository.findLatestBySpuId(argThat { value == 1001L })).thenReturn(snapshot)
        whenever(snapshotRepository.findLatestBySpuId(argThat { value == 1002L })).thenReturn(null)

        val result = service.queryLatestSnapshots(listOf(1001L, 1002L, 1001L))

        result.size shouldBe 1
        result.first().spuId shouldBe 1001L
        result.first().snapshotVersion shouldBe 3L
        result.first().spuName shouldBe "Keyboard"
        result.first().skuSnapshots.shouldContainExactly(
            listOf(
                GoodsSkuSnapshotInfo(
                    skuId = 2001L,
                    skuName = "Blue Switch",
                    attributes = listOf("switch" to "blue", "layout" to "87"),
                    price = price,
                )
            )
        )
    }
})
