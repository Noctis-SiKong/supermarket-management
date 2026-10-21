# B 侧 AC 验收汇总（2026-10-21）

> 2026-10-21 v3 当前状态：完整单体与正式 CUI 已统一接入，详见仓库 docs/P1_Implementation_Log.md。下文保留形成时点的历史结论、数字及发布回执，不表示本轮仍“等待 A/B”，也不授予本轮推送权限。
> 范围：`CGwal` 负责的 B 侧（auth/cui/stock/aftersale/query/reversal）。
>
> 口径：本汇总的 PASS 均为**自动化测试证据**，不是课程正式验收签字；依赖 A 的接口、Main/pom、恢复注入、人工视频/报告的项目仍按 PARTIAL/BLOCKED 记录。
>
> 详细逐项证据见 [`ac_execution_b_2026-10-21.md`](ac_execution_b_2026-10-21.md)。

## 1. 基线

| 项 | 值 |
|---|---|
| 分支 | `member-b/merge-main-2026-10-21` |
| 汇总提交 | `b1e8b77` |
| 构建 | `./mvnw -B clean verify` |
| 结果 | **176 tests / 0 failed / 0 error / 1 skipped**，BUILD SUCCESS |
| 1 skipped | A `AttachmentTest` 的符号链接平台差异 |
| 真实存储 | 集成测试使用临时目录的真实 `JsonStore`，不写生产 `data/` |

## 2. 结论摘要

- **PASS（自动化）21 项**：B 侧可独立验证的核心业务规则、金额/库存/售后/冲销路径已跑通。
- **PARTIAL 若干**：有模块/集成证据，但缺正式 CUI 演示、真实权限矩阵、故障注入或逐项数据复现。
- **BLOCKED 4 项**：AC-02（Main/pom）、AC-07/08（视频/报告/ZIP）、AC-85（A 恢复/单写者）。
- **AC-87/88/90**：B 侧草稿/追溯/取消/幂等已实现，但 A 的 IF-11 正式受控读取端口/真实中断注入未交付，不能宣称正式联调通过。

## 3. PASS（自动化）21 项

| AC | B 侧覆盖点 | 证据 | 限制 |
|---|---|---|---|
| AC-01 | 构建/范围 | 全量 `clean verify` | 干净克隆/离线需报告复跑 |
| AC-05 | CUI 非法输入/退出 | `InputValidatorTest`、`CuiAppTest` | 真实服务入口受 AC-02 限制 |
| AC-09 | 单件实付 8 元退货退 8 | `AftersaleServiceTest.ac09...` | 未做真实 CUI 离线店长场景 |
| AC-12 | 补货后 S/W 余额 | `StockOperationsIntegrationTest`：S5/W10→S8/W7 | 未接正式 CUI |
| AC-17 | 退款按原实付 | `AftersaleServiceTest.ac09...`：退 8 而非 10/12 | 未接正式 CUI |
| AC-19 | 开封非质量/其他原因 | `AftersaleServiceTest.opened...`、`managerCannotOverride...` | — |
| AC-21 | 小数数量拒绝 | `InputValidatorTest`、`CuiAppTest.invalidQuantity...` | — |
| AC-30 | 期初+补货余额重建 | `StockOperationsIntegrationTest.ac30...`：W10→W7/S3，合计 10 | 未做重启重建 |
| AC-33 | 已退已换后再退 | `AftersaleServiceTest.ac33...`：退1+换1后拒再退2 | 重复提交由同键重试覆盖 |
| AC-34 | 换货补/退/零差 | `AftersaleServiceTest.ac34...`：55→补7、30→退18、48→零差 | 未接真实 CUI/库存提交 |
| AC-35 | 整单冲销恢复 | `ReversalIntegrationTest`：S 恢复 10、原单 VOIDED、重复冲销失败 | 未接正式 CUI 演示 |
| AC-46 | 原行顺序部分退款 | `AftersaleServiceTest.ac46...`：3.33+3.33+3.34=10.00 | 未接正式 CUI |
| AC-47 | 第 30/31 天边界 | `AftersaleServiceTest.ac47...`：上海 10-31 23:59:59 通过、11-01 00:00 拒绝 | — |
| AC-52 | D1 阻断旧收货 | `ReversalReceiptDependencyTest`：W+10 后 W−3 阻断 | 未接正式 CUI |
| AC-58 | 零补差换货占满额度 | `AftersaleServiceTest.ac58...`：换1后再退被拒 | 未接正式 CUI |
| AC-79 | 3 条各 1 件尾差 | `AftersaleServiceTest.ac79...`：3.33/3.33/3.34 | — |
| AC-80 | 历史额度不回写 | `AftersaleServiceTest.ac80...`：历史 3.33 后本次 3.33 | — |
| AC-82 | 同单独立资格事实 | `AftersaleServiceTest.ac82...`：opened/quality/reason 独立保留 | 未接真实提交 |
| AC-83 | D1 整单拒绝 | `ReversalReceiptDependencyTest`：W− 阻断旧收货整单冲销 | 未接正式 CUI |
| AC-84 | 后续正收货不阻断 | `ReversalReceiptDependencyTest`：W+5 允许冲销 | 未接正式 CUI |
| AC-89 | 已追溯草稿取消 | `AftersaleDraftServiceTest`：CANCELLED、READ 保留、无退款/库存/核销 | 未接正式 CUI 演示 |

## 4. PARTIAL（按优先级）

| 优先级 | AC | 缺口 | 下一步 |
|---|---|---|---|
| P1 | AC-56/57 | 先换后退/双额度完整真实提交链、幂等新键 | 增加真实 `JsonStore` 提交与重试测试 |
| P1 | AC-81 | 先换后退两种录入顺序未逐项断言 | 补顺序交换测试，断言换优先占额 |
| P1 | AC-51/53 | 三角色权限矩阵、全部输入边界未逐项 | 扩 CUI/输入测试或正式 CUI 联调 |
| P2 | AC-54/55 | 促销分摊/快照退款依赖 A 销售链 | 与 A 对齐后做真实销售→退货联调 |
| P2 | AC-86 | 期初三角色权限真实 CUI 未验 | 接 CUI 后补 |
| P2 | AC-10/13/14/15/22/37 | 模块证据存在，但未逐字复现 AC 数据 | 按 AC 数据逐项补测试 |
| P2 | AC-87/88 | 草稿/追溯已实现，A 的 IF-11 正式端口未确认 | 等 A；不宣称正式联调 |
| P3 | AC-90 | 同键重试已测，缺真实进程中断注入 | 需要 A 的恢复/故障注入能力 |

## 5. BLOCKED

| AC | 阻塞原因 | 归属 |
|---|---|---|
| AC-02 | 无 `Main`/pom 启动入口，`java -jar` 不可演示 | A-01 |
| AC-07 | 5 分钟视频未录制 | B 人工材料 |
| AC-08 | 报告/ZIP/反思声明未完成 | 全组 |
| AC-85 | 崩溃恢复/单写者/写入阻断依赖 A IF-04/05 | A |

## 6. 明确不声称

1. 不声称全部 AC 已通过；仅 21 项有本轮自动化 PASS 证据。
2. 不声称正式课程验收完成；报告、视频、ZIP、Canvas 提交仍未做。
3. 不声称 AC-87/88 已与 A 正式联调；B 侧只有草稿/追溯编排与本地真实 JSON 测试。
4. 不把 `176 tests` 等同于 AC 全量通过。

## 7. 复跑命令

```powershell
./mvnw -B clean verify
```

关键测试类：

- `AftersaleServiceTest`（21）
- `AftersaleDraftServiceTest`
- `ReversalIntegrationTest`
- `ReversalReceiptDependencyTest`
- `ReversalCommitValidatorTest`
- `StockOperationsIntegrationTest`
- `SharedOperationsIntegrationTest`
- `CuiAppTest` / `InputValidatorTest`

## v3 统一实现补记（2026-10-21）

当前代码交付 `4a67050`；真实 CUI 的退款、整单冲销及原键跨重启重放已由 FullRuntimeTest 验证，AC-35/87/88/90 不再因“未接正式 CUI／等待 A”而阻塞编码。完整命令、真实链/替身范围及尚未执行的人工材料统一见 docs/P1_Implementation_Log.md。本补记不改写上方历史 PASS/PARTIAL/BLOCKED 形成时点的证据，不代替用户或课程验收。仅本地提交，未上传 GitHub。
