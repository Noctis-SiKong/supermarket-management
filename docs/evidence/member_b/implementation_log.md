# B 骨架实现记录（auth / cui / stock）

版本/日期：v0.23，2026-10-21（含 §6–§29 增补、合并 main、CR07-10、IF-07、售后完整链、review 修复、IF-11/CR07-11、补货/报损与审计交接单 B-01～B-08 修复）
分支：`member-b/implementation`（基于 main `04abd6a`）
性质：模块骨架 + 测试替身；**非真实持久化、非联调、非 AC 验收**。仅证明接口边界、纯规则与单元测试可跑通。

## 1. 范围与文件

| 包 | 文件 | 职责 |
|---|---|---|
| `auth` | `Role`、`SessionRef`、`AuthResult`、`AuthService`、`CredentialStore`、`PasswordHasher`、`Sha256PasswordHasher`、`LocalAuthService` | IF-01 登录/会话/身份解析；凭证只读端口；三态结果 |
| `cui` | `InputValidator`、`CuiApp` | 输入格式/基础边界校验；最小登录/退出 CUI 壳 |
| `stock` | `Position`、`StockBalance`、`StockChangeLine`、`StockEntry`、`StockCheckResult`、`StockPorts`、`SubmissionGateway`、`StockOperations`、`StockValidator`、`StockService` | IF-07 余额读取、变动校验、候选流水；IF-05/04/06/03 的 B 侧适配端口 |
| 测试替身（`src/test/.../fixture`） | `FakeWorkingCopy`、`FakeProductCatalog`、`FakeIdGenerator`、`FakeSubmissionGateway`、`InMemoryCredentialStore` | 只读快照/商品/固定 ID 时钟/捕获式提交/内存凭证；均标"虚构/非生产" |
| 测试 | `LocalAuthServiceTest`(7)、`InputValidatorTest`(4)、`StockValidatorTest`(4)、`StockServiceTest`(4) | 登录/会话/输入边界/库存规则/候选组装 |

## 2. 实际构建与测试（2026-10-21）

- 环境：JDK 17.0.12、Maven 3.9.11（Wrapper 3.3.4）
- 命令：`./mvnw.cmd --batch-mode --no-transfer-progress clean verify`
- 结果：**BUILD SUCCESS**；`Tests run: 21, Failures: 0, Errors: 0, Skipped: 0`
  （框架冒烟 2 + auth 7 + cui 4 + stock 4 + stock 4）

## 3. 接口与设计决策（自我提问记录）

1. **如何不复制 A 公共类型？** 在 `stock.StockPorts` 定义 B 侧最小只读端口（WorkingCopyView/ProductCatalog/IdGenerator），提交能力用 `SubmissionGateway` 端口；A 的 `shared/` 代码交付后以适配器替换，不把 A 的类型复制进 B。
2. **如何保证 IF-07 不保存？** `StockService` 只读 `WorkingCopyView`、只返回 `StockCheckResult` 候选；没有提交/写文件入口。
3. **如何让规则可测？** `StockValidator` 是纯函数（输入变动组+余额，输出可选拒绝），不依赖工作副本；`StockService` 只做编排与候选组装。
4. **CUI 如何低耦合？** `CuiApp` 目前只依赖 `AuthService` 接口；业务菜单后续通过构造器注入各 `*Operations` 接口，不直接依赖具体实现。
5. **错误如何表达？** 用 sealed 三态（`AuthResult`、`StockCheckResult`、`SubmitOutcome`），区分 PREPARED/REJECTED/FAILED；不用 boolean/null。
6. **替身放哪？** 全部在 `src/test/.../fixture`，不进 `src/main`，不写真实 `data/`；无法证明持久化/恢复/AC。

## 4. 已知边界与未证明

- 未接 A 的 `shared/` 公共代码与 IF-04/05 实现；`SubmissionGateway` 是捕获式替身，**不证明原子提交/幂等/恢复**。
- `SessionRef` 为骨架类（包内构造），未实现会话过期/持久化；密码哈希用 SHA-256 教学演示，正式应换带盐 KDF。
- CUI 仅有登录/退出骨架，业务菜单、错误码呈现、退出路径的完整交互待接模块。
- 未实现 aftersale/query/reversal；IF-11 真实读取待 A 方案。
- 未执行任何正式 AC；21 项测试仅验证模块逻辑与边界。

## 5. 下一步

1. 实现 `stock` 补货/报损用例服务（依赖 IF-07 + IF-13 候选），用替身测试。
2. 实现 `aftersale` 纯计算（QuotaCalculator/QuotaViewBuilder/QualityPairValidator）与编排骨架。
3. 接 CUI 菜单（登录后分派到各 Operations 接口）。
4. A 交付 `shared/` 后适配真实 WorkingCopy/ChangeSet/IF-05，再做 J-01–J-07 联调与 AC 证据。

## 6. 2026-10-21 增补：stock 用例服务

- 新增：`ReplenishmentService`（补货 W−q/S+q 候选）、`LossService`（店内报损扣来源位置 + LossRecord 候选）、`ReplenishmentResult`、`LossResult`、`LossRecord`、`LossOrigin`、`LossState`。
- 测试：`ReplenishmentServiceTest`（4）、`LossServiceTest`（4）；累计 `clean verify` **29 项测试全部通过**（BUILD SUCCESS）。
- 边界：仍只返回候选，不提交（真实 ChangeSet 还需 A 的文档/行类型）；补货 transferKey 由 IF-03 替身生成；报损仅 STORE，顾客交回不在此处理；未执行 AC。
- 下一步：`aftersale` 纯计算（QuotaViewBuilder/QuotaCalculator/QualityPairValidator）+ CUI 菜单接入；A 交付 shared 后做真实联调。

## 7. 2026-10-21 增补：aftersale 纯计算

- 新增模型：`OriginalSaleLine`、`Redemption`、`RedemptionKind`、`Effect`、`Replacement`、`QuotaView`、`SettlementContext`。
- 新增纯计算：`QuotaViewBuilder`（CONSUME 汇总、RELEASE 扣减、非法历史拒绝）、`QuotaCalculator`（P×q/Q HALF_UP、剩余金额封顶、结清用全部剩余）、`QualityPairValidator`（质量零差配对的条码/数量/差额/quality 校验）。
- 测试：`QuotaCalculatorTest`(5)、`QuotaViewBuilderTest`(3)、`QualityPairValidatorTest`(5)；累计 **42 项测试全部通过**（BUILD SUCCESS）。
- 边界：纯函数/模型，不读 A 代码、不提交、不执行 AC；`QuotaView` 一致性约束仍待后续与真实历史数据联调验证。

## 8. 2026-10-21 独立审查与修复

- 独立审查（thinking=high 子智能体）报告：`docs/evidence/member_b/independent_review_2026-10-21.md`（快照 S0=`6c272ca`、S1=`d1ec5b1`；实测 29 项通过）。报告 P0：无可运行 CUI、售后/冲销/查询零实现（S1 时点）、V2 与实现不同步；P1：IF-07 v0.3 文档两套输出/错误码、`eventKey` 每次重算、`receiptReviewId` 缺失、`INTERNAL_ERROR` 非规范码等。
- 已修复（本快照）：`StockChangeLine`/`StockEntry` 增加 `eventKey`（可稳定传入）与 `receiptReviewId`；`StockService` 使用调用方 eventKey、传递 receiptReviewId；内部异常码改为 B 登记码 `STOCK_CHECK_FAILED`；`changes==null` 返回 `INVALID_INPUT`；IF-07 §4/§5 加 v0.3 覆盖提示、§14.5 登记 `STOCK_CHECK_FAILED`、证据补 #8；`design_review.md` 补 R-04 覆盖说明；`design_evolution.md` §3.1 关闭"待确认"。
- 修复后 `clean verify`：**43 项测试全部通过**（新增 stable eventKey/receiptReviewId 测试）。
- 仍未处理（需决策/协作）：可运行 CUI 主类与 pom exec（A 的 Maven 框架；A shared 已合 main，B 适配中）；售后编排/查询/冲销服务；V2 与实现同步；`baseLast` 过期检测；权限/经办字段；README 状态行（A 维护）。

## 9. 2026-10-21 增补：售后编排骨架

- 新增：`AftersaleCommand`、`AftersaleCommandLine`、`AftersaleResult`、`AftersaleAssembler`、`AftersaleService`；端口 `OriginalSaleReader`、`QuotaLedgerReader`、`ProductSnapshot`、`ProductSnapshotReader`。
- 流程：验证重复原行 → 读原行（IF-11 替身）→ 简化资格（30 天 / 已开封非质量；CR07-09 店长例外）→ QuotaView/QuotaCalculator → Redemption/Replacement → 替换品 IF-07 核对；不保存/不提交。
- 测试：`AftersaleServiceTest`(9)；累计 **52 项测试全部通过**（BUILD SUCCESS）。
- 边界：同一原行一次命令限一条明细；30 天按 Asia/Shanghai 日历日；`AFTERSALE_FAILED` 为 B 暂用码待通知 A 登记；未记录款项/顾客交回损耗；未执行 AC。

## 10. 2026-10-21 增补：V2.1 同步 + CUI 菜单

- V2.1：新增 `docs/design/member_b/v2_1/class_v2_1.mmd`（当前实现快照，不是目标全图）与 `README.md`（V2 目标→实现差异表、AC-06 报告口径）；PNG 待 B 用 mermaid.live 渲染。
- CUI：`CuiApp` 重构为登录 + 角色菜单（补货/报损/售后/退出），只依赖 `Operations` 接口；新增 `CuiActor`、`OperationFeedback`、`Operations`、`UnavailableOperations`。未接真实 A 适配时 `UnavailableOperations` 明确返回"依赖未交付"，不伪造库存/提交结果。
- 测试：`CuiAppTest`(4)；累计 **56 项测试全部通过**（BUILD SUCCESS）。
- 边界：仍无 `main`/pom exec（A 的 Maven 框架）；A shared 已合 main；CUI 真实业务适配器与 IF-04/05 适配进行中；V2.1 图尚未渲染；未执行 AC。

## 11. 2026-10-21 增补：V2.1 分图源文件

- 新增 `docs/design/member_b/v2_1/split/`：`00_overview.mmd`（总览）、`01_auth_cui.mmd`、`02_stock.mmd`、`03_aftersale.mmd`。
- `02_stock.mmd` 已补 `LossOrigin`/`LossState` 的 `<<enumeration>>` 记号；四张图均只显示类/接口/依赖，不含字段/方法。
- `v2_1/README.md` §5 登记图 A–D、PNG 命名（`A_overview.png` 等）、渲染后核对 `RedemptionKind` 裁切与 auth/cui 空白裁切。
- 边界：PNG 仍由 B 渲染，未入库；未执行 AC；图注必须注明"结构快照"。

## 12. 2026-10-21 增补：query / reversal 骨架

- `query`：`QueryOperations`、`QueryService`、`QueryResult`、`SaleLineView`；只读原行视图、额度视图、核销历史；缺失返回 `NOT_FOUND`，读取异常返回 `QUERY_FAILED`。
- `reversal`：`ReversalService`、`ReversalPlan`、`ReversalCommand`、`ReversalResult`，端口 `RedemptionReader`、`ReversalDependencyReader`、`StockEffectReader`；店长/管理员权限、D1 依赖阻断、重复冲销 `EVENT_CONFLICT`、生成 RELEASE 核销 + 反向库存候选；不提交。
- 新增 B 业务码 `QUERY_FAILED`、`DEPENDENCY_BLOCKED`、`REVERSAL_FAILED`，待通知 A 登记；D1 只做阻断，不级联重建；款项/顾客交回未处理。
- 测试：`QueryServiceTest`(5)、`ReversalServiceTest`(7)；累计 **68 项测试全部通过**（BUILD SUCCESS）。
- 边界：权限范围/真实 A 数据未接入；V2.1 图尚未包含 `query`/`reversal` 新包；未执行 AC。

## 13. 2026-10-21 增补：独立可完成规则收口

- 售后：曾按 CR07-09 增加例外字段；2026-10-21 已按 CR07-10 迁移（见 §14），删除例外字段，改为 reasonKind/reasonText；支持同一原行多条明细的顺序额度扣减；新增 `SettlementCalculator`/`Settlement`/`SettlementDirection`，输出退款/补收/无差候选并接入 `AftersaleResult.Prepared`。
- 报损：`LossRecord.dispose(String)` 将状态转为 DISPOSED，保留原字段；新增测试。
- 查询：新增 `LossRecordView`、`LossRecordReader`，`QueryService` 支持按 ID/条码查损耗记录；补 4 项测试。
- 冲销：新增 `DependencyChecker`（直接/传递依赖与环检测）；补 4 项测试。
- CUI：补非法位置与换货质量事实分派测试。
- 测试：`clean verify` **84 项全部通过**；AC 索引见 `docs/evidence/member_b/ac_coverage_b.md`。
- 边界：仍用替身；D1 只阻断不级联；顾客交回损耗记录器、真实 A 数据/权限未接；未执行正式 AC。

## 14. 2026-10-21 合并 main + CR07-10 迁移

- Stage 1：在隔离 worktree `supermarket-management-merge` 将 `origin/main`（a109135）合入 B，merge commit `ef5eea1`；无文本冲突；合并树 `clean verify` = **118 tests / 0 failed / 0 error / 1 skipped**（A 36 + B 84 共享 FrameworkSmokeTest 2 项，实际 118）。
- Stage 2：按 CR07-10 迁移售后：删除 `AftersaleCommand`/`Redemption` 的店长资格例外字段；`AftersaleCommandLine` 增 `reasonKind(QUALITY/OTHER)+reasonText`；`AftersaleService` 改为严格资格（超 30 天、已开封非质量一律拒绝，无店长例外）；CUI 增加“其他原因”文本提示；新增/改写测试后合并树 **120 tests / 0 failed / 0 error / 1 skipped**。
- 文档：本条与后续文档修正同步；CR07-09 仅保留为历史记录，现行口径为 CR07-10。


## 15. 2026-10-21 Stage 3a：IF-07 真实适配

- 新增 `SharedWorkingCopyView`：把 A `WorkingCopy` 投影为 B 的 IF-07 只读余额视图；`SharedStockProjection` 通过 `STOCK_ENTRY.sourceLineId → DOC_LINE.barcode` 聚合余额。
- 新增 `SharedStockAdapter`：实现 A `AOperations.StockDependency` 的 `prepare/review/validateRecovered`；接受 A `shared.StockEntry` 组，用 B `StockValidator` 校验后返回仅新增 `STOCK_ENTRY` 的 `ChangeSet`，不合并 overlay/不提交。
- 测试：`SharedStockAdapterTest` 5 项；合并树累计 **125 tests / 0 failed / 0 error / 1 skipped**（BUILD SUCCESS）。
- 边界：`Result` 仅做直接映射；`STOCK_ENTRY` 只支持 inserts；提交/CommitValidator/认证/CUI 真实适配未做。

## 16. 2026-10-21 Stage 3b：六参 CommitValidator 与草稿删除

- 新增 `AftersaleDraftDeletionValidator`：实现 A `SubmissionPort.CommitValidator` 六参签名；校验 `AFTERSALE_DRAFT_DELETE` 的目标/草稿状态/创建者权限/读取追溯/生效影响/删除组形状；A `DraftDeletionGuard` 继续负责结构红线。
- 新增 `SharedStockCommitValidator`：六参包装 `SharedStockAdapter.review`，供 B 的库存相关操作注册。
- 测试：`AftersaleDraftDeletionValidatorTest` 5 项；合并树累计 **130 tests / 0 failed / 0 error / 1 skipped**（BUILD SUCCESS）。
- 边界：校验器尚未注册到最终 B 装配；B 售后/冲销仍未产出完整 ChangeSet；认证/CUI 真实适配未做。

## 17. 2026-10-21 Stage 3c：售后 ChangeSet 与提交入口

- 新增 `AftersaleChangeSetAssembler`：把 `AftersaleResult.Prepared` 组装为 A `ChangeSet`（DOCUMENT/AFTERSALE/DOC_LINE/DOC_EVENT/REDEMPTION/REPLACEMENT/STOCK_ENTRY/MONEY_ENTRY）；库存条目映射为 A `shared.StockEntry`，commitOrdinal=0。
- 新增 `AftersaleSubmissionService`：`lookupResult` 幂等检查 → B 售后候选 → ChangeSet → A `SubmissionPort.submit`；不嵌套 submit、不写文件。
- 测试：`AftersaleChangeSetAssemblerTest`(1)、`AftersaleSubmissionServiceTest`(1)；合并树累计 **132 tests / 0 failed / 0 error / 1 skipped**（BUILD SUCCESS）。
- 边界：B `AftersaleService` 仍用 B 库存/候选端口；`AFTERSALE` 六参 validator 尚未注册；认证/CUI 真实适配未做。

## 18. 2026-10-21 Stage 3d：AFTERSALE 校验器与注册表

- 新增 `AftersaleCommitValidator`：六参复核 AFTERSALE 单据/核销/替换品一致性、CR07-10 原因文本、库存候选；`documents` 必须唯一且 target/confirmedBy 匹配。
- 新增 `BCommitValidators`：注册 `AFTERSALE` 与 `AFTERSALE_DRAFT_DELETE` 校验器，供装配层交给 A `JsonStore`。
- 修正 `SharedStockAdapter.review`：无 STOCK_ENTRY 时返回成功（纯退货/无库存变更），不再被 B `StockValidator` 的空组拒绝。
- 测试：`AftersaleCommitValidatorTest` 4 项；合并树累计 **136 tests / 0 failed / 0 error / 1 skipped**（BUILD SUCCESS）。
- 边界：注册表尚未实际传给 A JsonStore；B 售后 submission 测试仍用 capturing SubmissionPort；认证/CUI 未接。

## 19. 2026-10-21 Stage 3e：认证适配与真实 JsonStore open

- 新增 `SharedIdentityResolver`：B `AuthService`/`SessionRef` → A `OperationContext.IdentityResolver`/`ActorContext`，角色枚举映射。
- 新增 `SharedCredentialStore`：A `JsonStore.authenticationView()` → B `CredentialStore` 只读适配。
- 新增 `SharedAuthJsonStoreTest`：临时项目根 + A `DataRoot`/`JsonStore` + B `LocalAuthService`，验证 B 会话可被 A `open` 接受（baseLast=0）。
- 测试：`SharedIdentityResolverTest`(2)、`SharedCredentialStoreTest`(2)、`SharedAuthJsonStoreTest`(1)；合并树累计 **141 tests / 0 failed / 0 error / 1 skipped**（BUILD SUCCESS）。
- 边界：尚未做统一 `Main`/pom 入口与 CUI 真实 `Operations`；B 服务装配仍用内存/替身端口。

## 20. 2026-10-21 Stage 3f：售后读取适配

- 新增 `SharedSaleReader`：从 A `WorkingCopy` 的 `DOCUMENT/DOC_LINE/SALE_LINE` 读取 `OriginalSaleLine`。
- 新增 `SharedQuotaLedgerReader`：从 A `REDEMPTION` 读取核销历史。
- 新增 `SharedProductSnapshotReader`：从 A `PRODUCT` 读取商品名称/售价快照。
- 新增 `SharedAftersaleReadTest`：真实 A `WorkingCopy` 数据驱动 B 售后读取与 `AftersaleService` 候选（2 项）。
- 测试：合并树累计 **143 tests / 0 failed / 0 error / 1 skipped**（BUILD SUCCESS）。
- 边界：CUI `Operations`/`Main` 装配仍未做；B 售后真实提交路径仍使用 capturing `SubmissionPort` 测试。

## 21. 2026-10-21 Stage 3g：BApplication 装配与真实 JSON 提交

- 新增 `BApplication`：组合 `DataRoot/JsonStore` + `SharedIdentityResolver` + `SharedCredentialStore` + A `AOperations` 校验器 + B `BCommitValidators` + `SharedStockAdapter` 完整性检查。
- 新增 `BApplicationTest`：临时项目根、真实 JSON 写入：B 登录 → A `open`（AFTERSALE target）→ 售后 ChangeSet + PRODUCT → A `submit` → `lookupResult` replayed。
- 测试：合并树累计 **144 tests / 0 failed / 0 error / 1 skipped**（BUILD SUCCESS）。
- 边界：CUI 真实 `Operations` 与 `Main`/pom 尚未接入；A 的 `DraftDeletionGuard` 仍由 A 执行；B 售后提交仍以手工 Prepared 构造测试。

## 22. 2026-10-21 Stage 3h：CUI 真实操作与售后提交链

- `CuiActor` 增加 `SessionRef`；`CuiApp` 登录后把 B 会话传给 CUI 操作。
- 新增 `SharedOperations`：售后执行 `A JsonStore.open` → `AftersaleService`（Shared readers）→ `AftersaleSubmissionService` → A `submit`；补货/报损明确返回“尚未接线”。
- 新增 `SharedProductCatalog`、`SharedIdGenerator`；`BApplication.operations()` 暴露 CUI 操作端口。
- `AftersaleService` 增加带 `aftersaleId` 的重载；`AftersaleSubmissionService` 使用 `op.targetId()`，使 A 的 open/submit 目标一致。
- 测试：`SharedIdGeneratorTest`(1)、`SharedProductCatalogTest`(1)；合并树累计 **146 tests / 0 failed / 0 error / 1 skipped**（BUILD SUCCESS）。
- 边界：CUI 还没有 `Main`/pom 入口，尚未把 `BApplication.operations()` 接到实际菜单启动；补货/报损 ChangeSet 未接线；售后完整 CUI 路径缺少带种子数据的集成测试。

## 23. 2026-10-21 步骤一：CUI 售后完整链

- 新增 `SharedOperationsIntegrationTest`：A 侧真实种子（PRODUCT_PUT/OPENING/SALE）→ B `SharedOperations.aftersale` → A `submit` → 重开读取 `REDEMPTION`。
- 修正 `SharedStockAdapter.review`：先把同一 ChangeSet 的非库存插入叠加到基线，再解析 `STOCK_ENTRY.sourceLineId`；解决“DOC_LINE expected”问题（A 的 OPENING/SALE 先插入 DOC_LINE）。
- 测试：`SharedOperationsIntegrationTest`(1)；合并树累计 **147 tests / 0 failed / 0 error / 1 skipped**（BUILD SUCCESS）。
- 边界：CUI `Main`/pom 启动入口仍由 A 的 Maven 框架决定；补货/报损真实 ChangeSet 未接；冲销/query 未接。

## 24. 2026-10-21 代码审查修复（第一轮）

- **换货 CUI**：新增 `AftersaleRequest` 收拢售后输入；CUI 在 EXCHANGE 时必填替换品条码/数量；`Operations.aftersale` 改传完整请求；CUI 对操作异常有兜底提示，不再让 `AftersaleCommandLine` 异常冒泡。
- **款项方向**：`AftersaleChangeSetAssembler` 改为 REFUND 负、CHARGE 正；测试断言 `MONEY_ENTRY.amountSigned == -3.33`。
- **校验器意图/时间**：`AftersaleCommitValidator` 使用原始 `BusinessRequest` 校验 requestKey、原销售行与数量；用 `acceptedAt` 按 Asia/Shanghai 校验 30 天窗口（基线有原单时）。
- **核销行**：`AftersaleAssembler` 的 `sourceLineId` 改为本次核销行 ID，不再重复原销售行。
- **装配**：`BApplication` 注入 `IdClock` 并传给 `SharedOperations`；角色映射统一到 `SharedRoles`。
- 测试：合并树累计 **147 tests / 0 failed / 0 error / 1 skipped**（BUILD SUCCESS）。
- 未修：IF-11 读取追溯/CR07-11 草稿取消、幂等重试通道、恢复校验覆盖、ERD 数据形状、死代码清理。

## 25. 2026-10-21 代码审查修复（第二轮）

- **幂等重试**：`AftersaleRequest` 增加可选 `requestKey`；`SharedOperations` 使用同一 requestKey 重试时走 `lookupResult`，集成测试连续两次调用只产生 1 条 REDEMPTION。
- **恢复校验**：新增 `AftersaleIntegrityValidator`，检查 AFTERSALE/REDEMPTION/REPLACEMENT/MONEY_ENTRY 的文档与核销关联、数量/金额/来源行；接入 `BApplication` 完整性链。
- **数据形状**：AFTERSALE 扩展不再写多余 status/requestKey；DOC_EVENT 动作改为 `CONFIRM`。
- 测试：新增 `AftersaleIntegrityValidatorTest`(2)；合并树累计 **149 tests / 0 failed / 0 error / 1 skipped**（BUILD SUCCESS）。
- 未修：IF-11 `READ_FOR_AFTERSALE` 读取追溯、CR07-11 草稿创建/取消编排。

## 26. 2026-10-21 IF-11 读取追溯与 CR07-11 草稿生命周期

- 新增 `AftersaleDraftOperations`、`AftersaleDraftService`：DRAFT 创建、READ_FOR_AFTERSALE 追溯、CANCEL_AFTERSALE 取消，均独立 open/submit。
- 新增 `AftersaleDraftValidator` 并注册到 `BCommitValidators`；创建/读取/取消分别校验 DRAFT 状态、追溯存在、取消只改状态+事件且无库存/退款/额度影响。
- `SharedOperations.aftersale` 流程改为：创建草稿 → 读原行并保存追溯 → 重新 open → 提交售后；最终 ChangeSet 对 DRAFT 做 DOCUMENT 补丁并追加业务事实。
- `AftersaleCommitValidator` 支持已有 DRAFT 的最终提交，要求已保存 READ_FOR_AFTERSALE；`AftersaleSubmissionService` 自动选择 draft 组装。
- 同一 requestKey 重试先查最终结果，避免重复建草稿/售后。
- 测试：新增 `AftersaleDraftServiceTest`(1)；合并树累计 **150 tests / 0 failed / 0 error / 1 skipped**（BUILD SUCCESS）。
- 边界：CUI 尚未暴露取消菜单；A 侧对 IF-11 跨人读取的授权/返回准入仍需 A 接口最终对齐。

## 27. 2026-10-21 CUI 取消入口与 A 对齐

- CUI 主菜单新增“取消售后草稿”；`Operations.cancelAftersaleDraft` 经 `AftersaleDraftService.cancel` 提交 CANCELLED + CANCEL_AFTERSALE，CUI 测试覆盖草稿 ID 分派。
- 新增 `docs/coordination/B_TO_A_IF11_ALIGNMENT_2026-10-21.md`，就 IF-11 读取准入、事件形状、DRAFT 最终提交、取消/删除边界向 A 提明确确认请求，不把推断当 A 接口。
- 测试：`CuiAppTest` 新增取消分派 1 项；合并树累计 **151 tests / 0 failed / 0 error / 1 skipped**（BUILD SUCCESS）。
- 未声称：A 侧 IF-11 接口已交付、真实跨人授权联调、AC 验收。

## 28. 2026-10-21 补货/报损真实 ChangeSet

- 新增 `StockChangeSetAssembler`：补货 DOCUMENT/DOC_LINE/DOC_EVENT + 两条平衡库存腿；报损 DOCUMENT/DOC_LINE/LOSS_RECORD/DOC_EVENT + 负库存腿。
- 新增 `StockOperationValidator` 并注册 `REPLENISHMENT`/`LOSS`：校验单据/行、库存腿平衡、来源行、报损事实与禁止售后事实。
- `SharedOperations.replenish`/`recordLoss` 改为真实 `StockService` 候选 → ChangeSet → A `submit`，不再返回“未接线”。
- 新增 `StockOperationsIntegrationTest`：A 种子商品/期初 → B 补货 3 → B 报损 2 → 真实 JSON 状态（LOSS_RECORD=1、STOCK_ENTRY=5）。
- 测试：合并树累计 **152 tests / 0 failed / 0 error / 1 skipped**（BUILD SUCCESS）。
- 边界：IF-11 读取准入仍待 A 对齐；AC 尚未执行。

## 29. 2026-10-21 B 分支审计交接单 B-01～B-08

- B-01：`StockOperationValidator` 使用原始 request 与 actor 复核条码/数量/位置/角色；拒绝补丁、删除和允许表外记录（补货仅 CLERK/MANAGER，报损仅 MANAGER）。
- B-02：`AftersaleIntegrityValidator` 不再拒绝金额为 0 的 `MONEY_ENTRY`；零价销售/零金额核销测试通过。
- B-03：`AftersaleCommitValidator` 用 List 保留同原行多明细，逐条比对类型/数量/开封/质量/原因/来源行/替换品，拒绝漏项、重复与夹带。
- B-04：`AftersaleService` 提交内先按“换货优先 + 稳定内容键”排序，再生成/分配核销，降低输入顺序影响。
- B-05：原销售行缺失、非 SALE、未确认、无 confirmedAt 一律拒绝，不再 `continue` 放行。
- B-07：取消按钮派生稳定 requestKey；`AftersaleDraftService.cancel` 先 lookupResult，移除客户端 `cancelledAt`，同键重试返回既有结果。
- B-08：草稿读取/取消校验创建者归属；读取事件 detail 的原销售行必须存在且属于已确认 SALE。
- 测试：新增 `StockOperationValidatorTest` 3 项、零金额/取消重试断言；合并树 **156 tests / 0 failed / 0 error / 1 skipped**（BUILD SUCCESS）。
- 未修：B-06 IF-11 正式受控读取端口待 A；B-09 草稿删除无生产/CUI 路径；B-10 冲销仍骨架；A-01/A/B-01 归 A。

## 30. 2026-10-21 审计回归测试补充

- 新增 `StockOperationValidatorTest`：合法补货、篡改数量、夹带 PRODUCT 三项。
- 新增 `AftersaleCommitValidatorTest`：同原行多核销保留、原销售行缺失拒绝。
- 新增 `AftersaleServiceTest`：同一业务输入打乱顺序后金额/结算结果一致。
- 新增零金额 `MONEY_ENTRY`、取消同键重试、草稿读取需真实已确认 SALE 等断言。
- 合并树累计 **159 tests / 0 failed / 0 error / 1 skipped**（BUILD SUCCESS）。
- 边界：B-04 的 AC-79 尾差 3.33/3.33/3.34 尚未逐项复现；B-06/B-09/B-10 仍未完成。
