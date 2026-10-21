# B 骨架实现记录（auth / cui / stock）

版本/日期：v0.5，2026-10-21（含 §6–§11 增补与独立审查修复记录）
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
- 仍未处理（需决策/协作）：可运行 CUI 主类与 pom exec（A 的 Maven 框架/A 侧 shared 未交付）；售后编排/查询/冲销服务；V2 与实现同步；`baseLast` 过期检测；权限/经办字段；README 状态行（A 维护）。

## 9. 2026-10-21 增补：售后编排骨架

- 新增：`AftersaleCommand`、`AftersaleCommandLine`、`AftersaleResult`、`AftersaleAssembler`、`AftersaleService`；端口 `OriginalSaleReader`、`QuotaLedgerReader`、`ProductSnapshot`、`ProductSnapshotReader`。
- 流程：验证重复原行 → 读原行（IF-11 替身）→ 简化资格（30 天 / 已开封非质量；CR07-09 店长例外）→ QuotaView/QuotaCalculator → Redemption/Replacement → 替换品 IF-07 核对；不保存/不提交。
- 测试：`AftersaleServiceTest`(9)；累计 **52 项测试全部通过**（BUILD SUCCESS）。
- 边界：同一原行一次命令限一条明细；30 天按 Asia/Shanghai 日历日；`AFTERSALE_FAILED` 为 B 暂用码待通知 A 登记；未记录款项/顾客交回损耗；未执行 AC。

## 10. 2026-10-21 增补：V2.1 同步 + CUI 菜单

- V2.1：新增 `docs/design/member_b/v2_1/class_v2_1.mmd`（当前实现快照，不是目标全图）与 `README.md`（V2 目标→实现差异表、AC-06 报告口径）；PNG 待 B 用 mermaid.live 渲染。
- CUI：`CuiApp` 重构为登录 + 角色菜单（补货/报损/售后/退出），只依赖 `Operations` 接口；新增 `CuiActor`、`OperationFeedback`、`Operations`、`UnavailableOperations`。未接真实 A 适配时 `UnavailableOperations` 明确返回"依赖未交付"，不伪造库存/提交结果。
- 测试：`CuiAppTest`(4)；累计 **56 项测试全部通过**（BUILD SUCCESS）。
- 边界：仍无 `main`/pom exec（A 的 Maven 框架/A 侧 shared 未交付）；CUI 真实业务适配器待 A 的 IF-04/05；V2.1 图尚未渲染；未执行 AC。

## 11. 2026-10-21 增补：V2.1 分图源文件

- 新增 `docs/design/member_b/v2_1/split/`：`00_overview.mmd`（总览）、`01_auth_cui.mmd`、`02_stock.mmd`、`03_aftersale.mmd`。
- `02_stock.mmd` 已补 `LossOrigin`/`LossState` 的 `<<enumeration>>` 记号；四张图均只显示类/接口/依赖，不含字段/方法。
- `v2_1/README.md` §5 登记图 A–D、PNG 命名（`A_overview.png` 等）、渲染后核对 `RedemptionKind` 裁切与 auth/cui 空白裁切。
- 边界：PNG 仍由 B 渲染，未入库；未执行 AC；图注必须注明"结构快照"。
