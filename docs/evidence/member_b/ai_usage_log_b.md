# 成员B · AI 使用日志（工作台账）

版本/日期：v0.2，2026-10-20（Asia/Shanghai）
用途：记录 B 侧真实 GenAI 协作过程，供成员A汇总为报告 Appendix A（A.1/A.2/A.3）；本文件是工作台账，不是最终报告附录。
记录原则：只记实际发生的提示词、AI 产出和人的判断；不补造、不虚构 AI 错误；模型/版本不确定写"不详"；不含本机绝对路径、账号密码或个人隐私；AI 输出不得直接充当 §2.6/§3.7 学生本人反思。

## A.1 使用的 GenAI 工具（B 侧）

| 工具 | 模型/版本 | 主要用途 |
|---|---|---|
| DeepSeek Harness（DSH，版本 0.2.1-rc.2） | deepseek-flash（版本 1017）；思考程度 high（#1–#4）/ max（#5） | 环境与构建核对、框架复现、设计辅助、代码实现与调试、评审材料整理 |

## A.2 提示词日志（B 侧）

| # | 阶段 | 工具/模型 | 主要提示词（摘要） | GenAI 生成的内容 | 我修改/拒绝了什么及原因 |
|---|---|---|---|---|---|
| 1 | 交接-环境与框架复现 | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=high） | 核对 Git 与开发环境、切 JDK17 并复现项目框架 clean verify | Git 工作区/HEAD/远端一致性核对；JDK 17.0.12、Maven 3.9.11（Wrapper 3.3.4）确认；在线与离线 `clean verify` 均 BUILD SUCCESS、2 项测试通过；从远端全新克隆后离线构建同样通过 | B 端默认 JDK 为 21，与开发要求的 JDK 17 冲突；要求在开发过程中显式切换为 JDK17，不接受以默认 JDK21 为基础的构建 |
| 2 | 设计-交接接受与依赖登记 | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=high） | 按交接包 v0.4 书面接受交接，登记 WP-03/05/06 依赖；缺口逐项登记，不把未批准事项写成已定 | 交接接受核对表；三包 FR/BR/AC、依赖接口、共享资产边界、缺口清单；缺失输入与可先推进顺序（写入 `docs/design/member_b/design_notes.md`） | 要求把 A 侧未交付契约全部标为未取得 |
| 3 | 设计-IF-07 基础契约 | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=high） | 按要求起草 IF-07 基础契约，注意文档要点，先交稳定契约供 A 确认调用边界 | IF-07 契约 v0.1 草案（同次上下文/基线、S-W 变动组、来源与转移关联、候选输出、拒绝语义、统一保存边界、幂等、共享数据、A 确认清单、未定项）；按审计要求升级为 v0.2（每节标注来源、标记本文补充） | 让契约草案每一节都标注来源，从而让A 审计时能直接逐条对回原文；不引入原文之外的新规则; 提升文档的可读性便于A审计 |
| 4 | 设计-类图 V1 | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=high） | 按要求起草 B 范围类图 V1；按本人要求补 SnapshotAccess、拆 QuotaCalculator/QuotaView、继续细化拆分、修正 Mermaid 语法与布局 | 18 框简化版与 32 框拆分版 Mermaid 代码、包分组、接口/组合关系、渲染图 | 本人选择 Mermaid 而非手绘；删除 AI 底稿中的非法注释行（SubmissionsPortNote）；要求加 SnapshotAccess（uses IF-04）、Object 改为待 A 契约的具体类型、拆出 QuotaCalculator/QuotaView 并细化拆分；自行渲染并调整布局 |
| 5 | 设计-类图 V1 真实 AI 评审 | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=max） | 以资深从业者视角评审 V1，重点业务逻辑合理性与落地可行性，逐条意见写入 `docs/evidence/member_b/design_review.md` | 29 条评审意见（8 项 P0、11 项 P1、8 项 P2、2 项 P3）+ 最小修复集 + AC 覆盖风险表 | B 于 2026-10-20 完成逐条审批：20 采纳、7 修改后采纳、2 拒绝（R-04 售后资格策略类、R-27 图面排版）；逐条理由见 `design_review.md` §7；V2 按决定修改 |
| 6 | 设计-类图 V2 修订 | DeepSeek Harness 0.2.1-rc.2 / deepseek-flash 1017（thinking=max） | 按已完成的审批决定修改 V2 类图，并记录 V1→V2 演进与裁决 | `class_v2.mmd`（约 50 类：BusinessTransaction 模板、AftersaleCommand/Assembler、QuotaLedgerReader/ViewBuilder、Effect/RedemptionKind、SubmissionRequest、cui/auth、DependencyResult 等）；`design_evolution.md`（27 项变化、裁决记录、待 A 输入） | [待 B 审核 V2 后确认；R-04/R-29 冲突按 R-04 优先处理，待 B 确认] |

对应证据：
- #1 `docs/evidence/member_b/framework_repro.md`（2026-10-20）
- #2 `docs/design/member_b/design_notes.md`（2026-10-20）
- #3 `docs/design/member_b/if07_contract.md`（2026-10-20，v0.2）
- #4 `docs/design/member_b/class_v1.mmd`、`class_v1.png`（2026-10-20，v1）
- #5 `docs/evidence/member_b/design_review.md`（2026-10-20）
- #6 `docs/design/member_b/class_v2.mmd`、`docs/evidence/member_b/design_evolution.md`（2026-10-20）

## 待补（按阶段追加，不预先填写）

- A.2 后续条目：本人对评审的逐条决定、类图 V2 与差异、模块实现、调试、AC 执行、报告装配。
- A.3 人机贡献划分：各阶段结束后按事实整理（主要由人工设计 / 主要由 AI 生成后经审核），在报告装配时提交给 A 汇总。
