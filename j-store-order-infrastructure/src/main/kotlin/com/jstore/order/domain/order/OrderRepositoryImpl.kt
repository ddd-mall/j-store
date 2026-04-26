package com.jstore.order.domain.order

import com.jstore.common.framework.Page
import com.jstore.common.framework.SortedPage
import com.jstore.common.geo.AddressComponent
import com.jstore.common.geo.CountryCode
import com.jstore.common.geo.chinese.DistrictCodeUtils
import com.jstore.common.geo.DivisionLevel
import com.jstore.common.geo.I18nGeoAddress
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.properties.Price
import com.jstore.order.domain.order.persistence.OrderItemPO
import com.jstore.order.domain.order.persistence.OrderPO
import com.jstore.order.domain.order.persistence.OrderPOJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Repository
import java.util.Locale

@Repository
class OrderRepositoryImpl(
    private val jpaRepository: OrderPOJpaRepository,
) : OrderRepository {

    override fun add(order: Order) {
        val po = Converter.toPO(order)
        jpaRepository.save(po)
    }

    override fun save(entity: Order): Order {
        val po = Converter.toPO(entity)
        val saved = jpaRepository.save(po)
        return Converter.toDomain(saved)
    }

    override fun findById(id: OrderId): Order? {
        return jpaRepository.findById(id.value).orElse(null)?.let { Converter.toDomain(it) }
    }

    override fun findByBuyerUserId(uid: Long): List<Order> {
        return jpaRepository.findByBuyerUid(uid).map { Converter.toDomain(it) }
    }

    override fun pageListByUserId(uid: Long, currentPage: Int, pageSize: Int): Page<Order> {
        val pageable = PageRequest.of(currentPage - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"))
        val page = jpaRepository.findByBuyerUid(uid, pageable)
        return SortedPage(
            current = currentPage,
            size = page.totalElements.toInt(),
            record = page.content.map { Converter.toDomain(it) }
        )
    }

    private object Converter {

        fun toPO(order: Order): OrderPO {
            return OrderPO(
                id = order.id.value,
                buyerUid = order.buyerInfo.uid,
                buyerPhone = order.buyerInfo.phoneNumber?.value,
                buyerName = order.buyerInfo.userName,
                countryCode = order.shippingAddress.countryCode.value,
                districtCode = order.shippingAddress.getLeafCode(),
                province = order.shippingAddress.getComponentAtLevel(1)?.getDefaultName() ?: "",
                city = order.shippingAddress.getComponentAtLevel(2)?.getDefaultName() ?: "",
                county = order.shippingAddress.getComponentAtLevel(3)?.getDefaultName() ?: "",
                detailAddress = order.shippingDetailAddress,
                status = order.status,
                previousStatus = order.previousStatus,
                totalAmount = order.totalAmount.toBigDecimal(),
                actualPay = order.actualPay.toBigDecimal(),
                createTime = order.createTime,
                updateTime = order.updateTime,
                items = order.items.map { toItemPO(it, order.id.value) }.toMutableList(),
            )
        }

        fun toItemPO(item: OrderItem, orderId: Long): OrderItemPO {
            return OrderItemPO(
                id = item.id.value,
                orderId = orderId,
                skuId = item.skuId,
                spuId = item.spuId,
                goodsName = item.goodsName,
                skuDescription = item.skuDescription,
                quantity = item.quantity,
                unitPrice = item.unitPrice.toBigDecimal(),
                status = item.status,
                previousItemStatus = item.previousItemStatus,
            )
        }

        fun toDomain(po: OrderPO): Order {
            val items = po.items.map { toDomainItem(it) }.toMutableList()
            val countryCode = CountryCode(po.countryCode)
            val defaultLocale = Locale.SIMPLIFIED_CHINESE
            val components = mutableListOf<AddressComponent>()

            if (po.province.isNotBlank()) {
                components.add(AddressComponent(
                    code = DistrictCodeUtils.getProvinceCode(po.districtCode),
                    level = DivisionLevel(1, "省"),
                    names = mapOf(defaultLocale to po.province),
                    defaultLocale = defaultLocale
                ))
            }
            if (po.city.isNotBlank()) {
                components.add(AddressComponent(
                    code = DistrictCodeUtils.getCityCode(po.districtCode),
                    level = DivisionLevel(2, "市"),
                    names = mapOf(defaultLocale to po.city),
                    defaultLocale = defaultLocale
                ))
            }
            if (po.county.isNotBlank()) {
                components.add(AddressComponent(
                    code = po.districtCode,
                    level = DivisionLevel(3, "区/县"),
                    names = mapOf(defaultLocale to po.county),
                    defaultLocale = defaultLocale
                ))
            }

            val address = I18nGeoAddress(
                countryCode = countryCode,
                components = components,
            )

            return OrderImpl(
                id = OrderId(po.id),
                buyerInfo = UserInfo(
                    uid = po.buyerUid,
                    phoneNumber = po.buyerPhone?.let { PhoneNumber(it) },
                    userName = po.buyerName,
                ),
                _items = items.toMutableList(),
                shippingAddress = address,
                shippingDetailAddress = po.detailAddress,
                _status = po.status,
                totalAmount = Price.fromBigDecimal(po.totalAmount),
                _actualPay = Price.fromBigDecimal(po.actualPay),
                createTime = po.createTime,
                _updateTime = po.updateTime,
                _previousStatus = po.previousStatus,
            )
        }

        fun toDomainItem(po: OrderItemPO): OrderItem {
            return OrderItemImpl(
                id = OrderItemId(po.id),
                skuId = po.skuId,
                spuId = po.spuId,
                goodsName = po.goodsName,
                skuDescription = po.skuDescription,
                quantity = po.quantity,
                unitPrice = Price.fromBigDecimal(po.unitPrice),
                status = po.status,
                _previousItemStatus = po.previousItemStatus,
            )
        }
    }
}
