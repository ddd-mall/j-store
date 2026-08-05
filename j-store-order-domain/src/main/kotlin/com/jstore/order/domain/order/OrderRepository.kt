package com.jstore.order.domain.order

import com.jstore.common.framework.Page
import com.jstore.common.framework.Repository

/**
 * 订单仓储接口 ✅ 改进：
 * - 移除基础设施概念（如findByIdAndLock）
 * - 清晰的方法语义（add vs save）
 * - 只定义业务相关方法
 */
interface OrderRepository : Repository<OrderId, Order> {

    /** 添加新订单 */
    fun add(order: Order)

    /** 保存已存在的订单（更新） */
    override fun save(entity: Order): Order

    /** 根据ID查询订单 */
    override fun findById(id: OrderId): Order?

    /** 根据买家ID查询订单列表 */
    fun findByBuyerUserId(uid: Long): List<Order>

    /** 分页查询用户订单 */
    fun pageListByUserId(uid: Long, currentPage: Int, pageSize: Int): Page<Order>
}
