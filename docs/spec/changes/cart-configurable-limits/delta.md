# 购物车可配置限制与 Nacos 动态刷新

用户授权：将固定数量/行数限制改为外部注入，并接入 Nacos 支持运行时刷新。

## 需求与验收

- CART-R1 数量范围与质量目标中的行数上限改为可配置；缺省仍为 1..999、100 行。
- Boot 属性类集中提供默认值，支持本地 Spring 配置绑定；领域层的 CartLimits 不含框架依赖、配置读取或隐式默认值。
- 应用层通过 CartLimitsProvider 获取不可变快照。一次数量用例的检查、外部 Offer 查询、提交和乐观锁重试共享同一份快照，下一次请求使用最新有效配置。
- 配置要求 minQuantity > 0、maxQuantity >= minQuantity、maxLines > 0；启动时非法配置失败。运行中非法更新保留最近有效快照，输出不包含配置正文/凭据的告警。
- Nacos 默认关闭。显式开启时连接必填，启动读取失败或配置缺失失败；启动后删除配置或空内容视为非法更新，保留最近有效快照。非空远端配置缺失的字段回落到本地配置及属性默认值。
- 限制只作用于新的数量设置命令，不自动修改/删除已有行。容量收紧后仍可更新原行；不在新数量范围内的目标不能作为幂等成功绕过校验。刷新、Selection 和 Checkout 保持既有职责。
- 默认值仅在 Boot 属性类声明。领域测试显式构造限制。
- 数据库仅保留 quantity > 0 的结构不变量；按内部开发规则修改当前基线，不新增旧开发数据迁移或自动执行数据库变更。

## 技术选择

使用官方 nacos-client 3.2.4，版本由 catalog 与统一 Platform constraint 管理；仅 Cart Boot 依赖。
通过 ConfigService 的 getConfigAndSignListener 读取/监听独立 properties 文档，校验整份候选后原子替换快照。
不用逐字段可变配置直接驱动领域，也不在业务事务中调用配置中心。Spring 容器负责客户端关闭。

参考：https://nacos.io/en/docs/v3.0/manual/user/java-sdk/usage/

## 验证目标

领域边界、自定义限制、属性默认/部分覆盖、Nacos 初始读取/合法及非法刷新/删除/关闭、请求内快照稳定、PostgreSQL 数量大于 999 的持久化；依赖解析、SBOM/漏洞/许可证、完整本地质量门禁。

## 恢复

关闭 Nacos 开关后使用本地配置；完整回退需同时恢复代码和基线，并重建可丢弃的开发数据库。未授权触碰现有数据库或外部 Nacos 配置。

## Nacos 传递依赖安全例外

OSV Scanner 2.4.0 对首次生成的生产 SBOM 报告新增 HTTP Components 漏洞：
httpclient5 5.5.2 命中 GHSA-hjcp-jmpx-g3qm，httpcore5/httpcore5-h2 5.3.6
分别命中 GHSA-hf6x-8p5f-cgmf、GHSA-v3jc-474w-2wm6。
因此统一 Platform 对这三个坐标设置安全约束为 5.6.3、5.4.3、5.4.3。
这是本次 Nacos 接入的必要安全例外，不修改各消费模块的版本策略。
HttpClient 5.6.3 的官方 parent POM 本身选择 HttpCore 5.4.3；其余 Boot 管理版本不变。

- https://github.com/advisories/GHSA-hjcp-jmpx-g3qm
- https://github.com/advisories/GHSA-hf6x-8p5f-cgmf
- https://github.com/advisories/GHSA-v3jc-474w-2wm6
- https://repo.maven.apache.org/maven2/org/apache/httpcomponents/client5/httpclient5-parent/5.6.3/httpclient5-parent-5.6.3.pom

首次扫描发现基线 netty-handler 4.1.136.Final 的两项公告；同步最新 develop
（ecce81c5，包含 Netty 4.1.137.Final 修复）后重新扫描已消除。本 PR 不包含额外 Netty 升级。

## 验证结果

2026-09-16：同步 develop ecce81c5 后重跑完整本地质量门禁通过。
生产 SBOM 共 227 个包；使用已验证发布摘要、与 CI 同版本的 OSV Scanner 2.4.0 扫描，
退出码 0、无漏洞发现。Nacos 监听以 ConfigService mock 验证，真实服务端连接与配置推送、
独立评审未执行；远端 CI 状态以 PR 最新提交检查为准。
详细证据摘要见 `docs/spec/cart/summary.md`。
