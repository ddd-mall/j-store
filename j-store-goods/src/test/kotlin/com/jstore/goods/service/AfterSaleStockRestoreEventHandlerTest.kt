package com.jstore.goods.service
import com.jstore.goods.acl.event.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.goods.domain.inventory.CommodityCode
import org.mockito.Mockito.*
import java.math.BigDecimal

class AfterSaleStockRestoreEventHandlerTest:FunSpec({
 test("restore contract preserves sku and exact quantity"){val event=AfterSaleStockRestoreRequestedEvent(1,2,listOf(StockRestoreItem(3,4)));event.eventName shouldBe "stock.after-sale-restore-requested";event.items.single() shouldBe StockRestoreItem(3,4)}
 test("handler adds the exact approved quantity once") {
  val inventory = mock(InventoryService::class.java)
  `when`(inventory.add(CommodityCode(3), BigDecimal(4))).thenReturn(Success(true))
  AfterSaleStockRestoreEventHandler(inventory).onDomainEvent(AfterSaleStockRestoreRequestedEvent(1,2,listOf(StockRestoreItem(3,4))))
  verify(inventory, times(1)).add(CommodityCode(3), BigDecimal(4))
 }
 test("handler throws so event consumption is not acknowledged when inventory fails") {
  val inventory = mock(InventoryService::class.java)
  `when`(inventory.add(CommodityCode(3), BigDecimal(4))).thenReturn(Failure(BusinessError("failed","Inventory.Failed",409)))
  shouldThrow<IllegalStateException> { AfterSaleStockRestoreEventHandler(inventory).onDomainEvent(AfterSaleStockRestoreRequestedEvent(1,2,listOf(StockRestoreItem(3,4)))) }
 }
})
