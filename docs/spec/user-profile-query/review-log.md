# 用户资料跨上下文查询评审记录

## 独立安全评审

首次评审结论为 FAIL，发现以下问题：

1. HIGH：订单详情与取消只要求登录，未校验订单归属；真实用户资料快照引入后会扩大隐私泄露影响。
2. HIGH：组合应用中 User boot 无条件提供本地 `UserProfileQueryService`，使 `remote` 自动配置因已有 Bean 而静默失效。
3. MEDIUM：开发验证码发送器记录完整手机号和验证码。

修复候选已完成：

- 订单详情与取消在应用服务层校验 `buyerInfo.uid`，越权统一返回 `ORDER_NOT_FOUND`；公开响应移除昵称和手机号。
- User 提供方改为独立 `UserProfileReader`；仅 local 模式适配消费接口，remote 模式只允许 HTTP 实现并对配置 fail-fast。
- 开发发送器不再记录验证码，只记录脱敏手机号。
- 远程客户端额外拒绝 5xx、空响应、非法资料和用户 ID 不匹配响应。

首次复评确认上述三个问题已解决，但发现一个 MEDIUM：远程 DTO 只校验手机号非空，会让非 E.164 数据延迟到 Order ACL 才抛普通参数异常。修复后发布 DTO 在反序列化边界校验 E.164 结构且错误消息不含原始号码，远程客户端将该失败统一包装为 `UserProfileDependencyException`；对应非法手机号回归测试已补充。

最终独立复评结论为 PASS，无未解决 blocker。复评额外强制执行 User API/client 测试并确认非法手机号被包装为不含原始资料的依赖失败。

非阻断测试增强项：User API DTO 尚无直接边界单测；内部端点尚未在真实认证拦截器组合下做 HTTP 集成测试；connect/read timeout 有配置门禁但未用真实延迟端点验证。
