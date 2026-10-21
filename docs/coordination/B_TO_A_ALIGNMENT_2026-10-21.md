# B → A 对齐消息（P1）

- 日期：2026-10-21 14:07（Asia/Shanghai）
- 发出：B（CGwal）
- 接收：A（A1moss）
- 分支：`member-b/implementation`
- HEAD：`68f7c51`（`feat(member-b): complete independent business rules and tests`）
- 基线 main：`04abd6a`（本分支领先 **12** 个提交）
- 构建状态：JDK 17.0.12 + Maven 3.9.11，`clean verify` **BUILD SUCCESS**，**84 项测试**通过（模块级替身，非真实 AC）
- 证据入口：`docs/evidence/member_b/implementation_log.md`、`docs/evidence/member_b/ac_coverage_b.md`、`docs/evidence/member_b/ai_usage_log_b.md`

> 本文是 B 侧对齐请求，不是对 A 的指令。A 可逐条采纳/拒绝/改期；B 按 A 的正式回复调整。

## 1. B 已完成（当前分支）

| 包 | 已实现 | 状态 |
|---|---|---|
| `auth` | 登录、会话、角色、凭证只读端口 | 骨架 |
| `cui` | 登录 + 角色菜单（补货/报损/售后）；`Operations` 接口 | 骨架；真实适配未接 |
| `stock` | IF-07 校验/候选、补货、报损、`LossRecord.dispose` | 候选，不提交 |
| `aftersale` | 额度/金额/质量配对/结算、售后编排、例外留痕、多明细顺序额度 | 候选，不提交 |
| `query` | 原行/额度/核销历史/损耗记录只读 | 骨架 |
| `reversal` | 权限、D1 阻断/环检测、RELEASE + 反向库存计划 | 骨架，不级联重建 |

明确边界：全部使用测试替身，不写真实 `data/`，不接真实 IF-04/05，不声称 AC 通过。

## 2. 需要 A 确认/提供的事项（按优先级）

### R1. `shared/` 与 IF-04/05 公共代码（阻塞 B 真实集成）

请 A 给出：
1. `shared/`、`infrastructure/` 的**实际包名与类名**（含 `SessionRef`、`OperationContext`、`WorkingCopy`、`ChangeSet`、`SubmissionRequest`、`CommitResult` 等）；
2. `WorkingCopy` 的授权只读视图接口（B 的 stock/query/reversal 需要读余额/原行/核销/损耗）；
3. IF-05 的提交入口与 `businessRequest`/`requestKey` 传递方式；
4. 交付时间点（B 可在收到后 1 个时间片内做适配）。

B 当前临时端口（待 A 类型到达后写适配器，不复制 A 类型）：
- `stock.StockPorts.WorkingCopyView` / `ProductCatalog` / `IdGenerator`
- `stock.SubmissionGateway`
- `aftersale.OriginalSaleReader` / `QuotaLedgerReader` / `ProductSnapshotReader`
- `reversal.RedemptionReader` / `ReversalDependencyReader` / `StockEffectReader`

### R2. 包路径不一致（需统一定稿）

- A 的共享规范文本写 `nz.ac.aut.pdc.{auth,...}`；
- 当前 B 代码在 `nz.ac.aut.pdc.supermarket.{auth,cui,stock,aftersale,query,reversal}`（README/任务分工的既有约定）。
- 请 A 确认：**以 `nz.ac.aut.pdc.supermarket` 为最终路径**，还是需要 B 迁移。若迁移，请给出目标包名与迁移时点。

### R3. IF-07 `balances` 签名差异（契约需裁决）

- 共享规范 v1.02 / IF-07 v0.3 §14 写：`balances(wc, overlay, barcodes)`；
- B 骨架实现为：`balances(WorkingCopyView wc, Collection<String> barcodes)`（无 overlay）。
- 请 A 确认：余额读取是否必须带 overlay；若必须，B 调整接口与测试；若只读余额不需要 overlay，请 A 在 IF-07 / 共享规范中改为无 overlay，并注明 overlay 仅用于 `check`。

### R4. B 新增业务码登记

B 已使用以下稳定码，尚未在共享规范 §4 列表内，请 A 决定是否登记或替换：

| 码 | 类型 | 使用位置 |
|---|---|---|
| `STOCK_CHECK_FAILED` | FAILED | IF-07 内部/依赖读取异常 |
| `QUERY_FAILED` | FAILED | 查询读取异常 |
| `AFTERSALE_FAILED` | FAILED | 售后编排内部异常 |
| `DEPENDENCY_BLOCKED` | REJECTED | D1 存在后续引用，阻止直接冲销 |
| `REVERSAL_FAILED` | FAILED | 冲销编排内部异常 |

### R5. IF-11 / AC-87 真实读取与追溯

B 的 `OriginalSaleReader`、`QuotaLedgerReader`、`StockEffectReader`、`ReversalDependencyReader` 目前是替身。请 A 给出 IF-11 的真实读取/追溯方案与接口；否则 AC-87 及相关售后/冲销 AC 只能停留在模块测试。

### R6. CUI 可运行入口与 pom 归属

- 当前仓库无 `main`，`pom.xml` 无 exec/shade/Main-Class；AC-02 无法达成。
- 请 A 明确：启动入口与 `exec-maven-plugin`/`Main-Class` 由 A 的 Maven 框架统一加，还是授权 B 在 `cui` 包加 `Main`；若 B 加，A 是否接受对 `pom.xml` 的改动。

### R7. 分支与合并

- B 分支已推送：`origin/member-b/implementation`，12 个提交领先 main；工作区干净。
- 未开 PR；B 不直接推 main。
- 请 A：
  1. 确认审查/合并方式（PR 还是本地 merge）；
  2. 如 main 有新增提交，告知 B rebase 时点；
  3. 更新 README 的“尚无业务代码”状态行（README 由 A 维护）；
  4. 确认 P1 用例图是否仍需要 B 正式评审。

### R8. 结果类型映射

B 侧目前有 `AuthResult`、`StockCheckResult`、`QueryResult`、`ReversalResult`、`AftersaleResult` 等三态结果；A 的共享规范要求 `ReadResult`/`CandidateResult`/`CommitResult`。请 A 确认：
- 是否要求 B 在适配层统一映射为 A 公共结果类型；
- 若要求，请给出映射责任方（B 适配器 or A 提供转换器）。

## 3. B 接下来会做的（不阻塞 A）

1. 收到 R1/R2/R3 后写适配器与真实集成测试骨架；
2. 代码冻结后更新 V2.1 图（只渲染一次）；
3. 不改 CUI/提交边界，直到 R6/R1 明确。

## 4. A 的回复方式（建议）

- 在 main 或本分支新增 `docs/coordination/A_TO_B_ALIGNMENT_2026-10-21.md`，逐条写“采纳/拒绝/改期 + 类型/包名/时间点”；
- 或在 PR 评论中回复（B 可读）；
- B 收到后 `git fetch` + `rebase`，并在 `design_notes.md` §8 记录同步结果。

## 5. 复现命令

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
./mvnw.cmd --batch-mode --no-transfer-progress clean verify
# 期望：BUILD SUCCESS，Tests run: 84, Failures: 0, Errors: 0
```

本消息由 B 生成并提交到 Git；如与 A 的正式规范冲突，以 A 的共享规范为准，B 调整实现。
