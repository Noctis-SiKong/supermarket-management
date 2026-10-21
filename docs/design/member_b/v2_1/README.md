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
| `QuotaViewBuilder` / `QuotaCalculator` / `QualityPairValidator` | 已实现（纯函数） | `aftersale/` |
| `AftersaleService` / `AftersaleAssembler` / `AftersaleCommand` | 已实现（编排骨架） | 资格简化规则；不提交；见 §9 |
| `BusinessTransaction` / `OperationResult` | **未实现** | 目标模板，后续跨模块复用；当前各服务用三态结果 |
| `ReversalPlan` / `DependencyChecker` / D1 消费 | **未实现** | 归属 WP-06；依赖 A 的 IF-04/05 |
| 查询模块（`query`） | **未实现** | 归属 WP-05；依赖 A IF-04 |
| A 的 `shared/`、`infrastructure/` | **未交付** | 联调/真实提交前置 |
| 款项/顾客交回损耗/权限校验细节 | **未实现** | 骨架边界，报告需如实说明 |

## 3. 已知差异与报告口径

1. V2.1 不是"完成全部设计"的图；它证明当前代码结构可追溯到文档，不证明 A 域已实现。
2. 图中 `UnavailableOperations` 是明确的未接线占位，不得在报告/演示中当作真实业务能力。
3. 最终 AC-06 证据需在 A 交付 shared、B 完成 query/reversal 后重绘图并替换本文件说明。
4. 建议由 B 在 mermaid.live 渲染 `class_v2_1.mmd` 为 PNG，放入 `v2_1/rendered/` 并在报告图注中标注"V2.1 已实现快照"。

## 4. 文件

- `class_v2_1.mmd`：本快照的 Mermaid 源码（当前未渲染）。
- 目标设计：`../class_v2.mmd`；差异决策：`../design_evolution.md`。
