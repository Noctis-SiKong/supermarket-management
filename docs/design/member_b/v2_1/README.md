# B 类图 V2.1：已实现快照（2026-10-21）

## 1. 定位

- `class_v2.mmd` 是**目标设计**（约 56 类，含 A 公共类型与未实现编排）。
- 本文件 `class_v2_1.mmd` 是**当前实现快照**：只画 `src/main/java` 中已存在的 B 侧类/接口/端口，以及它们与 A 端口的消费关系。
- 用途：支撑 AC-06/TC-02"设计与实现对应"的阶段性证据；最终报告定稿时，应再升级为"最终实现图"并注明与 V2 的差异。

## 2. V2 目标 → V2.1 状态

| V2 目标元素 | 当前状态 | 位置/说明 |
|---|---|---|
| `AuthService` / `LocalAuthService` / `Role` / `SessionRef` | 已实现（骨架） | `auth/`；凭证来源用 `CredentialStore` 替身 |
| `CuiApp` / CUI 输入校验 | 已实现（登录 + 菜单分派） | `cui/`；真实业务接线待 A 的 shared |
| `StockOperations` / `StockService` / `StockValidator` | 已实现 | `stock/`；IF-07 候选，不提交 |
| `StockPorts` / `SubmissionGateway` | 已实现（B 侧端口） | 正式适配 A 的 IF-03/04/05/06；提交未接线 |
| `ReplenishmentService` / `LossService` | 已实现（候选） | 不提交；见 `implementation_log.md` §6 |
| `QuotaViewBuilder` / `QuotaCalculator` / `QualityPairValidator` / `SettlementCalculator` | 已实现（纯函数） | `aftersale/`；结算候选已接入编排 |
| `AftersaleService` / `AftersaleAssembler` / `AftersaleCommand` | 已实现（编排骨架） | 资格简化规则；不提交；见 §9 |
| `BusinessTransaction` / `OperationResult` | **未实现** | 目标模板，后续跨模块复用；当前各服务用三态结果 |
| `ReversalPlan` / `ReversalService` / `DependencyChecker` / D1 消费 | **已实现骨架（不提交）** | `reversal/`；D1 只做阻断/环检测，不级联重建；依赖 A 的 IF-04/05 |
| 查询模块（`query`） | **已实现骨架（只读）** | `query/QueryService`：原行/额度/核销历史/损耗记录；权限范围待 A 的 IF-04 |
| A 的 `shared/`、`infrastructure/` | **已合入 main（a109135）** | 当前 Application 已统一接真实端口；此图为历史快照，现状见 docs/P1_Implementation_Log.md |
| 款项/顾客交回损耗/权限校验细节 | **未实现** | 骨架边界，报告需如实说明 |

> 注：当前 `class_v2_1.mmd` 与 `split/` 分图尚未包含 `query`/`reversal` 新包；下次渲染前补入，或在报告中单独出图。

## 3. 已知差异与报告口径

1. V2.1 不是"完成全部设计"的图；它证明当前代码结构可追溯到文档，不证明 A 域已实现。
2. 图中 `UnavailableOperations` 是明确的未接线占位，不得在报告/演示中当作真实业务能力。
3. 最终 AC-06 证据需在 B 完成 G3 真实端口适配后重绘图并替换本文件说明。
4. 建议由 B 在 mermaid.live 渲染 `class_v2_1.mmd` 为 PNG，放入 `v2_1/rendered/` 并在报告图注中标注"V2.1 已实现快照"。

## 4. 文件

- `class_v2_1.mmd`：本快照的 Mermaid 源码（当前未渲染）。
- 目标设计：`../class_v2.mmd`；差异决策：`../design_evolution.md`。

## 5. 分图源文件（2026-10-21）

总览图信息密度高，报告装配时拆为四张结构图（均只显示类/接口/依赖，不含字段/方法）：

| 文件 | 建议图号 | 图注 |
|---|---|---|
| `split/00_overview.mmd` | 图 A | V2.1 已实现结构总览（仅类与依赖关系） |
| `split/01_auth_cui.mmd` | 图 B | auth / cui 认证与交互结构 |
| `split/02_stock.mmd` | 图 C | stock 库存结构（已补 `LossOrigin/LossState` 枚举记号） |
| `split/03_aftersale.mmd` | 图 D | aftersale 售后结构（含 stock 外部依赖） |

- 四张图的 PNG 尚未入库，待 B 用 mermaid.live 渲染后放 `v2_1/rendered/`（建议命名 `A_overview.png`、`B_auth_cui.png`、`C_stock.png`、`D_aftersale.png`）。
- 渲染时确认 `RedemptionKind` 末字符未被裁切；`auth/cui` 图可裁掉左侧多余空白。
- 图注必须保留“仅类与依赖、不含字段/方法”，不得把结构快照表述为完整详细类图。



2026-10-21 v3 补记：当前款项、顾客交回损耗、权限、IF-11、CUI 与整单冲销已有统一实现和真实链验证；上表是 V2.1 形成时点快照，不重绘 V1/V2，不以旧“未交付”判断当前状态。纯单核销计划原型及旧提交载荷移到测试源码保留，生产整单冲销只走正式统一入口。
