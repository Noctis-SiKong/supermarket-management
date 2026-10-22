# 成员B 独立审查报告（快照：2026-10-21 12:40，复核至 12:50）

审查性质：只读、证据化、独立审查。审查者不参与本项目设计/实现，不代表成员A或成员B做任何决定。
本文件是本轮唯一写入的文件；未修改源码、未修改既有文档、未执行 `git add/commit/push`。

---

## 0. 结论摘要（先看这一节）

| 维度 | 结论（事实层面） | 最严重级别 |
|---|---|---|
| 1 需求与验收可追溯性 | 设计文档有 FR/BR/AC 列，但**代码与测试中 0 处 AC 引用**（仅 `CuiApp.java:15` 提到 FR-11）；B 责任范围内售后/冲销/查询三个包**不存在**；指定 AC 中仅 AC-51/AC-53 有部分落点 | P0 |
| 2 设计与契约一致性 | IF-07 v0.3 文件内部**自相矛盾**（§4/§5 未按 §14 修订）；V2（56 类）与实现（27 文件）差异大；R-04/R-29 与 CR07-09 的口径已闭合但 `design_review.md` §7 原始理由未加覆盖注记 | P1 |
| 3 代码质量与接口规范 | 构造器注入、不可变 record、sealed 三态、无保存入口等做得规范；但 `SubmissionGateway`/`WorkingCopyView.baseLast()` **生产代码零调用**，错误码 `INTERNAL_ERROR` 不在规范列表，`eventKey` 每次重算破坏幂等，`receiptReviewId` 缺失 | P1 |
| 4 测试充分性 | 实测 29 项全部通过（`clean verify`，JDK 17.0.12）；全部为模块内单元测试，**无 CUI 测试、无幂等/并发/恢复测试、无 AC 级测试** | P1 |
| 5 证据与留痕诚实性 | 构建/测试计数、JDK/Maven 版本、评审条数与决定数**均可核验且一致**；未发现伪造；但存在文档版本号未升、证据索引指向旧条目、README 与分支状态矛盾等**过期表述** | P1 |
| 6 交付风险 | 无可运行 CUI 入口、售后/冲销未实现、设计图未随实现更新，是距离 10-22 的三项硬风险 | P0 |

**最严重的 3 件事（详见 §7.4）**
1. 仓库内**没有可运行的 CUI 主类/启动入口**（无 `main`，`pom.xml` 无 exec/shade/Main-Class），AC-02 无法达成，对应课程"无法编译运行 -5%~-10%"风险（`docs/Project_Spec.md:85`）。
2. **WP-05 售后与 WP-06 冲销/查询零代码**（无 `aftersale`/`query`/`reversal` 包），对应 `design_review.md:91` 自列的 AC-09/14/15/17–20/33/34/46/47/54–59/79–82 整块无实现。
3. **V2 设计与实现不同步**（56 类 vs 27 文件；`BusinessTransaction`/`OperationResult`/`Quota*`/`Reversal*` 未实现，新增 `StockOperations`/`StockPorts` 未入图），直接命中 AC-06/TC-02"图与实现对应"。

---

## 1. 审查范围、快照与环境

### 1.1 快照与时间

| 项 | 值 |
|---|---|
| 审查开始（工具时间戳） | 2026-10-21 12:44:42 +08:00 |
| 基准快照 S0（=任务口径"截至 12:40"） | `6c272ca2d5bbf1b70ac3b1b003e54f837d39a907`（2026-10-21T12:40:01+08:00，`feat(member-b): add auth/cui/stock skeleton with test doubles`） |
| 审查期间新增提交 | `d1ec5b1c41ec0f8eb20910467d1415197403d5a5`（2026-10-21T12:46:59+08:00，`feat(member-b): add stock replenishment and loss use-case services`），复核快照 S1 |
| 最终核验时间 | 2026-10-21 12:50:57 +08:00；HEAD = `d1ec5b1`；`git status --porcelain` 为空 |
| 分支位置 | `member-b/implementation` 比 `main`（`04abd6a`）**领先 4 个提交**（`159266e`、`c96a7e0`、`6c272ca`、`d1ec5b1`）；`origin/member-b/implementation` 与本分支一致 |
| 工作区 | 干净（12:44:42 与 12:50:57 两次核验均为空输出） |

**快照漂移说明（事实）**：12:44:42 时的 HEAD 为 `6c272ca`；12:46:59 出现 `d1ec5b1`，新增 `ReplenishmentService`/`LossService` 等 8 个文件、2 个测试类，并补写 `implementation_log.md` §6 与 `ai_usage_log_b.md` #10。本报告所有结论同时标注快照；凡与"21 项 vs 29 项测试"有关的结论按快照分别陈述。另：12:44–12:47 期间工作区曾被并发写入（审查者在 12:44 后读到 `ai_usage_log_b.md` 已含 #10，但 `git show 6c272ca` 证明该行由 `d1ec5b1` 引入），故本报告不把该窗口内的文档内容当作 `6c272ca` 的提交内容。

### 1.2 实测环境与命令（本轮实际执行）

| 项 | 实测值 | 证据 |
|---|---|---|
| JDK | `java version "17.0.12" 2024-07-16 LTS`（`C:\Program Files\Java\jdk-17`） | 审查者执行 `java -version` |
| Maven | Wrapper 3.3.4 → `distributionUrl` apache-maven **3.9.11** | `.mvn/wrapper/maven-wrapper.properties:1,3` |
| 构建命令 | `./mvnw.cmd --batch-mode --no-transfer-progress clean verify`（`JAVA_HOME` 指向 JDK 17） | 审查者执行；与 `README.md:105`、`docs/evidence/member_b/implementation_log.md:20` 一致 |
| 构建结果 | **BUILD SUCCESS**；`Tests run: 29, Failures: 0, Errors: 0, Skipped: 0` | 审查者执行输出（逐类：Framework 2 / auth 7 / cui 4 / Loss 4 / Replenishment 4 / StockService 4 / StockValidator 4） |
| 代码规模（S1） | `src/main/java` 27 个 `.java` / 950 行；`src/test/java` 12 个 `.java` / 523 行 | 审查者统计 |
| 包结构（S1） | `nz.ac.aut.pdc.supermarket.{auth(8), cui(2), stock(17)}`；**无** `aftersale`/`query`/`reversal` | 目录枚举 |
| 设计产物 | `class_v1.mmd` 32 类；`class_v2.mmd` **56** 类；`v2/rendered/` 8 个 PNG | 审查者统计 |

`6c272ca` 快照的测试数：21 项（Framework 2 / auth 7 / cui 4 / StockService 4 / StockValidator 4），与 `docs/evidence/member_b/implementation_log.md:21`（21 项）及 `ai_usage_log_b.md` #9 一致；`d1ec5b1` 后的 29 项与 `implementation_log.md:52`、`ai_usage_log_b.md` #10 一致。两处计数均与实测相符。

### 1.3 本次审查实际执行 / 未执行

**已执行**：通读任务列出的全部必读材料（README、Spec、PRD、ERD、分工清单、共享规范 v1.02、B 的 V1/V2/if07/design_notes、5 份 evidence）；读取 `src/main/java` 全部 27 个文件与 `src/test/java` 全部 12 个文件；只读 Git（`log`/`show --stat`/`status`/`branch -v`）；执行一次 `clean verify`；统计类数、测试数、行数。

**未执行（不构成本报告结论）**：未做远端克隆与离线构建复核；未访问 Canvas；未渲染 `.mmd` 或逐张查看 8 张 PNG（仅确认文件存在与数量）；未对 `.svg` 用例图做内容判读；未执行任何 AC 场景；未评估学生反思文本（模板 §2.6/§3.7 由本人完成，见 `docs/Project_Spec.md:571,603`）。

---

## 2. 维度一：需求与验收可追溯性

### 2.1 总表（B 责任范围及跨边界项）

说明：`—` 表示按分工不属于 B（见 `docs/Shared_Boundaries_and_Interface_Spec.md:31-32`、`docs/P1_Preliminary_Work_Distribution_and_Interface_List.md:326-327`）；"部分"指有结构落点但无场景/无验收证据。

| 需求/验收 | 设计要求（文档落点） | 代码落点（S1） | 测试落点 | 结论 | 证据 |
|---|---|---|---|---|---|
| FR-01/BR-04 三角色与经办 | `design_notes.md:30`；V2 `auth` 命名空间 | `auth/Role`、`AuthService`、`LocalAuthService`、`SessionRef` | `LocalAuthServiceTest`（7） | **部分**：有登录/会话/角色枚举；无"角色→操作"权限矩阵（仅 `Role.canAuthorizeException()` 一项），无预置账号持久化，无具体经办人写入业务记录 | `auth/Role.java:8-16`、`LocalAuthService.java:26-47` |
| FR-04/12 BR-08/20 两位置、补货 | `design_notes.md:30`；IF-07 | `stock/StockService`、`StockValidator`、`ReplenishmentService` | `StockServiceTest`(4)、`StockValidatorTest`(4)、`ReplenishmentServiceTest`(4) | **部分**：补货 W−/S+ 双腿守恒与来源不足有实现+单测；无期初、无流水重建、无余额持久化（AC-22/30 无落点） | `ReplenishmentService.java:35-44`、`StockValidator.java:72-92` |
| FR-13 BR-14/25 报损与处置 | `design_notes.md:30`；`design_review.md:34`(R-06) | `stock/LossService`、`LossRecord`、`LossOrigin`、`LossState` | `LossServiceTest`(4) | **部分**：店内报损扣来源位置；**无经办/时间/原因/去向**，`sourceLineId=null`、`disposition=null`（与 ERD 必填冲突）；顾客交回仅枚举值无实现（AC-15/18 无落点） | `LossService.java:48-57`、`LossRecord.java:11-34` |
| FR-07/08 BR-12/13/15/22/26/29 售后双额度 | `design_notes.md:31`；`design_evolution.md:33-60`；V2 `aftersale` 命名空间 | **无**（`aftersale` 包不存在） | 无 | **缺口**：AC-09/14/15/17–20/33/34/46/47/54–59/79–82 全无实现 | 目录枚举；`implementation_log.md:38`（"未实现 aftersale/query/reversal"） |
| FR-09 BR-16 授权整单冲销 | `design_notes.md:30`；`design_evolution.md:43,51` | **无** | 无 | **缺口**：AC-10/28/35/52/59/83/84 无实现 | 同上 |
| FR-10 BR-17 D2 快照/序号 | `docs/Project_Spec.md:347-362` | **无**（`commitOrdinal` 仅出现在注释与端口占位） | 无 | **缺口**：AC-85 十项无任何落点；`WorkingCopyView.baseLast()` 生产代码零调用 | `StockPorts.java:22`、`StockService.java:15` |
| FR-11 BR-18 CUI | `PRD-11`、`docs/Project_Spec.md:374` | `cui/CuiApp`、`InputValidator` | `InputValidatorTest`(4)；**CuiApp 无测试** | **部分/缺口**：仅登录-退出循环，**无业务菜单**，`InputValidator` 未被 `CuiApp` 调用 | `CuiApp.java:32-56`（未 import/使用 `InputValidator`） |
| FR-02 BR-05/06 条码与数量边界 | `docs/Project_Spec.md:422`(AC-53) | `cui/InputValidator` | `InputValidatorTest` | **部分**：数量 1–9999、金额 0–999999.99 与两位小数有实现与单测；**条码 8–14 位、折扣 1–99%、满减、附件 5,000,000 字节/3 张均无** | `InputValidator.java:14-51` |
| AC-06/TC-02 图与实现一致 | `docs/Project_Spec.md:375` | V2 的 `shared`/`aftersale`/`query`/`reversal`/`external` 大部分无对应实现；实现新增 V2 未有的 `StockOperations`/`StockPorts` | 无 | **缺口**：图-实现映射无法自洽 | `class_v2.mmd:267-329` vs `StockOperations.java:12`、`StockPorts.java:14` |
| AC-87 SYNC-02 售后专用读取 | `docs/Project_Spec.md:439`、`Shared…:144-159` | **无**（IF-11 消费方未实现） | 无 | **缺口**（且 A 侧技术方案未交付，见 `Shared…:148-157`） | 目录枚举 |

### 2.2 指定 AC 的缺口明细

| AC | 需求要点（原文摘要） | 当前落点 | 缺口性质 | 证据 |
|---|---|---|---|---|
| AC-09 | 店员无店长在线完成普通退货、退款 8 元、记录经办/时间/原因/去向/状态 | 仅 `Role.CLERK` 存在 | **无实现**（售后包缺失）；"不需店长逐笔批准"的编排不存在 | `docs/Project_Spec.md:378`；`auth/Role.java:8-16` |
| AC-14 | 同款质量换货超原购/在售不足拒绝；确认后差额 0、在售 0/在仓 10、替换流水关联换货 | 无 | **无实现**（`QualityPairValidator`/`QuotaCalculator` 均不存在） | `docs/Project_Spec.md:383`；`class_v2.mmd:152-154` 仅为图 |
| AC-15 | 退货 1 件残次品：退款 8 元、两位置不变、记录 1 件损耗及实际去向、无"不可售"桶 | `LossOrigin.CUSTOMER_RETURN` 枚举存在但无记录器；`LossService` 只处理 `LossOrigin.STORE` 且 `disposition=null` | **部分/缺口**：无售后联动，无去向字段值 | `docs/Project_Spec.md:384`；`LossService.java:48-57`；`LossOrigin.java:4-7` |
| AC-46 | Q=3、P=10.00 连续各退 1 件：3.33/3.33/3.34，最后一次用剩余实付 | 无 | **无实现** | `docs/Project_Spec.md:415`；`class_v2.mmd:144-151` 仅为图 |
| AC-51 | 三角色离线登录、凭证非明文、错密码拒绝；店员可销售/补货但不可配促销 | `LocalAuthService`+`Sha256PasswordHasher`（无盐 SHA-256，文档已声明）；**无"店员不可配促销"等能力矩阵** | **部分**：认证"持久化非明文"成立（哈希存储），权限矩阵与预置账号持久化缺失 | `docs/Project_Spec.md:420`；`Sha256PasswordHasher.java:17-28`；`Role.java:13-16` |
| AC-81 | 同单换 1 件退 2 件，两种录入顺序结果一致（先换后占额） | 无 | **无实现**（V2 的 `AftersaleCommand`/`AftersaleService.apply` 仅为图） | `docs/Project_Spec.md:433`；`design_evolution.md:33-34` |
| AC-82 | 同单不同商品资格独立、特殊质量配对显式关联且校验条码/数量/差额 | 无 | **无实现**（`QualityPairValidator` 不存在） | `docs/Project_Spec.md:434`；`class_v2.mmd:152-154` |
| AC-85 | D2 十项（序号递增、同次同号、失败不可见、崩溃可见、恢复校验、last<max 阻写、双进程锁、不替代提交序、无库存提交递增、附件异常阻写） | 无 `commitOrdinal`/`last`/锁/快照代码 | **无实现**；`SubmissionGateway.SubmissionRequest` 有 `baseLast` 字段但**无任何生产调用方** | `docs/Project_Spec.md:437`；`SubmissionGateway.java:17-32`；审查者实测：全仓库仅 `FakeSubmissionGateway` 引用该类型 |
| AC-87 | 店员普通查询不可跨人；凭凭证经售后专用流程最少字段；可追溯经办人—原销售—售后 | 无 | **无实现**；且 A 侧 IF-11 局部未决（保存失败是否返数据、取消/删除留存） | `docs/Project_Spec.md:439`；`Shared…:148-157` |

### 2.3 追溯性通道本身的问题

- **事实**：`src/main/java` 与 `src/test/java` 中仅 1 处需求编号引用（`CuiApp.java:15` 注释提到 FR-11）；**0 处 AC 编号**。测试方法名不对应 AC 编号（如 `replenishmentPreparesBalancedTransferAndBalances`）。
- **事实**：AC 追踪只存在于设计文档层（`design_notes.md:31-33`、`design_review.md:87-97`、`design_evolution.md:33-60` 的"影响"列）。
- **建议（供作者决定）**：在测试类 Javadoc 或方法名中标注 AC 编号，或在 `docs/evidence/acceptance_results.md`（Spec §15.3 计划路径，当前不存在）建立 AC→测试→实跑结果索引；此项直接影响 T §2.2/§3.6 与 AC-06 的可审计性。
- **严重度**：P1（不影响编译，但影响"可追溯"评分项与"A 集中审计"的可行性）。

---

## 3. 维度二：设计与契约一致性

### 3.1 事实发现

| # | 结论（事实） | 证据 | 严重度 | 建议（供作者决定） |
|---|---|---|---|---|
| D-01 | IF-07 v0.3 **同一文件自相矛盾**：§4 输出表仍列 `source_locations`（"来源入库定位…可用数量"），§14.1 与共享规范 §5.3 已明文删除该语义 | `docs/archive/design_member_b/if07_contract.md:82` vs `:191`；`Shared_Boundaries_and_Interface_Spec.md:134` | P1 | 在 §4 就地删除/标注失效，或用"§14 覆盖 §4"的显式修订标记；否则 A 审计时会读到两套输出契约 |
| D-02 | IF-07 v0.3 §5 错误码表（`SOURCE_INSUFFICIENT`/`INVALID_QUANTITY`/`DUPLICATE_EVENT`/`UNKNOWN_REFERENCE`/`OUT_OF_SCOPE`）与 §14.5"沿用规范 §4 稳定码、本接口不新增细分码"矛盾；**代码实际采用 §14 口径** | `if07_contract.md:93-99` vs `:198`；`StockValidator.java:29,38,63,95`；`StockService.java:86` | P1 | 二选一并同步 §5/§14 与代码；当前状态是"文档两套、代码按其中一套" |
| D-03 | IF-07 v0.3 声明签名 `balances(wc, overlay, barcodes)`，代码实现为 `balances(WorkingCopyView, Collection)`（**无 overlay**） | `if07_contract.md:193`；`Shared…:132`；`StockOperations.java:15`；`StockService.java:34-42` | P1 | 由 B 决定：补 overlay 入参，或在契约中登记"B 侧当前不含 overlay"的差异与理由（规范 §10 要求变更通知） |
| D-04 | IF-07 v0.3 声明 `check(wc, overlay: OverlayView, changes)`，代码以 `List<StockEntry>` 充当 overlay；无 `OverlayView` 类型 | `if07_contract.md:194`；`StockOperations.java:24-27` | P2 | 在契约 §14.8 补"以 `List<StockEntry>` 逻辑等价表达 overlay"的说明，避免 A 按类型名对接失败 |
| D-05 | 幂等契约未落地且被代码反向破坏：契约要求"沿 event_key/首次有效收货行/transfer_key 校验、不得换新键"，代码每次 `check` 都生成**新的** `EV-*` 键，且无任何去重逻辑 | `if07_contract.md:113-118`；`Shared…:138`；`StockService.java:65-76` | P1 | 由 B 决定 eventKey 的来源（调用方提供 vs 稳定派生）；否则重试路径必然产生重复流水候选 |
| D-06 | 契约 §3 输入要求收货审核场景携带 `receipt_review_id`，V2 的 `StockEntry` 亦有 `receiptReviewId`，**代码两个类型都没有该字段**（V2→代码倒退） | `if07_contract.md:66`；`class_v2.mmd:51-62`；`design_review.md:45`(R-12)；`StockChangeLine.java:18-24`；`StockEntry.java:10-18` | P1 | 补字段或在契约中登记"由 A 的 sourceLine 承载"的替代方案；否则 AC-83/84 的来源链与"首次有效收货行"防重无载体 |
| D-07 | `design_evolution.md` 内部矛盾：§3.1 写 R-04 口径"待 B 确认"，§3.6 写"不再标'待 B 确认'" | `design_evolution.md:66` vs `:71` | P2 | 保留一处权威表述（§3.6 为最新），把 §3.1 改为"见 §3.6" |
| D-08 | A 的共享规范 §9 仍记"R-04/R-29 内部选择仍待 B 记录"，而 B 已于 2026-10-21 记录确认 | `Shared…:202`；`design_notes.md:83`；`design_evolution.md:71`；`ai_usage_log_b.md:24` | P2 | B 不得改 A 文件：需通知 A 更新 §9 行状态（属于 A 的维护边界） |
| D-09 | R-04 原始拒绝理由（"售后逻辑一般由人工负责，没有在系统中的复杂逻辑实现"）与 Spec BR-22/26、CR07-09 的"系统默认拒绝＋店长例外"直接冲突；后续已用 A 规范 §8 覆盖，但 `design_review.md` §7 原格未加覆盖注记 | `design_review.md:108` vs `docs/Project_Spec.md:256,260`；`Shared…:191,207`；`design_evolution.md:70-71` | P1 | 在 §7 该行加"已被 2026-10-21 口径覆盖，见 design_evolution §3.6"的注记，避免报告 §3.4 直接引用时被读成"系统不校验资格"（规范 §9 明确该解释违规） |
| D-10 | V2 与实现差异：V2 56 类中 `shared`(6 类)、`aftersale`(15)、`query`(3)、`reversal`(6)、`external`(5) 均无实现；实现新增 V2 未有的 `StockOperations` 接口与 `StockPorts` 聚合类型 | `class_v2.mmd:267-329`；`StockOperations.java:12`；`StockPorts.java:14-39` | P1（AC-06） | 实现期同步 V2（`v2/README.md:28` 已自设此要求），或出 V2.1 并记录差异 |
| D-11 | 包根路径与共享规范约定不一致：规范写 B 位置为 `src/main/java/nz/ac/aut/pdc/{auth,cui,stock…}`，代码为 `nz/ac/aut/pdc/**supermarket**/...`；A 的 `shared/`、`infrastructure/` 同规范无 `supermarket` 段 | `Shared…:185,193`；`design_notes.md:59`；`src/main/java/nz/ac/aut/pdc/supermarket/` | P2 | A/B 对齐后择一（B 侧文档与代码自洽，但与规范文本不一致），并在代码位置记录中登记 |
| D-12 | V2 规模陈述"约 50 个类"，实测 **56** 个类框 | `v2/README.md:4`；`design_evolution.md:78`；审查者统计 `class_v2.mmd` | P3 | 改为"56 个"或"约 56" |
| D-13 | `v2/README.md:5` 称 PNG 由 B 在 mermaid.live 渲染导出；仓库仅存 PNG，无法核验渲染来源与 `class_v2.mmd` 的逐图一致性 | `v2/README.md:5,22`；`docs/archive/design_member_b/v2/rendered/*.png`（8 个） | P3 | 报告中如实写"图由 B 渲染"，或在 evidence 记录渲染时间/版本 |

### 3.2 已闭合项（避免误判，均为事实）

- **CR07-09 与 R-04 的裁决已记录且与基线一致**：`design_evolution.md:70-71`、`design_notes.md:83` 明确"不抽策略类；系统做默认资格校验；店长/管理员授权例外并留痕；数量/金额/库存校验不变"，与 `docs/Project_Spec.md:256`(BR-22)、`:260`(BR-26)、`:657`(CR07-09 批准范围) 一致，也与 `Shared…:191` 给出的两种可接受方案相容。**该冲突在文档层已闭合**；其未闭合的部分只在于实现（售后包不存在）。
- **V1→V2 的评审决定可对数**：`design_review.md:99-133` 共 29 条（R-01…R-29），与 `:23` 的 8 P0/11 P1/8 P2/2 P3 及 `design_evolution.md:23-25` 的 20 采纳/7 修改后采纳/2 拒绝逐项相符（审查者逐行核对）。R-27 拒绝"改排版"但另出 8 张分图（`design_evolution.md:77`），二者不冲突：分图是报告材料，未改权威源。
- **V2 已纳入 CR07-09 字段**：`class_v2.mmd:105-112`（`exceptionAuthorizedBy/exceptionReason/relaxedRule`）、`:379`（`AftersaleService ..> Session`）与 `design_evolution.md:60` 一致。

---

## 4. 维度三：代码质量与接口规范

### 4.1 符合项（事实）

| 项 | 证据 |
|---|---|
| 构造器注入，无静态单例/服务定位器 | `StockService.java:24-31`；`ReplenishmentService.java:17-20`；`LossService.java:16-19`；`CuiApp.java:25-29`；`LocalAuthService.java:20-23` |
| 不可变数据（`record` + `List.copyOf` + 紧凑构造器校验） | `StockBalance.java:12-22`；`StockEntry.java:10-31`；`StockCheckResult.java:15-20`；`SubmissionGateway.java:23-31`；`ReplenishmentResult.java:10-19`；`LossResult.java:10-17` |
| 结果三态用 `sealed interface` 而非 boolean/null | `AuthResult.java:11-37`；`StockCheckResult.java:11-36`；`ReplenishmentResult.java:7-33`；`LossResult.java:7-31`；`SubmissionGateway.java:35-46` |
| IF-07 不保存：`StockService` 不持有任何提交/文件依赖 | `StockService.java:18-124`（全文无 `SubmissionGateway`/IO） |
| 未发现 God class | 最大文件 `StockService` 125 行、`StockValidator` 113 行、`CuiApp` 67 行 |
| `SessionRef` 构造器包内可见，业务包无法伪造 | `SessionRef.java:15-17`；`LocalAuthService.java:41` |
| 价格/金额未用 double | `InputValidator.java:16,34-51`（`BigDecimal`） |

### 4.2 缺陷/偏差（事实）

| # | 结论（事实） | 证据 | 严重度 | 建议（供作者决定） |
|---|---|---|---|---|
| C-01 | `SubmissionGateway` 在 `src/main` 中**无任何调用方**；其唯一实现 `FakeSubmissionGateway` 也**未被任何测试引用**。即：声称的 IF-05 提交边界未接通，也无测试覆盖 | `SubmissionGateway.java:12`；`fixture/FakeSubmissionGateway.java:9`；审查者 grep `SubmissionGateway` 全命中仅定义/注释 | P1 | 在实现补货/报损提交编排时接上，或至少在 `implementation_log` 标注"当前未接通、无测试" |
| C-02 | `WorkingCopyView.baseLast()` 生产代码零调用，`STALE_WORKING_COPY` 永不产生 | `StockPorts.java:22`；`StockService.java`（全文无 `baseLast`）；`StockValidator.java`（无过期分支） | P1 | 由 B 决定过期检测落在 IF-07 还是 IF-05；契约 §5.3 把"工作副本过期"列为 IF-07 错误语义（`if07_contract.md:96`） |
| C-03 | `EVENT_CONFLICT` 与 `STALE_WORKING_COPY` 在代码中**无产生点**；契约声明的错误语义未实现 | `if07_contract.md:97,198`；`StockValidator.java:25-70`（仅 3 类拒绝码） | P1 | 与 C-02 一并收口 |
| C-04 | 错误码 `INTERNAL_ERROR` 不在共享规范 §4 稳定码列表内（列表含 `PERSISTENCE_FAILED` 等 FAILED 码） | `StockService.java:79` vs `Shared…:102` | P1 | 换用规范码或先在规范中登记（规范 §10：新增码须通知调用方） |
| C-05 | `check` 用 `catch (RuntimeException)` 把编程错误/`null` 参数统一转成 FAILED：`changes == null` 会得到 `FAILED/INTERNAL_ERROR` 而非 `REJECTED/INVALID_INPUT` | `StockService.java:51-52,78-80` | P2 | 区分"参数非法（REJECTED）"与"技术失败（FAILED）"；catch 范围收窄 |
| C-06 | `LocalAuthService.login` 同样以 `catch (RuntimeException)` 覆盖凭证读取/校验，可能掩盖实现缺陷为"技术失败" | `LocalAuthService.java:44-46` | P2 | 同上（此处在语义上可接受，但应限定异常类型） |
| C-07 | `StockValidator` 用**净额**判断位置非负（同一 `(barcode, position)` 先合并 delta 再比较），组内瞬时负值不拦截；该行为的业务含义（是否允许同组内先出后入）在契约中未写明 | `StockValidator.java:48-68` | P2 | 在 IF-07 契约写明"按净额校验"或改为逐条模拟；当前属未记录语义 |
| C-08 | `LossRecord` 允许 `disposition=null`（紧凑构造器未校验），`LossService` 恰好传 `null`；`sourceLineId` 亦传 `null`。ERD 规定 `source_line_id`/`disposition` 必填 | `LossRecord.java:21-34`；`LossService.java:48-56`；`docs/Project_ERD.md:631,634` | P1 | 骨架期可保留，但必须与"候选不可直接持久化"一起标注，避免被当作可提交记录 |
| C-09 | `LossRecord` 同时保存 `barcode`/`quantity`，与 ERD"商品由来源行确定"及 IF-07 §8"不另存可漂移商品 FK"冲突（V2 已有此偏差） | `LossRecord.java:11-19`；`docs/Project_ERD.md:631`；`if07_contract.md:126` | P2 | 由 B 决定：删除冗余字段或登记为"快照"并说明来源 |
| C-10 | 权限/角色校验在补货、报损入口**完全缺失**（方法签名无 session/context 参数） | `ReplenishmentService.java:22-26`；`LossService.java:21-26`；对照 `docs/Project_Spec.md:120-124,127` | P1 | 入口层或提交复核补；规范 §6 步骤 1 要求"业务协调者验证动作范围" |
| C-11 | 同一"三态结果"被重复定义 4 套（`AuthResult`/`StockCheckResult`/`ReplenishmentResult`/`LossResult` + `SubmitOutcome`），与 R-19"统一 `OperationResult`"及 V2 `shared.OperationResult` 不一致，CUI 需 5 个分支 | `AuthResult.java`、`StockCheckResult.java`、`ReplenishmentResult.java`、`LossResult.java`、`SubmissionGateway.java:35-46`；`design_review.md:52`；`class_v2.mmd:275-278` | P2 | 由 B 决定是否在 A 的公共三态交付后收敛（规范 §4 要求统一三态，B 内部适配可保留） |
| C-12 | `LocalAuthService` 构造器未做 `Objects.requireNonNull`（与其它类不一致）；会话无过期（已声明） | `LocalAuthService.java:20-23` vs `StockService.java:28-30` | P3 | 一致性小修 |
| C-13 | 密码哈希为**无盐 SHA-256**；文档已如实声明"正式应换带盐 KDF" | `Sha256PasswordHasher.java:9-12,17-28`；`implementation_log.md:36` | P3（事实已披露） | 若维持，报告应如实写"教学演示哈希"，不得声称"安全哈希" |
| C-14 | 全仓库无日志/审计输出（`java.util.logging`/SLF4J 均未使用）；BR-21/AC-09 的"经办/时间/原因/结果/去向/状态"无写入点 | 审查者 grep：`src/main` 无 logger；`LossService.java:48-57` 等 | P1 | 属实现缺口而非风格问题；至少 AC-09/37 需要的字段需在业务记录中出现 |
| C-15 | `src/main/java/.gitkeep` 仍存在（骨架期遗留） | 文件枚举 | P3 | 可删（不影响构建） |
| C-16 | `StockCheckResult.Rejected` 缺少规范 REJECTED 形态中的 `recordId` 字段（规范给出 `code/field?/recordId?/detail`） | `StockCheckResult.java:23-28`；`Shared…:97` | P3 | 若需定位具体原单/原行，补 `recordId` |

---

## 5. 维度四：测试充分性

### 5.1 现有测试清单（S1 实测 29 项，全部通过）

| 测试类 | 数量 | 覆盖内容 |
|---|---:|---|
| `FrameworkSmokeTest` | 2 | JDK 17 运行时特性、Jackson 中文 UTF-8 往返（框架既有，非 B 新增） |
| `LocalAuthServiceTest` | 7 | 正确登录/错密码/未知用户/会话解析/未知会话/null 会话/`Role.canAuthorizeException` |
| `InputValidatorTest` | 4 | 数量 1 与 9999、0/10000/1.5/abc/空；金额 0.00/999999.99/12.30；-1/1000000/12.345/abc |
| `StockValidatorTest` | 4 | 双腿守恒通过、在仓不足、双腿不守恒、0 与 10000 |
| `StockServiceTest` | 4 | 补货候选与余额、在仓不足、未知商品、overlay 计入 before |
| `ReplenishmentServiceTest` | 4 | 候选+余额+双腿同 key、数量非法、来源不足、未知商品 |
| `LossServiceTest` | 4 | 扣来源位置+LossRecord、来源不足、位置缺失、数量非法 |

### 5.2 覆盖结论（事实）

- **已覆盖**：R-INPUT 的部分边界（数量、金额、小数）、补货双腿守恒与来源不足、未知商品引用、overlay 参与 before 计算、报损单腿扣减、认证成功/失败/解析、`INSUFFICIENT_STOCK`/`TRANSFER_UNBALANCED`/`INVALID_INPUT`/`REFERENCE_INVALID` 四类拒绝码。
- **未覆盖（按风险排序）**：
  1. **CUI 零测试**：`CuiApp` 无任何测试类（AC-05"输入非法有说明、可重试或退出、无未处理异常"无验证）。
  2. **幂等/重放零测试**：`FakeSubmissionGateway` 未被使用；`eventKey` 每次重算的缺陷（见 §3 D-05）无测试能发现。
  3. **并发/恢复/持久化零测试**：无 `baseLast` 过期、无双进程锁、无快照/恢复（属 A 域，但 AC-85 目前完全无证据）。
  4. **边界与异常输入不完整**：`StockService.check(changes=null)`、同一变动组跨条码、`transferKey` 3 条以上腿、同组内先出后入（净额语义）、`StockEntry`/`StockChangeLine` 构造校验、`LossRecord` 必填字段（`disposition`）均无测试。
  5. **AC 级场景零测试**：无一项测试以 AC 编号组织；AC-12/13 只有"结构相似"的单测，未验证"两位置合计不绕过"（AC-13）的整体不变量，也未验证 AC-22/30 的余额重建。
  6. **售后域零测试**（代码不存在）。

### 5.3 替身是否被误当真实实现

- **事实**：5 个替身均在 `src/test/.../fixture`，`src/main` 无任何引用；`FakeWorkingCopy`（`:12-17`）、`FakeProductCatalog`（`:8-11`）、`FakeIdGenerator`（`:8-11`）、`InMemoryCredentialStore`（`:9-12`）、`FakeSubmissionGateway`（`:8`）均自带"虚构/非生产"说明。
- **事实**：`implementation_log.md:5,35-39` 与 `framework_repro.md:37-39` 明确"非真实持久化、非联调、非 AC 验收""不证明原子提交/幂等/恢复"。
- **结论**：**未发现把替身当真实实现的表述**；`design_notes.md:42-50`、`Shared…:175`、分工清单 `:633-636` 的"夹具不记真实 AC 通过"规则被遵守。
- **但需注意（事实）**：由于全部测试都在替身上运行，**没有任何一条测试跨过"候选→提交→重启"边界**；因此"不保存"只能由结构（`StockService` 无提交依赖）间接说明，而非测试断言。

---

## 6. 维度五：证据与留痕诚实性

### 6.1 可核验且成立

| 声明 | 出处 | 核验结果 |
|---|---|---|
| S0 时 21 项测试通过 | `implementation_log.md:21`、`ai_usage_log_b.md` #9 | 成立：21 = 2+7+4+4+4，与 `6c272ca` 的文件集一致 |
| S1 时 29 项测试通过、BUILD SUCCESS | `implementation_log.md:52`、`ai_usage_log_b.md` #10 | 成立：审查者实测 `Tests run: 29 … BUILD SUCCESS` |
| JDK 17.0.12 / Maven 3.9.11（Wrapper 3.3.4） | `framework_repro.md:12-13`、`implementation_log.md:19` | 成立：`java -version`=17.0.12；wrapper properties=`3.3.4`、`apache-maven-3.9.11` |
| 框架在线/离线 `clean verify` 均通过（2 项测试） | `framework_repro.md:28-30` | 部分成立：在线构建本轮复现；**离线与"远端全新克隆"两项未复核**（见 6.3） |
| 评审 29 条＝8 P0/11 P1/8 P2/2 P3；决定 20/7/2 | `design_review.md:23`、`:99-133`；`design_evolution.md:23-25` | 成立：逐行核对一致 |
| V1=32 类、V2≈50 类 | `design_review.md:4`、`v2/README.md:4` | V1 成立（32）；V2 实测 **56**（"约 50"偏低） |
| 提交信息与内容一致 | `6c272ca`（31 files/+1384）、`d1ec5b1`（11 files/+408） | 成立：`git show --stat` 与消息相符 |
| 8 张分图已入库 | `design_evolution.md:77`、`v2/README.md:7-18` | 成立：`v2/rendered/` 实有 8 个 PNG |

### 6.2 不一致 / 过期（事实，按严重度）

| # | 事实 | 证据 | 严重度 | 建议 |
|---|---|---|---|---|
| E-01 | `README.md` 仍称"尚无业务代码、无业务主类或业务实现"，而分支 HEAD 已有 27 个主代码文件；README 未随分支更新 | `README.md:3,93` vs 目录实测 | P1 | 分支合并前更新 README 的"当前状态/构建与运行"；否则合并后即成为与事实相反的平台陈述 |
| E-02 | `docs/Project_Spec.md:12`、`docs/Project_ERD.md:12` 的"当前交付/当前产物"仍写"无业务代码/暂无业务测试代码"；属 2026-10-20 冻结基线的状态行 | 同左 | P2 | 需求基线不必改；但 P1 装订报告时需在证据索引里注明"基线状态行早于实现" |
| E-03 | 文档版本号未随更正上升：`implementation_log.md` 标 v0.1 但已追加 §6；`design_notes.md` 标 v0.1 但已追加 2026-10-21 的 §8 记录 | `implementation_log.md:3,48-53`；`design_notes.md:3,73-85` | P2 | 升 v0.2 或加"修订记录"行，便于审计定位 |
| E-04 | `if07_contract.md:184` 的证据仍指向"AI 使用日志 #3"，而 v0.3 的实际记录为 #8 | `if07_contract.md:184`；`ai_usage_log_b.md:24,36` | P2 | 改为 #3（v0.1/v0.2）+ #8（v0.3） |
| E-05 | `design_review.md` §7 R-04 行未标注"已被 2026-10-21 口径覆盖"，而 §8 已写"决定须由 B 本人作出" | `design_review.md:108,139`；`design_evolution.md:70-71` | P1 | 加一行覆盖注记（见 D-09） |
| E-06 | `ai_usage_log_b.md` #8"我修改/拒绝了什么"列写"以 A 的想法执行（R-04 维持不抽策略类…）"——该列语义是"人的修改/拒绝理由"，此处记录的是采纳 A 方案的事实 | `ai_usage_log_b.md:24` | P2（证据质量，非不实） | 报告附录 A.2 引用时补"本人确认理由"，避免被读作"AI 代替判断"（T §3.4 要求 Accepted/Rejected 及 Reason） |
| E-07 | 12:40–12:46 之间，`implementation_log.md` §5 仍写"下一步：实现 stock 补货/报损"，而工作区已出现对应实现与 AI 日志 #10；两者在 `d1ec5b1` 提交后一致 | `git show 6c272ca`/`d1ec5b1`；`implementation_log.md:43-44,48-53` | P3（时间顺序所致，非伪造） | 无需处理；本报告已在 §1.1 记录该窗口 |

### 6.3 不可核验项（如实登记，不判真伪）

- `ai_usage_log_b.md:11` 的工具/模型/思考强度（DSH 0.2.1-rc.2、deepseek-flash 1017、thinking high/max）：仓库内无独立证据。
- `framework_repro.md:30` 的"远端全新克隆后离线构建通过"：本轮未克隆、未离线执行。
- `v2/README.md:5` 的"由 B 在 mermaid.live 渲染导出"：仓库只有 PNG。
- `docs/Project_Spec.md:33`、`:85` 的 Canvas 截止/时区：Spec 自述"未访问平台"（`:33`）；本轮亦未访问。

---

## 7. 维度六：交付风险（距离 10-22）

### 7.1 P0（不解决即影响提交可用性/硬性评分）

| # | 风险（事实基础） | 证据 | 影响 |
|---|---|---|---|
| P0-1 | **无可运行入口**：无 `main` 方法；`pom.xml` 无 `exec-maven-plugin`/`shade`/`Main-Class`，`maven-jar-plugin` 只产空壳 JAR；README 已自述"不能 `java -jar`" | `CuiApp.java:32`（仅 `run()`）；`pom.xml:28-63`；`README.md:93`；`docs/Project_Spec.md:310`(TC-04)、`:371`(AC-02) | AC-02/AC-05/AC-23/AC-07（演示视频）无法达成；课程"无法编译运行 -5%~-10%"（`docs/Project_Spec.md:85`） |
| P0-2 | **售后/冲销/查询零实现**：`aftersale`/`query`/`reversal` 包不存在；`design_review.md:91` 自列的售后 AC 组（AC-09/14/15/17–20/33/34/46/47/54–59/79–82）与冲销组（AC-10/28/35/52/59/83/84/85）全部无落点 | 目录枚举；`implementation_log.md:38` | P1 实现（30%）与 FR-07/08/09 功能覆盖；AC 覆盖率 |
| P0-3 | **A 侧公共代码未交付、分支未合并**：仓库无 `nz/ac/aut/pdc/shared`；`member-b/implementation` 领先 `main` 4 个提交；B 全部服务只返回候选，无一条提交/持久化路径 | `Shared…:185`（A 代码位置约定）；`git log main..member-b/implementation`；`implementation_log.md:35` | 无法执行任何端到端 AC；B 的"候选"若无人接 IF-04/05 即为不可运行代码 |

### 7.2 P1（高：直接影响评分项或返工成本）

| # | 风险 | 证据 |
|---|---|---|
| P1-1 | 设计-实现不同步（V2 56 类 vs 实现 27 文件；模板方法 `BusinessTransaction` 与统一三态未实现）→ AC-06/TC-02/TC-03 风险；且 CR-07 要求的"继承/抽象类实际用途"在 `src/main` 中目前**无用户自定义继承**（仅接口与 record/enum） | `class_v2.mmd:267-336`；`src/main/java` 实测无用户类 `extends`；`docs/Project_Spec.md:309`(TC-03)、`:375`(AC-06) |
| P1-2 | 契约偏差四项未收口：`balances` 缺 overlay、`INTERNAL_ERROR` 非规范码、`eventKey` 每次重算、`receiptReviewId` 缺失 | §3 D-03/D-05/D-06、§4 C-04 |
| P1-3 | 报损/补货无权限校验、无经办/时间/原因/去向字段 → AC-37/AC-51/BR-21 无落点 | §4 C-10、C-14；`docs/Project_Spec.md:406,420` |
| P1-4 | 幂等/过期/事件冲突三条契约语义无实现也无测试（AC-26/57/59 相关） | §3 D-05、§4 C-02/C-03 |
| P1-5 | 文档一致性：README 状态陈述与分支事实相反；IF-07 v0.3 同一文件两套输出/错误码；`design_review.md` R-04 原格未加覆盖注记 | §6 E-01、§3 D-01/D-02、D-09 |
| P1-6 | 无 AC→测试→实跑结果索引；`docs/evidence/acceptance_results.md` 不存在（Spec §15.3 计划路径） | `docs/Project_Spec.md:582`；`docs/evidence/member_b/` 仅 5 个文件 |

### 7.3 P2（中：可在提交前压缩处理）

| # | 风险 | 证据 |
|---|---|---|
| P2-1 | 售后/查询/冲销的**设计**已就绪但无任何可执行代码，若 10-22 前只做桩，需在报告中如实说明"未实现"而非"已实现待联调" | `design_evolution.md:99`；`implementation_log.md:43-45` |
| P2-2 | 测试均为单元级（29 项），无集成/CUI/边界矩阵；课程不强制 P1 测试数量，但报告 §3.6/AC 索引需要"已执行证据" | `docs/Project_Spec.md:330`、`:582` |
| P2-3 | 包根 `supermarket` 段与共享规范文本不一致；A/B 合并时 import 需对齐 | §3 D-11 |
| P2-4 | 文档版本号未升、证据索引指向旧条目、V2 类数陈述偏差 | §6 E-03/E-04、§3 D-12 |
| P2-5 | `LossRecord` 冗余/缺失字段与 ERD 不一致（barcode/quantity 冗余；disposition 允许 null） | §4 C-08/C-09 |
| P2-6 | 三态结果重复 5 套，CUI 呈现需 5 个分支 | §4 C-11 |

### 7.4 最可能影响评分的 3 件事

1. **无业务主类/可运行 CUI**（P0-1）：直接关联"完整工作 CUI""按实际命令构建运行"（CR-06/CR-08、AC-02）与"无法编译运行 -5%~-10%"的评分条款（`docs/Project_Spec.md:70,72,85,371`）。当前 `clean verify` 虽通过，但只证明库代码可编译。
2. **售后与冲销整块缺失**（P0-2）：`design_review.md:91-93` 自列的 AC 组是 P1 功能与验收的主体，其中售后双额度（BR-12/13/15/29、AC-46/54–59/79–82）是 Spec §7.4 的核心算法；无实现即无 AC 证据。
3. **设计图与实现不一致且未同步**（P0-3/P1-1）：课程把"基于最终设计实现、图与实现一致"列入设计 30% 与 AC-06（`docs/Project_Spec.md:375`），V2 的 `shared`/`aftersale`/`query`/`reversal`/`external` 命名空间与实现差异会直接暴露在该评分点上。

### 7.5 距 10-22 的缺口清单（按职责陈述，不替作者排序决策）

- **B 侧（成员B 责任）**：`auth` 权限矩阵与预置账号；`cui` 业务菜单/入口/`InputValidator` 接线；`stock` 提交编排（IF-05 适配）与权限/经办字段；`aftersale` 全套（双额度、资格、质量配对、幂等）；`query`/`reversal`（D1/D2 消费）；V2 同步或出 V2.1；IF-07 v0.3 自相矛盾的两处修正。
- **A 侧（成员A 责任，B 无法替代）**：`shared`/`infrastructure` 公共类型与 IF-04/05 真实实现；`catalog`/`receiving`/`sales`/`persistence`；IF-11 方案；分支合并与 README/状态行更新。
- **共同**：`docs/evidence/acceptance_results.md` 与 AC 索引；报告 §3.4/§3.6 引用所需的干净证据链；构建原始输出留档。

---

## 8. 维度七：客观性说明

1. **事实与建议分离**：本报告所有表格中的"证据"列只写文件行号、提交哈希或审查者实测输出；凡带"建议"字样的列均为可选项，不构成对作者决策的替代。报告未使用主观偏好表述。
2. **可复核性**：读者可用下列命令复现关键事实——`git rev-parse HEAD`、`git log --oneline -n 5`、`git show --stat 6c272ca`、`git show --stat d1ec5b1`、`git log --oneline main..member-b/implementation`、`Get-ChildItem -Recurse -File src -Filter *.java`、`$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; ./mvnw.cmd --batch-mode --no-transfer-progress clean verify`。
3. **审查者未做的事**（因此不在结论内）：未修复任何代码/文档；未提交/推送；未做远端克隆与离线构建；未访问 Canvas；未查看 8 张 PNG 的内容；未渲染 `.mmd`；未评估学生反思与签名（属本人义务，`docs/Project_Spec.md:571,603`）。
4. **快照限制**：仓库在审查期间仍在演进（12:40 `6c272ca` → 12:46:59 `d1ec5b1`，最终核验 12:50:57）。凡结论依赖具体文件集合或测试计数的，均已在正文标注 S0/S1；若在 12:50 之后又有提交，本报告的计数类结论需重新核验。
5. **对"21 vs 29 项测试"的处理**：两者分别对应 S0 与 S1，均与实测一致；审查者未把 S0 的 21 项当作 S1 的缺陷，也未把 S1 的 29 项回溯当作 S0 的证据。
6. **对作者判断的处理**：R-04 拒绝理由与 CR07-09 的冲突、`ai_usage_log` #8 的理由文本，本文只陈述"文本与基线/列语义存在差异"这一事实，并给出可选处理方式；是否改、如何改由 B 本人决定（`Shared…:191` 已明确"由 B 本人确认，不以规范代其确认"）。

---

## 附录 A：关键引用索引

**需求基线**
- `docs/Project_Spec.md:85`（扣分/评分条款）、`:120-127`（权限表）、`:255-260`（BR-21/22/25/26）、`:309-310`（TC-03/04）、`:347-362`（D2）、`:366-439`（AC 表）、`:582`（测试证据计划）。
- `docs/Project_ERD.md:615,620,631,634`（STOCK_ENTRY.commit_ordinal/receipt_review_id、LOSS_RECORD.source_line_id/disposition）。
- `docs/Shared_Boundaries_and_Interface_Spec.md:94-102`（三态与稳定错误码）、`:130-140`（IF-07 §5.3）、`:185,193`（A/B 代码位置）、`:191,202,207`（R-04/R-29 与违规解释）。

**B 设计/契约**
- `docs/archive/design_member_b/if07_contract.md:3,8,66,82,93-99,113-118,126,184,191-201`。
- `docs/archive/design_member_b/class_v2.mmd:51-62,105-112,267-329,331-336,418`。
- `docs/archive/design_member_b/design_notes.md:14,22,30-33,59,83,85`。
- `docs/archive/design_member_b/v2/README.md:4,5,7-18,28`。

**B 证据**
- `docs/evidence/member_b/design_review.md:4,23,32,45,47,48,52,53,62,71,87-97,99-133,108,137-140`。
- `docs/evidence/member_b/design_evolution.md:23-25,33-60,64-72,77-79,99`。
- `docs/evidence/member_b/implementation_log.md:3,5,15,19-22,35-39,43-45,48-53`。
- `docs/evidence/member_b/ai_usage_log_b.md:11,24,25,26,36-38,40-43`。
- `docs/evidence/member_b/framework_repro.md:12-13,28-32,37-39`。

**代码（S1）**
- `src/main/java/nz/ac/aut/pdc/supermarket/auth/{Role.java:8-16, LocalAuthService.java:20-59, SessionRef.java:15-17, Sha256PasswordHasher.java:9-28}`。
- `src/main/java/nz/ac/aut/pdc/supermarket/cui/{CuiApp.java:15,25-56, InputValidator.java:14-51}`。
- `src/main/java/nz/ac/aut/pdc/supermarket/stock/{StockService.java:24-124, StockValidator.java:25-92, StockOperations.java:12-27, StockPorts.java:14-39, SubmissionGateway.java:12-46, StockEntry.java:10-31, StockChangeLine.java:18-31, StockCheckResult.java:11-36, ReplenishmentService.java:12-50, LossService.java:11-58, LossRecord.java:11-34}`。
- `src/test/java/nz/ac/aut/pdc/supermarket/**`（7 个测试类，29 个 `@Test`；5 个 fixture）。

## 附录 B：本轮实测命令与输出（摘要）

```
$ git rev-parse HEAD
d1ec5b1c41ec0f8eb20910467d1415197403d5a5
$ git log --oneline -n 3
d1ec5b1 feat(member-b): add stock replenishment and loss use-case services
6c272ca feat(member-b): add auth/cui/stock skeleton with test doubles
c96a7e0 docs(member-b): confirm R-04 per shared spec and align IF-07 v0.3
$ git log --oneline main..member-b/implementation
d1ec5b1 / 6c272ca / c96a7e0 / 159266e        （main = 04abd6a）
$ $env:JAVA_HOME='C:\Program Files\Java\jdk-17'; ./mvnw.cmd --batch-mode --no-transfer-progress clean verify
[INFO] Tests run: 2,  … FrameworkSmokeTest
[INFO] Tests run: 7,  … LocalAuthServiceTest
[INFO] Tests run: 4,  … InputValidatorTest
[INFO] Tests run: 4,  … LossServiceTest
[INFO] Tests run: 4,  … ReplenishmentServiceTest
[INFO] Tests run: 4,  … StockServiceTest
[INFO] Tests run: 4,  … StockValidatorTest
[INFO] Tests run: 29, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

（构建产物写入被忽略的 `target/`；审查者未修改任何源文件或既有文档。）

## 附录 C：审查完成后的仓库变化（post-audit delta，仅事实记录）

本节记录 12:50:57 最终核验之后、审查者交回报告前观察到的变化。**这些内容未纳入上文审查结论**（审查者未阅读其内容、未运行其测试）。

| 时间（2026-10-21） | 变化 | 证据 |
|---|---|---|
| 12:51:57 | 新提交 `15bb46e docs(member-b): correct AI log #9 thinking level`（HEAD 由 `d1ec5b1` 变为 `15bb46e`） | `git log -1 --format='%h %ad %s'` |
| 12:52:51–12:53:37 | 工作区出现**未提交**的 `aftersale` 主代码 10 个文件（`Effect`、`OriginalSaleLine`、`QuotaCalculator`、`QuotaView`、`QuotaViewBuilder`、`QualityPairValidator`、`Redemption`、`RedemptionKind`、`Replacement`、`SettlementContext`）与测试 3 个文件（`QuotaCalculatorTest`、`QuotaViewBuilderTest`、`QualityPairValidatorTest`） | 目录枚举 + 文件时间戳 |
| 12:54:20 | 工作区仍有未提交修改：`docs/evidence/member_b/ai_usage_log_b.md`、`docs/evidence/member_b/implementation_log.md`；仓库根出现未跟踪 `build.log` | `git status --porcelain=v1` |
| 12:54:26 | 上述 `aftersale` 文件与两份 evidence 文档的修改被提交为 `d5a9ad1 feat(member-b): add aftersale quota and quality-pair calculations`（15 files/+564，含 3 个测试类） | `git show --stat d5a9ad1` |
| 12:54:46 | HEAD = `d5a9ad1`；工作区仅剩本报告 1 个未跟踪文件；`build.log` 仍未被跟踪（`git ls-files build.log` 无输出） | `git log`、`git status --porcelain=v1`、`git ls-files` |

由此产生三点必须如实说明的限制：

1. 本报告 §2 中"`aftersale` 包不存在""售后零实现"以及 §7.1 P0-2 的结论，**仅对 S0（`6c272ca`）与 S1（`d1ec5b1`）快照成立**。12:52 之后出现的 `aftersale` 文件是否与 V2、IF-07 v0.3、Spec §7.4 一致，其测试是否通过，均需在新快照上重新审查，本报告不作判定（审查者未阅读其实体内容，也未在新快照上重跑 `clean verify`）。
2. 本报告 §1.1"工作区干净"一句**只对应 12:50:57 的核验**；12:54:20 时工作区曾非干净（含未提交修改与未跟踪文件），12:54:46 又回到干净。
3. 报告的计数类结论（27 个主代码文件、950 行、29 项测试、`main` 落后 4 个提交）均以 S1 = `d1ec5b1` 为准；`d5a9ad1` 之后这些数字已变化（新增 10 个主代码文件与 3 个测试类），未在本报告重新统计。

另（事实 + 建议）：`build.log` 位于仓库根且**未被 `.gitignore` 覆盖**（`git check-ignore -v build.log` 返回非零；`.gitignore:2,10,11,18` 只覆盖 `/target/`、`hs_err_pid*.log`、`replay_pid*.log`、`/logs/`）。提交前需由作者决定删除或加入忽略规则，否则它可能被误提交进单 ZIP 交付包。
