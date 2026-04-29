# 需求文档：财务模块基础功能

## 简介

财务模块基础功能面向 j-store 的多商户电商交易流程，目标是在不引入完整 ERP 财务复杂度的前提下，建立独立的 Accounting 限界上下文。第一阶段聚焦总账凭证、账户科目、幂等入账、退款冲正和商户结算的最小闭环。

本阶段不实现完整支付渠道对账、应付管理、财务三大报表、税务、成本核算和多币种汇兑。支付渠道流水、商户银行账户、佣金费率等外部信息通过 Accounting 本地 ACL 或快照数据进入财务模块，不让订单模块依赖财务模块。

## 术语表

- **Accounting_Context**：财务限界上下文，负责根据业务事件生成账务凭证、维护账户科目、执行结算和提供财务查询。
- **Ledger_Account**：账务账户或会计科目，表示平台现金、支付渠道清算、商户待结算款、平台佣金收入等可被分录引用的账户。
- **Journal_Entry**：账务凭证聚合根，由一组借贷分录组成，是财务事实源。
- **Journal_Line**：账务凭证内的分录实体，记录账户、借贷方向和非负金额。
- **Source_Document**：来源业务单据标识，由来源类型、来源 ID 和事件类型组成，用于追溯和幂等入账。
- **Accounting_Event_Handler**：财务事件处理器，监听订单或结算相关领域事件并驱动入账用例。
- **Settlement_Statement**：商户结算单聚合根，记录某商户在一个结算周期内的订单、退款、佣金和应结金额。
- **Accounting_Period**：会计期间，控制某日期范围是否允许新增过账凭证。
- **Reversal_Journal_Entry**：冲正凭证，用相反借贷方向冲销原凭证，不修改原凭证。
- **Accounting_Balance_View**：账户余额查询视图，由已过账凭证分录汇总得到，不作为唯一事实源。

## 需求

### 需求 1：独立财务上下文

**用户故事：** 作为系统架构师，我希望财务能力独立于订单、商品和仓储上下文，以便资金流转规则可以独立演进并保持领域边界清晰。

#### 验收标准

1. THE Accounting_Context SHALL 使用 `j-store-accounting` 和 `j-store-accounting-infrastructure` 承载领域、应用和基础设施代码。
2. THE Accounting_Context SHALL NOT 让订单模块依赖财务模块。
3. THE Accounting_Event_Handler SHALL 通过领域事件或 ACL 获取外部业务信息。

### 需求 2：账务账户管理

**用户故事：** 作为财务管理员，我希望系统维护基础账务账户，以便账务凭证可以引用明确的会计科目。

#### 验收标准

1. THE Ledger_Account SHALL 支持账户编码、名称、类型、余额方向、归属主体和状态。
2. THE Ledger_Account SHALL 只允许 ACTIVE 状态账户被 Journal_Line 引用。
3. WHEN Ledger_Account 被停用, THE Ledger_Account SHALL 拒绝后续新增入账引用。

### 需求 3：复式记账凭证

**用户故事：** 作为财务管理员，我希望每笔财务动作都生成借贷平衡的凭证，以便账务数据可审计、可追溯。

#### 验收标准

1. THE Journal_Entry SHALL 包含至少两条 Journal_Line。
2. THE Journal_Entry SHALL 在过账前校验借方金额合计等于贷方金额合计。
3. WHILE Journal_Entry IN POSTED, WHEN 添加或修改 Journal_Line, THE Journal_Entry SHALL 拒绝该操作。
4. THE Journal_Line SHALL 使用非负金额并通过借贷方向表达账务影响。

### 需求 4：幂等入账

**用户故事：** 作为系统运维人员，我希望重复投递的业务事件不会重复入账，以便异步事件处理具备可靠性。

#### 验收标准

1. THE Source_Document SHALL 唯一标识一次业务入账来源。
2. WHEN Accounting_Event_Handler 收到已入账的 Source_Document, THE Journal_Entry SHALL NOT 被重复创建。
3. THE Journal_Entry SHALL 保存 Source_Document 以支持追溯。

### 需求 5：订单支付入账

**用户故事：** 作为财务管理员，我希望买家支付成功后记录平台代收款和商户待结算负债，以便不在支付时提前确认平台收入。

#### 验收标准

1. WHEN Accounting_Event_Handler 收到订单支付成功事件, THE Journal_Entry SHALL 借记支付渠道清算账户。
2. WHEN Accounting_Event_Handler 收到订单支付成功事件, THE Journal_Entry SHALL 贷记商户待结算账户。
3. THE Journal_Entry SHALL NOT 在支付成功时贷记平台佣金收入账户。

### 需求 6：订单完成佣金确认

**用户故事：** 作为财务管理员，我希望订单完成后确认平台佣金，以便收入确认发生在交易履约完成之后。

#### 验收标准

1. WHEN Accounting_Event_Handler 收到订单完成事件, THE Journal_Entry SHALL 借记商户待结算账户。
2. WHEN Accounting_Event_Handler 收到订单完成事件, THE Journal_Entry SHALL 贷记平台佣金收入账户。
3. THE Journal_Entry SHALL 使用订单或商户快照中的佣金金额生成分录。

### 需求 7：退款冲正

**用户故事：** 作为财务管理员，我希望退款批准后通过冲正凭证处理账务，以便保留原始入账记录并保持账务可审计。

#### 验收标准

1. WHEN Accounting_Event_Handler 收到退款批准事件, THE Reversal_Journal_Entry SHALL 引用原 Journal_Entry。
2. THE Reversal_Journal_Entry SHALL 使用相反借贷方向冲销原分录的退款部分。
3. THE Reversal_Journal_Entry SHALL NOT 修改原 Journal_Entry。

### 需求 8：商户结算

**用户故事：** 作为财务管理员，我希望系统按商户和账期生成结算单并记录打款凭证，以便完成平台代收款向商户的结算。

#### 验收标准

1. THE Settlement_Statement SHALL 按商户和结算周期汇总可结算订单、退款和佣金。
2. WHEN Settlement_Statement 被确认, THE Settlement_Statement SHALL 冻结结算金额和明细。
3. WHEN Settlement_Statement 标记已打款, THE Journal_Entry SHALL 借记商户待结算账户并贷记平台银行存款账户。

### 需求 9：会计期间控制

**用户故事：** 作为财务管理员，我希望关闭后的会计期间不再允许普通入账，以便月结后的账务不会被意外改变。

#### 验收标准

1. THE Accounting_Period SHALL 支持 OPEN 和 CLOSED 状态。
2. WHEN Journal_Entry 的 accountingDate 落入 CLOSED Accounting_Period, THE Journal_Entry SHALL 拒绝普通过账。
3. WHEN 需要调整 CLOSED Accounting_Period, THE Reversal_Journal_Entry SHALL 使用当前开放期间的 accountingDate。

### 需求 10：账户余额查询

**用户故事：** 作为财务管理员，我希望查询账户余额和发生额，以便核对账务状态。

#### 验收标准

1. THE Accounting_Balance_View SHALL 只统计 POSTED Journal_Entry 中的 Journal_Line。
2. THE Accounting_Balance_View SHALL 支持按 Ledger_Account、主体和日期范围查询借方发生额、贷方发生额和余额。
3. THE Accounting_Balance_View SHALL 能由 Journal_Entry 重算得到。
