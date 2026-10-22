# B 范围类图 V2 · 报告图集

> **CR07-10（2026-10-20）**：CR07-09店长资格例外已撤销。以下“含CR07-09同步”仅指历史图集；V2实现/图应删除AFTERSALE.exception_*，在REDEMPTION使用reason_text，不能继续按店长授权例外设计。

版本/日期：v1，2026-10-20（Asia/Shanghai）
设计基线：`docs/design/member_b/class_v2.mmd`（约 50 个类/接口/枚举，含 CR07-09 同步）
当前实现图：[`docs/design/final/`](../../final/README.md)（2026-10-22，由源码生成）；本目录是设计阶段的历史图集，不是最终实现快照。
本目录：报告/演示用分图（PNG，由 B 在 mermaid.live 按 `theme: base, layout: elk, fontSize: 18` 渲染后导出）。

## 图清单

| 文件 | 内容 | 报告建议位置 |
|---|---|---|
| `rendered/00_overview.png` | 总体关系图（核心服务与 A 接口依赖） | 正文 §3.3/§3.5 主图（Figure 3 或 Figure 2 对照） |
| `rendered/01_stock.png` | 库存模块详情（补货/报损/修正/库存核对） | 附录或 §3.5 支撑材料 |
| `rendered/02_aftersale.png` | 售后模块详情（退换/双额度/质量配对/CR07-09 资格例外） | 附录或 §3.5 支撑材料 |
| `rendered/03_reversal.png` | 撤销模块详情（D1 依赖/整单冲销/授权） | 附录 |
| `rendered/04_query.png` | 查询模块详情（权限范围/只读） | 附录 |
| `rendered/05_auth_cui.png` | 认证与交互入口（CUI/登录/角色） | 附录 |
| `rendered/06_shared.png` | 共享事务（模板方法/提交请求/变更集） | 附录 |
| `rendered/07_external.png` | 外部接口（IF-03/04/05/06/11） | 附录 |

## 说明

- 分图由权威源按模块裁剪/聚合生成，内容与 `class_v2.mmd` 一致；单图渲染仅用于可读性，不改变设计。
- 已按 A 的 CR07-09（店长授权资格个案例外）与 R-06（顾客交回损耗）修正：
  - 00 总体图：`customer-return loss` 指向 `CustomerReturnLossRecorder`；
  - 02 售后详情：`AftersaleCommand` 含 `exceptionAuthorizedBy/exceptionReason/relaxedRule`，`AftersaleService` 依赖 `Session` 做授权校验。
- 报告模板只要求"类、属性、方法、关系"及打印可读；建议正文只放总体图，模块详情放附录/支撑材料，避免图集过多影响页面。
- 图注示例：`Figure 3: Class diagram — Iteration 2 (final design, v2, 2026-10-20, B scope)`；分图另加 "Module detail: aftersale (v2)"。
- 与代码实现对应关系：V2 冻结后进入编码；若实现期调整结构，需同步更新 `class_v2.mmd` 与本图集并记录差异（AC-06）。
