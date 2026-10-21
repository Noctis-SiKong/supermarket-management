# 成员B · AI 使用日志（工作台账）

版本/日期：v0.2，2026-10-20（Asia/Shanghai）
用途：记录 B 侧真实 GenAI 协作过程，供成员A汇总为报告 Appendix A（A.1/A.2/A.3）；本文件是工作台账，不是最终报告附录。
记录原则：只记实际发生的提示词、AI 产出和人的判断；不补造、不虚构 AI 错误；模型/版本不确定写"不详"；不含本机绝对路径、账号密码或个人隐私；AI 输出不得直接充当 §2.6/§3.7 学生本人反思。

## A.1 使用的 GenAI 工具（B 侧）

| 工具 | 模型/版本 | 主要用途 |
|---|---|---|
| DeepSeek Harness（DSH，版本 0.2.1-rc.2） | deepseek-flash（版本 1017）；思考程度 high（#1–#4、#7–#8、#12、#16、#19）/ max（#5–#6、#9–#11、#13–#15、#17–#18、#20–#30） | 环境与构建核对、框架复现、设计辅助、代码实现与调试、评审材料整理 |

## A.2 提示词日志（B 侧）

| # | 阶段 | 工具/模型 | 主要提示词（摘要） | GenAI 生成的内容 | 我修改/拒绝了什么及原因 |
|---|---|---|---|---|---|
| 1 | 交接-环境与框架复现 | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=high） | 核对 Git 与开发环境、切 JDK17 并复现项目框架 clean verify | Git 工作区/HEAD/远端一致性核对；JDK 17.0.12、Maven 3.9.11（Wrapper 3.3.4）确认；在线与离线 `clean verify` 均 BUILD SUCCESS、2 项测试通过；从远端全新克隆后离线构建同样通过 | B 端默认 JDK 为 21，与开发要求的 JDK 17 冲突；要求在开发过程中显式切换为 JDK17，不接受以默认 JDK21 为基础的构建 |
| 2 | 设计-交接接受与依赖登记 | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=high） | 按交接包 v0.4 书面接受交接，登记 WP-03/05/06 依赖；缺口逐项登记，不把未批准事项写成已定 | 交接接受核对表；三包 FR/BR/AC、依赖接口、共享资产边界、缺口清单；缺失输入与可先推进顺序（写入 `docs/design/member_b/design_notes.md`） | 要求把 A 侧未交付契约全部标为未取得 |
| 3 | 设计-IF-07 基础契约 | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=high） | 按要求起草 IF-07 基础契约，注意文档要点，先交稳定契约供 A 确认调用边界 | IF-07 契约 v0.1 草案（同次上下文/基线、S-W 变动组、来源与转移关联、候选输出、拒绝语义、统一保存边界、幂等、共享数据、A 确认清单、未定项）；按审计要求升级为 v0.2（每节标注来源、标记本文补充） | 让契约草案每一节都标注来源，从而让A 审计时能直接逐条对回原文；不引入原文之外的新规则; 提升文档的可读性便于A审计 |
| 4 | 设计-类图 V1 | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=high） | 按要求起草 B 范围类图 V1；按本人要求补 SnapshotAccess、拆 QuotaCalculator/QuotaView、继续细化拆分、修正 Mermaid 语法与布局 | 18 框简化版与 32 框拆分版 Mermaid 代码、包分组、接口/组合关系、渲染图 | 本人选择 Mermaid 而非手绘；删除 AI 底稿中的非法注释行（SubmissionsPortNote）；要求加 SnapshotAccess（uses IF-04）、Object 改为待 A 契约的具体类型、拆出 QuotaCalculator/QuotaView 并细化拆分；自行渲染并调整布局 |
| 5 | 设计-类图 V1 真实 AI 评审 | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=max） | 以资深从业者视角评审 V1，重点业务逻辑合理性与落地可行性，逐条意见写入 `docs/evidence/member_b/design_review.md` | 29 条评审意见（8 项 P0、11 项 P1、8 项 P2、2 项 P3）+ 最小修复集 + AC 覆盖风险表 | B 于 2026-10-20 完成逐条审批：20 采纳、7 修改后采纳、2 拒绝（R-04 售后资格策略类、R-27 图面排版）；逐条理由见 `design_review.md` §7；V2 按决定修改 |
| 6 | 设计-类图 V2 修订 | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=max） | 按已完成的审批决定修改 V2 类图，并记录 V1→V2 演进与裁决 | `class_v2.mmd`（约 50 类：BusinessTransaction 模板、AftersaleCommand/Assembler、QuotaLedgerReader/ViewBuilder、Effect/RedemptionKind、SubmissionRequest、cui/auth、DependencyResult 等）；`design_evolution.md`（27 项变化、裁决记录、待 A 输入） | A 已审核并合入 main（`7216724`→`04abd6a`）；R-04/R-29 仍待 B 正式记录 |
| 7 | 交接-共享规范 v1.02 同步 | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=high） | 拉取 A 审核后的最新分支，核对共享接口规范 v1.02 并同步项目最新进展 | 识别 main `04abd6a` 新增 `docs/Shared_Boundaries_and_Interface_Spec.md` v1.02 与 README；列出 B 需适配的契约差异（IF-01/04/05/07/11、SessionRef/WorkingCopy/ChangeSet/三态结果、R-04/R-29、编码门、代码位置）；给出 B 下一步 P0/P1 与时间风险 | 要求本步同步必须留痕并记入 AI 日志；确认前不开始编码（R-04/R-29 口径仍待确认） |
| 8 | 设计-R-04 执行口径与 IF-07 v0.3 | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=high） | 以 A 的共享规范思路执行 R-04，并修订 IF-07 契约 v0.3 | R-04 按 A 规范 §8 确认：系统默认资格校验 + CR07-09 例外留痕、不抽策略类；IF-07 v0.3 对齐 §5.3（取消 source_locations、balances/check、overlay 只合并一次、三态）；同步更新 design_review/design_evolution/design_notes | 以 A 的想法执行（R-04 维持不抽策略类；系统简单校验+例外留痕；IF-07 按 §5.3 对齐） |
| 9 | 实现-B 骨架（auth/cui/stock） | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=max） | 用只读快照/捕获式提交/固定 ID 时钟替身开发骨架，注意接口规范、可读性与高内聚低耦合 | auth：Role/SessionRef/AuthResult/AuthService/CredentialStore/PasswordHasher/Sha256PasswordHasher/LocalAuthService；cui：InputValidator/CuiApp；stock：Position/StockBalance/StockChangeLine/StockEntry/StockCheckResult/StockPorts/SubmissionGateway/StockOperations/StockValidator/StockService；测试替身 5 个与 4 个测试类；`clean verify` BUILD SUCCESS、21 项测试通过 | 要求按 A 共享规范思路、用替身开发骨架；注重接口规范和可读性、高内聚低耦合；本步仅骨架，未证明真实持久化/AC |
| 10 | 实现-stock 用例服务（补货/报损） | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=max） | 保持节奏进行下一步开发；同时安排 thinking=high 的独立子智能体客观审查既有内容 | `ReplenishmentService`、`LossService`、`LossRecord`、`LossOrigin`、`LossState`、`ReplenishmentResult`、`LossResult` 及 8 项测试；`clean verify` BUILD SUCCESS、29 项测试通过 | 要求保持节奏、下一步开发，并启动客观审查；本步仍用替身、不提交、不冒充真实 AC |
| 11 | 实现-aftersale 纯计算（额度/质量配对） | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=max） | 继续进行下一步开发：售后额度视图、退款金额计算、质量零差配对校验 | `OriginalSaleLine/RedemptionKind/Effect/Redemption/Replacement/QuotaView/SettlementContext/QuotaViewBuilder/QuotaCalculator/QualityPairValidator` + 3 个测试类；`clean verify` BUILD SUCCESS、42 项测试通过 | 按要求继续开发；本步纯函数/模型，不读 A 代码、不提交、未执行 AC |
| 12 | 审查-独立子智能体全面审查 | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（子智能体，thinking=high） | 以完全客观、证据化方式审查既有内容（需求追溯/契约一致/代码/测试/留痕/交付风险），不修改源码与既有文档 | 报告 `independent_review_2026-10-21.md`：P0=无可运行 CUI、售后/冲销/查询零实现（S1 快照）、V2-实现不同步；P1=IF-07 文档两套输出/错误码、`eventKey` 重算、`receiptReviewId` 缺失、`INTERNAL_ERROR` 非规范码等；含证据行号/提交哈希 | 要求客观、无主观色彩；报告如实记录快照与限制；本步不修改代码 |
| 13 | 修复-审查发现（契约/代码） | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=max） | 按审查发现修复可直接处理项 | `StockChangeLine`/`StockEntry` 增 `eventKey`/`receiptReviewId` 并传递；内部异常码改 `STOCK_CHECK_FAILED`；`changes==null`→INVALID_INPUT；IF-07 §4/§5 加覆盖提示、§14.5 登记新码、证据补 #8；`design_review` 补 R-04 覆盖说明；`design_evolution` 关闭待确认；43 项测试通过 | 要求继续开发并处理审查发现；剩余 P0（CUI 主类、售后编排、V2 同步）待协作决策 |
| 14 | 实现-售后编排骨架 | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=max） | 按"先做第一项"实现售后编排：资格 → 额度 → 核销/替换品 → 替换品库存核对 | `AftersaleCommand/AftersaleCommandLine/AftersaleResult/AftersaleAssembler/AftersaleService` + 端口 `OriginalSaleReader/QuotaLedgerReader/ProductSnapshot/ProductSnapshotReader`；9 项测试；累计 52 项测试通过 | 要求先做第一项（售后编排）；不提交、不保存、未执行 AC |
| 15 | 实现-V2.1 同步 + CUI 菜单 | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=max） | 按建议执行 V2.1 设计-实现同步与 CUI 业务菜单接入 | `v2_1/class_v2_1.mmd` + `v2_1/README.md`（V2 目标→实现差异、AC-06 口径）；`CuiActor/OperationFeedback/Operations/UnavailableOperations` + `CuiApp` 菜单重构；`CuiAppTest` 4 项；累计 56 项测试通过 | 按建议执行；未接真实 A 适配时明确失败、不伪造数据；V2.1 图待渲染；未执行 AC |
| 16 | 文档-V2.1 分图源文件落库 | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=high） | 将总览/auth-cui/stock/aftersale 四张分图源文件落库并补 README/图注/渲染核对 | `split/00_overview.mmd`、`01_auth_cui.mmd`、`02_stock.mmd`（补 LossOrigin/LossState 枚举记号）、`03_aftersale.mmd`；README §5 图 A–D/命名/裁切核对；未生成 PNG | 按"可以"落库；要求 thinking=high；PNG 仍由 B 渲染，未执行 AC |
| 17 | 实现-query / reversal 骨架 | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=max） | 按用户要求开始 query / reversal 骨架 | `QueryOperations/QueryService/QueryResult/SaleLineView`；`ReversalService/ReversalPlan/ReversalCommand/ReversalResult` + 端口 `RedemptionReader/ReversalDependencyReader/StockEffectReader`；5+7 项测试；累计 68 项通过 | 要求 thinking=max；只读/不提交；D1 仅阻断不级联；新增 B 业务码待通知 A；未执行 AC |
| 18 | 实现-独立可完成规则收口 | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=max） | 按用户要求把 B 独立可完成规则补齐并保证质量 | Redemption 例外字段；同原行多明细顺序额度；SettlementCalculator；LossRecord.dispose；LossRecordView/Reader；DependencyChecker；CUI 测试；AC 索引；84 项通过 | 仍用替身；D1 不级联；顾客交回/真实 A 未接；未执行 AC |
| 19 | 协调-B→A Git 对齐消息 | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=high） | 在 Git 留正式 B→A 对齐消息，列清 B 状态、A 需交付/裁决项与回复方式 | `docs/coordination/B_TO_A_ALIGNMENT_2026-10-21.md`：R1 shared/IF-04/05、R2 包路径、R3 IF-07 balances 签名、R4 业务码、R5 IF-11、R6 CUI 入口/pom、R7 分支合并、R8 结果类型映射；HEAD 68f7c51、84 项测试 | 用户要求在 git 里留消息和 A 对齐；未改代码；等待 A 逐条回复 |
| 20 | 集成-合并 main + CR07-10 迁移 | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=max） | 在隔离分支合并 main，并迁移 CR07-09 到 CR07-10 | merge ef5eea1；主代码/测试 CR07-09 例外 → reasonKind/reasonText；CUI 其他原因文本；合并树 120 tests / 0 failed / 0 error / 1 skipped；文档同步 | 用户要求开始补救；不直接推 main；真实端口适配（G3）未开始 |
| 21 | 集成-IF-07 A 适配器 | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=max） | 在合并分支实现 A StockDependency 的真实适配 | SharedWorkingCopyView/SharedStockProjection/SharedStockAdapter；5 项测试；125 tests / 0 failed / 0 error / 1 skipped | 用户继续 Stage 3；只读/不提交；CommitValidator/认证/CUI 未做 |
| 22 | 集成-六参 CommitValidator/草稿删除 | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=max） | 实现 AFTERSALE_DRAFT_DELETE 与 stock 六参 CommitValidator | AftersaleDraftDeletionValidator + SharedStockCommitValidator；5 项测试；130 tests / 0 failed / 0 error / 1 skipped | 用户继续 Stage 3；A 的 DraftDeletionGuard 仍负责结构红线；未注册最终装配 |
| 23 | 集成-售后 ChangeSet/提交入口 | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=max） | 把 B 售后候选组装为 A ChangeSet 并接 SubmissionPort | AftersaleChangeSetAssembler + AftersaleSubmissionService；2 项测试；132 tests / 0 failed / 0 error / 1 skipped | 用户继续 Stage 3；未嵌套 submit；AFTERSALE validator/认证/CUI 未接 |
| 24 | 集成-AFTERSALE 校验器/注册表 | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=max） | 补 AFTERSALE 六参校验器与注册表 | AftersaleCommitValidator + BCommitValidators；SharedStockAdapter 空 stock 修复；4 项测试；136 tests | 用户继续 Stage 3；注册表未交 A JsonStore；认证/CUI 未接 |
| 25 | 集成-认证适配/JsonStore 集成 | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=max） | 接 B 认证到 A IdentityResolver，并验证真实 JsonStore.open | SharedIdentityResolver + SharedCredentialStore + SharedAuthJsonStoreTest；5 项测试；141 tests | 用户继续 Stage 3；未做 Main/CUI 真实装配 |
| 26 | 集成-售后读取适配 | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=max） | 把 B 售后读取端口接到 A WorkingCopy | SharedSaleReader/SharedQuotaLedgerReader/SharedProductSnapshotReader + SharedAftersaleReadTest；2 项测试；143 tests | 用户继续 Stage 3；CUI/Main 未做 |
| 27 | 集成-BApplication 装配/真实 JSON 提交 | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=max） | 装配 A JsonStore + B 认证/校验器并跑真实提交 | BApplication + BApplicationTest；1 项测试；144 tests / 0 failed / 0 error / 1 skipped | 用户继续 Stage 3；CUI/Main 未做；A 的稳定性/规则仍由 A 组件执行 |
| 28 | 集成-CUI 真实操作/售后链 | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=max） | 把 CUI 会话接到 A open/submit，售后走真实服务 | CuiActor+SessionRef、SharedOperations、SharedProductCatalog、SharedIdGenerator、BApplication.operations；2 项测试；146 tests | 用户继续 Stage 3；补货/报损未接线；Main/pom 未接 |
| 29 | 集成-CUI 售后完整链 | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=max） | 用 A 种子数据跑通 CUI 售后到 A submit | SharedOperationsIntegrationTest + SharedStockAdapter.review 修正；1 项测试；147 tests | 用户继续；Main/pom 仍待 A；补货/报损未接 |
| 30 | 代码审查修复-售后链 | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=max） | 修复 review 的换货、款项符号、校验器意图/30天、核销行与角色/时钟装配 | AftersaleRequest + CUI 换货输入；MoneyEntry 符号；validator request/acceptedAt；SharedRoles/IdClock；147 tests | 用户要求修；IF-11/CR07-11、幂等、恢复校验未修 |

对应证据：
- #1 `docs/evidence/member_b/framework_repro.md`（2026-10-20）
- #2 `docs/design/member_b/design_notes.md`（2026-10-20）
- #3 `docs/design/member_b/if07_contract.md`（2026-10-20，v0.2）
- #4 `docs/design/member_b/class_v1.mmd`、`class_v1.png`（2026-10-20，v1）
- #5 `docs/evidence/member_b/design_review.md`（2026-10-20）
- #6 `docs/design/member_b/class_v2.mmd`、`docs/evidence/member_b/design_evolution.md`（2026-10-20）
- #7 `docs/Shared_Boundaries_and_Interface_Spec.md`（v1.02）、`docs/design/member_b/design_notes.md` §8（2026-10-20 同步）
- #8 `docs/design/member_b/if07_contract.md`（v0.3）、`docs/evidence/member_b/design_evolution.md`（v1.2）、`docs/design/member_b/design_notes.md` §8（2026-10-21）
- #9 `src/main/java/nz/ac/aut/pdc/supermarket/{auth,cui,stock}/`、`src/test/java/nz/ac/aut/pdc/supermarket/{auth,cui,stock,fixture}/`、`docs/evidence/member_b/implementation_log.md`（2026-10-21）
- #10 `src/main/java/nz/ac/aut/pdc/supermarket/stock/{ReplenishmentService,LossService,LossRecord,LossOrigin,LossState,ReplenishmentResult,LossResult}.java`、对应测试类、`docs/evidence/member_b/implementation_log.md`（2026-10-21，29 项测试）
- #11 `src/main/java/nz/ac/aut/pdc/supermarket/aftersale/`、对应测试类、`docs/evidence/member_b/implementation_log.md`（2026-10-21，42 项测试）
- #12 `docs/evidence/member_b/independent_review_2026-10-21.md`（2026-10-21，独立子智能体审查报告）
- #13 源码修复（StockChangeLine/StockEntry/StockService）、`docs/design/member_b/if07_contract.md`、`docs/evidence/member_b/{design_review,design_evolution,implementation_log}.md`（2026-10-21，43 项测试）
- #14 `src/main/java/nz/ac/aut/pdc/supermarket/aftersale/{AftersaleCommand,AftersaleCommandLine,AftersaleResult,AftersaleAssembler,AftersaleService,OriginalSaleReader,QuotaLedgerReader,ProductSnapshot,ProductSnapshotReader}.java`、`AftersaleServiceTest`、`docs/evidence/member_b/implementation_log.md` §9（2026-10-21，52 项测试）
- #15 `docs/design/member_b/v2_1/`、`src/main/java/nz/ac/aut/pdc/supermarket/cui/{CuiActor,OperationFeedback,Operations,UnavailableOperations,CuiApp}.java`、`CuiAppTest`、`implementation_log.md` §10（2026-10-21，56 项测试）
- #16 `docs/design/member_b/v2_1/split/`、`docs/design/member_b/v2_1/README.md` §5、`implementation_log.md` §11（2026-10-21，thinking=high）
- #17 `src/main/java/nz/ac/aut/pdc/supermarket/{query,reversal}/`、对应测试类、`implementation_log.md` §12（2026-10-21，68 项测试）
- #18 `src/main/java/nz/ac/aut/pdc/supermarket/{aftersale,stock,query,reversal}/` 规则补全、对应测试、`docs/evidence/member_b/ac_coverage_b.md`、`implementation_log.md` §13（2026-10-21，84 项测试）
- #19 `docs/coordination/B_TO_A_ALIGNMENT_2026-10-21.md`（2026-10-21，B→A 对齐消息）
- #20 `member-b/merge-main-2026-10-21` merge `ef5eea1`、CR07-10 代码/测试/文档（2026-10-21，120 项测试）
- #21 SharedStockAdapter/SharedWorkingCopyView/SharedStockProjection、`SharedStockAdapterTest`、`implementation_log.md` §15（2026-10-21，125 项测试）
- #22 AftersaleDraftDeletionValidator/SharedStockCommitValidator、`AftersaleDraftDeletionValidatorTest`、`implementation_log.md` §16（2026-10-21，130 项测试）
- #23 AftersaleChangeSetAssembler/AftersaleSubmissionService、对应测试、`implementation_log.md` §17（2026-10-21，132 项测试）
- #24 AftersaleCommitValidator/BCommitValidators、`AftersaleCommitValidatorTest`、`implementation_log.md` §18（2026-10-21，136 项测试）
- #25 SharedIdentityResolver/SharedCredentialStore、3 个认证测试、`implementation_log.md` §19（2026-10-21，141 项测试）
- #26 SharedSaleReader/SharedQuotaLedgerReader/SharedProductSnapshotReader、`SharedAftersaleReadTest`、`implementation_log.md` §20（2026-10-21，143 项测试）
- #27 BApplication/BApplicationTest、`implementation_log.md` §21（2026-10-21，144 项测试）
- #28 CuiActor+SessionRef/SharedOperations/SharedProductCatalog/SharedIdGenerator、`implementation_log.md` §22（2026-10-21，146 项测试）
- #29 SharedOperationsIntegrationTest/SharedStockAdapter.review 修正、`implementation_log.md` §23（2026-10-21，147 项测试）
- #30 review 修复：AftersaleRequest/CuiApp 换货、款项符号、validator request/30天、SharedRoles/IdClock、`implementation_log.md` §24（2026-10-21，147 项测试）

## 待补（按阶段追加，不预先填写）

- A.2 后续条目：本人对评审的逐条决定、类图 V2 与差异、模块实现、调试、AC 执行、报告装配。
- A.3 人机贡献划分：各阶段结束后按事实整理（主要由人工设计 / 主要由 AI 生成后经审核），在报告装配时提交给 A 汇总。

















