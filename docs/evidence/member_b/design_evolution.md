# B 范围类图 V1→V2 设计演进记录

版本/日期：v1，2026-10-20（Asia/Shanghai）
依据：`docs/evidence/member_b/design_review.md` §7 的 B 本人逐条决定（29 条）、V1 类图 `docs/design/member_b/class_v1.mmd`
产物：`docs/design/member_b/class_v2.mmd`（V2 源文件；PNG 待渲染）
性质：记录 V1→V2 的实质变化、决定依据与未决项；不代替代码实现或 AC 结果。

## 1. 决定汇总（来自 B 本人，§7）

| 决定 | 数量 | 条目 |
|---|---:|---|
| 采纳 | 20 | R-01、R-02、R-05、R-06、R-07、R-08、R-09、R-10、R-11、R-12、R-13、R-14、R-17、R-18、R-19、R-20、R-22、R-24、R-26、R-28 |
| 修改后采纳 | 7 | R-03、R-15、R-16、R-21、R-23、R-25、R-29 |
| 拒绝 | 2 | R-04（售后资格/期限策略）、R-27（图面可读性） |

说明：R-28 原文记"采用"，按采纳处理；R-04 拒绝仅指**不新增复杂策略类**，不代表取消系统对 30 天/开封/质量规则的校验（见 §4 风险提示）。

## 2. V1→V2 实质变化

| # | 位置 | V1 | V2 | 对应决定 | 影响 |
|---|---|---|---|---|---|
| 1 | `AftersaleService` | `returnItems`/`exchange` 两方法，一次一动作 | `apply(AftersaleCommand)`：命令含明细列表，逐明细 opened/quality；先换后退由服务编排 | R-01、R-29 | AC-81/82 |
| 2 | 新增 `AftersaleCommand`、`AftersaleCommandLine` | 无 | 售后命令与逐行明细（退/换、资格、替换品、质量配对） | R-01、R-05 | AC-81/82 |
| 3 | 新增 `QuotaLedgerReader` | 无历史核销读取 | 只读端口：按原行读已提交有效 CONSUME/RELEASE | R-02 | AC-46/56–59/79–82 |
| 4 | 新增 `QuotaViewBuilder`；`QuotaView` 增加 remainingQty/remainingAmount | QuotaView 无来源、无剩余字段 | 原行快照 + 台账 → 额度视图 | R-02 | AC-46/56–59 |
| 5 | `AftersaleService` 依赖 `StockService` | 换货无库存链路 | 替换品 `S−q` 进入同一工作副本；**只从在售扣，不足拒绝并返回 S/W 余额提示，不用在仓抵扣** | R-03（修改后采纳） | AC-14/18/34/56 |
| 6 | 未新增 `EligibilityPolicy` | — | 期限/资格校验以 AftersaleService 内简单校验实现（不抽策略类） | R-04 拒绝 + R-29 冲突裁决 | AC-19/20/33/47（见 §4） |
| 7 | `QualityPairValidator` 入参增加原条码；`Redemption` 增 `originalBarcode` | 校验缺原条码/数量/差额 | 先生成 Redemption（含原行信息）再生成 Replacement，最后校验"条码同/数量同/差额0/quality=true" | R-05 | AC-58/82 |
| 8 | 新增 `CustomerReturnLossRecorder` | 顾客交回复用 `LossService`（position 必填） | 独立记录器：无 position、无 STOCK_ENTRY、origin=CUSTOMER | R-06 | AC-15/18 |
| 9 | `Redemption` 增 `Effect`（CONSUME/RELEASE）、`RedemptionKind`（RETURN/EXCHANGE） | 仅 kind 一个字段 | 业务类型与账务方向分离；RELEASE 必须带 `reverseOfId` | R-07 | AC-35/56–59 |
| 10 | `Replacement` 增 `redemptionId`、`quantity`、名称/单价快照 | 仅 qualityRedemptionId | 普通换货可追原核销；特殊配对仍用 qualityRedemptionId；补 ERD 快照字段 | R-08、R-26 | AC-34/82 |
| 11 | `ReversalService` 增 `AftersaleLinkReader` 依赖；新增 `Reversal` 记录 | 无售后关联检查 | 冲销前检查原单无未解除售后；REVERSAL 记录授权人/原因/时间 | R-09、R-18 | AC-35/52/59/83/84 |
| 12 | 新增 `SettlementContext`；`QuotaCalculator.allocate(qty, settle, view)` | 缺结清/排序上下文 | 支持"结清用剩余金额"与当前提交同类排序 | R-10 | AC-46/79/80 |
| 13 | `RecordQueryService.query(filter, session)` | scope 由调用方传入 | 角色从 Session 派生，不接受外部指定 ALL | R-11 | AC-10/28/87 |
| 14 | `StockEntry` 增 `commitOrdinal`、`reverseOfId`、`receiptReviewId` | 缺 D1/D2 字段 | 支撑提交序、反向配对、收货审核来源 | R-12 | AC-52/59/83/84/85 |
| 15 | 新增 `SubmissionRequest`、`ChangeSet`；`SubmissionPort.submit(request)` | `submit(changeSet)` 未定义 | 请求键/baseLast/经办/指纹/变更清单显式化 | R-13 | IF-05；AC-26/57/59/85 |
| 16 | 新增 `cui`/`auth`：`CuiApp`、`AuthService`、`Session`、`Role`、`InputValidator` | 无入口层 | 登录、角色、输入格式校验、菜单分派 | R-14、R-22 | AC-05/51/86 |
| 17 | 新增 `DocumentHeader`、`DocumentLine`（待 A 确认 common 类型） | 无单据头/行 | 补货/报损/售后/冲销的业务单据载体 | R-15（修改后采纳） | ERD §3–4 |
| 18 | `OriginalSaleReader` 增 `locateByReceipt(voucher)` | 只有 readLine(saleLineId) | 支持售后专用凭证定位；追溯写入仍经 IF-05；语义待 A 的 IF-11 方案 | R-16（修改后采纳） | AC-87 |
| 19 | `DependencyChecker.check(originalDocument, committedState)` + `DependencyResult` | `hasLaterNegative(originalEntries, laterEntries)` | D1 筛选规则收进检查器/契约，返回阻断明细 | R-17 | AC-52/59/83/84 |
| 20 | `OperationResult` + `Rejection` 统一 | 仅 stock 有 Rejection | 所有业务返回成功/拒绝/失败三态，供 CUI 呈现 | R-19 | AC-05 |
| 21 | `SubmissionRequest.requestKey` + `DocumentHeader.requestKey` | 只有明细级 requestKey | 文档级幂等；同键同内容返回既有结果 | R-20 | AC-26/57/59 |
| 22 | 实体 ID 职责（设计说明） | 不明确 | 领域 ID 由服务经 `IdClock` 生成；IF-05 只分配 `commitOrdinal`；请求键绑定（待 A 对齐） | R-21（修改后采纳） | AC-31/57/79 |
| 23 | `StockBalance` + `StockCheckResult.totalsAfter` | `Map<String,Integer>` 不分位置 | 按 (barcode, position) 返回余额，同时保留合计显示逻辑 | R-23（修改后采纳） | AC-22/30 |
| 24 | `LossRecord` 增 sourceLineId/dispositionState；`LossCorrection` 增 documentId/beforeAfter/operator/time | 缺 ERD 字段 | 损耗可追溯、修正审计完整 | R-24 | AC-37 |
| 25 | `MoneyEntry` 增 `documentId`；归属待 A 确认 | 缺 document 关联 | 作为共享款项类型由 common/A 提供（待确认） | R-25（修改后采纳） | AC-59 |
| 26 | 新增 `BusinessTransaction` 抽象类；六个写服务继承 | 服务全为具体类 | 模板方法：validate→assemble→submit；真实复用提交/幂等/错误处理 | R-28 | TC-03；AC-06 |
| 27 | 图面排版 | 32 类单图 | 未改（拒绝 R-27）；报告按横向整页处理 | R-27 拒绝 | 报告呈现 |

## 3. 关键裁决记录

1. **R-04 与 R-29 冲突**：R-04 拒绝"售后资格/期限策略类"，R-29 修改后采纳"抽 EligibilityPolicy 和 AftersaleAssembler"。
   - 本版按 **R-04 优先**：V2 不新增 `EligibilityPolicy`；30 天窗口/开封/质量规则以 `AftersaleService` 内简单校验方法实现（仍由系统拦截，见 §4）；R-29 只落地 `AftersaleAssembler` 与 `QuotaViewBuilder`。
   - 待 B 确认；若改为保留策略类，则需同步修改本节与 §7 决定记录。
2. **R-03 解读**：替换品**只从在售（S）扣减**；在售不足时拒绝并返回 S/W 余额用于提示补货，**不允许用在仓（W）直接抵扣**（符合 FR-08/AC-13 的精神）。"不足再查看库存"按"查看余额、提示补货"理解，不是从 W 抵扣。
3. **R-23 解读**：返回按位置的余额列表，同时保留 `totalsAfter` 合计字段（满足"要有总余额显示逻辑"）。
4. **R-15/R-16/R-21/R-25**：涉及 A 的 common/IF 契约，V2 先用占位类型/方法建模，待 A 确认后对齐；不改变已批准业务规则。

## 4. 风险提示（需 B 确认）

- **R-04 拒绝的口径**：冻结基线仍要求系统执行 30 天本地日历、开封/质量资格与超期质量例外（AC-47/19/20/33/34）。因此"拒绝 R-04"只能理解为**不抽复杂策略类**，简单校验必须保留，否则相关 AC 无法通过。若 B 本意是"全部人工处理"，与冻结 AC 冲突，需要按 Spec §17 变更或重新考虑。
- **PNG 未生成**：`class_v2.mmd` 需 B 在 mermaid.live 渲染并保存 `class_v2.png`（报告 Figure 3 使用）。
- **V2 规模**：约 50 个类/接口/枚举，单图较宽；报告可横向整页或拆"业务核心 + 入口/接口"两张；图面可读性（R-27）已拒绝，不再改图。

## 5. 待 A 提供/确认的输入（不阻塞 V2 设计，阻塞编码）

| 项 | 用途 | 当前状态 |
|---|---|---|
| IF-03 稳定 ID/时钟契约 | 实体 ID 生成、排序 | 未取得 |
| IF-04 快照读取/恢复 | baseLast、CommittedState 读取 | 未取得 |
| IF-05 统一提交契约 | SubmissionRequest/幂等/失败可见性 | 未取得 |
| IF-06 商品只读快照 | 商品/条码查询 | 未取得 |
| IF-11 原销售读取+追溯方案 | locateByReceipt/读取追溯/失败取消语义 | 未取得 |
| IF-16 DATA_ROOT | 附件/启动恢复（B 间接） | 未取得 |
| common 单据头/行类型 | DocumentHeader/DocumentLine | 未取得 |
| MONEY_ENTRY 归属 | MoneyEntry 类型归属 | 未取得 |

## 6. 下一步

1. B 渲染 `class_v2.png` 并发回核对（类/关系/包分组）。
2. A 确认 IF-07 契约 §10 与上述 A 侧输入；B 记录确认结果。
3. V2 冻结后进入 G-06 模块实现（B 三包先纯计算，再接 CUI/认证）；按交接包 §8 交 A 集中审计。
4. 报告 §3.5/§3.6 使用 V2 + 本演进表。
