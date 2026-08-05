package com.jstore.shop.domain.merchant

import com.jstore.common.errors.BusinessError

object MerchantErrors {
    val NOT_FOUND = BusinessError("商户不存在", "Merchant.NotFound", 404)
    val NAME_INVALID = BusinessError("商户名称无效", "Merchant.Name.Invalid", 400)
    val ILLEGAL_STATE = BusinessError("商户状态不合法", "Merchant.State.Invalid", 409)
    val FORBIDDEN = BusinessError("无商户操作权限", "Merchant.Access.Forbidden", 403)
    val USER_NOT_FOUND = BusinessError("用户不存在", "Merchant.Member.UserNotFound", 404)
    val MEMBER_NOT_FOUND = BusinessError("商户成员不存在", "Merchant.Member.NotFound", 404)
    val MEMBER_ALREADY_EXISTS = BusinessError("用户已是商户成员", "Merchant.Member.AlreadyExists", 409)
    val ROLES_EMPTY = BusinessError("成员角色不能为空", "Merchant.Member.RolesEmpty", 400)
    val OWNER_PROTECTED = BusinessError("商户所有者不能通过普通成员操作修改", "Merchant.Member.OwnerProtected", 409)
    val OWNER_ROLE_RESERVED =
        BusinessError("OWNER 角色只能由商户创建或所有权转移产生", "Merchant.Member.OwnerRoleReserved", 400)
}
