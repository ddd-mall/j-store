package com.jstore.order.controller

import com.jstore.authentication.annotation.CurrentUserId
import com.jstore.authentication.annotation.RequireLogin
import com.jstore.common.errors.BusinessError
import com.jstore.common.properties.Price
import com.jstore.common.utils.Result
import com.jstore.common.utils.fold
import com.jstore.order.domain.aftersale.*
import com.jstore.order.domain.aftersale.command.*
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderItemId
import com.jstore.order.service.AfterSaleApplicationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import java.time.LocalDateTime

@RestController @RequestMapping("/api/after-sales") @RequireLogin
class AfterSaleController(private val service:AfterSaleApplicationService){
 data class ItemRequest(@field:Positive val orderItemId:Long,@field:Positive val quantity:Int,@field:Positive val amount:Long,@field:NotBlank val currency:String="CNY")
 data class CreateRequest(@field:Positive val orderId:Long,val category:RefundCategory,@field:Size(max=500) val description:String,@field:NotEmpty @field:Valid val items:List<ItemRequest>)
 data class RejectRequest(@field:NotBlank @field:Size(max=500) val rejectionReason:String)
 data class ErrorResponse(val message:String,val errorCode:String)
 data class ItemResponse(val id:Long,val orderItemId:Long,val requestedQuantity:Int,val requestedAmount:Long,val currency:String,val eligibleQuantity:Int,val eligibleAmount:Long,val skuId:Long,val spuId:Long,val goodsName:String,val skuDescription:String)
 data class Response(val id:Long,val orderId:Long,val applicantId:Long,val merchantId:Long,val status:String,val reason:RefundReason,val fulfillmentSnapshot:FulfillmentSnapshot,val items:List<ItemResponse>,val reviewDecision:ReviewDecision?,val cancelledAt:LocalDateTime?,val createTime:LocalDateTime,val updateTime:LocalDateTime)
 @PostMapping fun create(@CurrentUserId uid:Long,@RequestHeader("Idempotency-Key") @NotBlank key:String,@Valid @RequestBody body:CreateRequest)=service.create(AfterSaleCreateCMD(OrderId(body.orderId),ApplicantActorId(uid),RefundReason(body.category,body.description),body.items.map{AfterSaleItemRequestCMD(OrderItemId(it.orderItemId),it.quantity,Price.ofFen(it.amount),it.currency)},key)).response()
 @GetMapping("/{id}") fun get(@CurrentUserId uid:Long,@PathVariable id:Long)=service.get(AfterSaleId(id),uid).response()
 @GetMapping fun list(@CurrentUserId uid:Long,@RequestParam orderId:Long)=service.listByOrder(OrderId(orderId),uid).response { list -> list.map(::map) }
 @PostMapping("/{id}/approve") fun approve(@CurrentUserId uid:Long,@PathVariable id:Long,@RequestHeader("Idempotency-Key") key:String)=service.approve(AfterSaleApproveCMD(AfterSaleId(id),MerchantActorId(uid),key)).response()
 @PostMapping("/{id}/reject") fun reject(@CurrentUserId uid:Long,@PathVariable id:Long,@RequestHeader("Idempotency-Key") key:String,@Valid @RequestBody body:RejectRequest)=service.reject(AfterSaleRejectCMD(AfterSaleId(id),MerchantActorId(uid),body.rejectionReason,key)).response()
 @PostMapping("/{id}/cancel") fun cancel(@CurrentUserId uid:Long,@PathVariable id:Long,@RequestHeader("Idempotency-Key") key:String)=service.cancel(AfterSaleCancelCMD(AfterSaleId(id),ApplicantActorId(uid),key)).response()
 private fun Result<AfterSale,BusinessError>.response()=response(::map)
 private fun <T> Result<T,BusinessError>.response(mapper:(T)->Any):ResponseEntity<*> = fold({ResponseEntity.ok(mapper(it))},{ResponseEntity.status(it.httpCode).body(ErrorResponse(it.message,it.errorCode))})
 private fun map(a:AfterSale)=Response(a.id.value,a.orderId.value,a.applicantId.value,a.merchantId.value,a.status.name,a.reason,a.fulfillmentSnapshot,a.items.map{ItemResponse(it.id.value,it.orderItemId.value,it.requestedQuantity,it.requestedAmount.fen,it.currency,it.eligibilitySnapshot.refundableQuantity,it.eligibilitySnapshot.refundableAmount.fen,it.eligibilitySnapshot.goods.skuId,it.eligibilitySnapshot.goods.spuId,it.eligibilitySnapshot.goods.goodsName,it.eligibilitySnapshot.goods.skuDescription)},a.reviewDecision,a.cancelledAt,a.createTime,a.updateTime)
}
