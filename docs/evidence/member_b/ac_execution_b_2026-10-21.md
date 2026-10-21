# B 侧 AC 执行记录（2026-10-21）

> 口径：本记录是 B 侧按 `docs/Project_Spec.md` 执行的**模块/集成级验收证据**，不等同课程正式验收签字，也不把 A 尚未交付的 IF-11/启动入口记作通过。
>
> 状态定义：
> - **PASS（自动化）**：本轮 `clean verify` 或专项测试真实执行，覆盖 AC 给定核心数据；仍需报告/视频抽查。
> - **PARTIAL**：有自动化证据但只覆盖部分场景，或依赖真实 CUI/持久化/权限联调。
> - **BLOCKED**：缺 A 入口/接口或人工材料，当前不能执行。
> - **N/A**：已撤销或非 B 范围。

## 1. 环境与结果

| 项 | 值 |
|---|---|
| 分支 | `member-b/merge-main-2026-10-21` |
| 提交 | `f4ed044` |
| JDK | 17 |
| 构建 | `./mvnw -B clean verify` |
| 结果 | **172 tests / 0 failed / 0 error / 1 skipped**，BUILD SUCCESS |
| 说明 | 1 skipped 为 A `AttachmentTest` 的符号链接平台差异 |

## 2. B 相关 AC 状态

| AC | B 相关场景 | 执行证据 | 状态 | 缺口 |
|---|---|---|---|---|
| AC-01 | 构建/范围 | 全量 `clean verify` | PASS（自动化） | 干净克隆/离线环境需报告复跑 |
| AC-02 | CUI 可运行入口 | 无 `main`/pom 启动配置 | BLOCKED | A-01 |
| AC-03 | 持久化后重启读取 | `BApplicationTest`、`SharedAuthJsonStoreTest` 真实 JSON open/submit | PARTIAL | 无正式 CUI 重启脚本 |
| AC-05 | CUI 非法输入/退出 | `InputValidatorTest`、`CuiAppTest` | PASS（自动化） | — |
| AC-06 | 设计图与实现对应 | V2/V2.1 文档、当前代码 | PARTIAL | 最终图未冻结重绘 |
| AC-07 | 视频/AI 日志 | 无 | BLOCKED | 人工视频 |
| AC-08 | ZIP/报告/反思 | 无 | BLOCKED | 人工材料 |
| AC-09 | 普通退货退款 8 元 | `AftersaleServiceTest.ac09AndAc17...` 精确退款 8.00 | PASS（自动化） | 未做真实 CUI 离线店长场景 |
| AC-10 | 两角色查询售后记录 | `QueryServiceTest` | PARTIAL | 真实 CUI/数据 |
| AC-12 | 补货后两位置余额 | `StockOperationsIntegrationTest` 断言补货后 S=8/W=7、合计 15 | PASS（自动化） | 未接正式 CUI |
| AC-13 | 在售不足拒绝整单 | `StockValidatorTest`/`StockServiceTest` | PARTIAL | 未接真实销售 CUI |
| AC-14 | 换货不足/同款质量换 | `AftersaleServiceTest` 换货库存不足 | PARTIAL | 未复现 AC-14 全部数字 |
| AC-15 | 残次退货记损耗/去向 | 无完整证据 | PARTIAL | 交回品损耗未接 |
| AC-17 | 退款按原实付 | `AftersaleServiceTest.ac09AndAc17...` 退款 8.00 而非 10/12 | PASS（自动化） | 未接正式 CUI |
| AC-19 | 开封非质量/其他原因 | `AftersaleServiceTest.openedNonQuality...`、`managerCannotOverride...` | PASS（自动化） | — |
| AC-21 | 小数数量拒绝 | `InputValidatorTest`、`CuiAppTest.invalidQuantity...` | PASS（自动化） | — |
| AC-22 | 库存不足查来源 | 无专门测试 | PARTIAL | A 的 IF-04 读取/核对 |
| AC-30 | 期初+补货余额重建 | `StockOperationsIntegrationTest.ac30...` W10→W7/S3，合计 10 | PASS（自动化） | 未做重启重建 |
| AC-33 | 已退已换后再退/重复提交 | `AftersaleServiceTest.ac33...` 历史退1+换1后拒绝再退2 | PASS（自动化） | 重复提交由同键重试测试覆盖 |
| AC-34 | 换货补/退/零差 | `AftersaleServiceTest.ac34...` 现价 55/30/48 分别补 7/退 18/零差 | PASS（自动化） | 未接真实 CUI/库存提交 |
| AC-35 | 整单作废反向恢复 | `ReversalIntegrationTest` 销售后冲销，S 余额恢复 10、原单保留 VOIDED、重复冲销失败 | PASS（自动化） | 未接正式 CUI 演示 |
| AC-37 | 店长报损/管理员修正 | `LossServiceTest`、`StockOperationsIntegrationTest` | PARTIAL | 修正/处置未接 |
| AC-46 | 原行部分退款 | `AftersaleServiceTest` | PARTIAL | 未逐项复现 AC-46 |
| AC-47 | 第 30/31 天边界 | `AftersaleServiceTest.ac47...` 上海 10-31 23:59:59 通过、11-01 00:00:00 拒绝 | PASS（自动化） | — |
| AC-51 | 三角色登录/权限 | `LocalAuthServiceTest`、`SharedIdentityResolverTest` | PARTIAL | 真实 CUI/权限菜单未全验 |
| AC-52 | D1 阻断旧收货 | `ReversalReceiptDependencyTest` 收货 W+10 后 W−3 阻断旧收货冲销 | PASS（自动化） | 未接正式 CUI |
| AC-53 | 输入边界 | `InputValidatorTest`、`StockValidatorTest` | PARTIAL | 全部数值/图片边界未逐项 |
| AC-54/55 | 促销分摊后按实付退款 | 无端到端 | PARTIAL | A 销售/售后真实联调 |
| AC-56–58 | 双额度/零补差/幂等 | `AftersaleServiceTest`、`QuotaCalculatorTest` | PARTIAL | 无完整真实提交链 |
| AC-59 | 售后失败原子性/整单冲销 | `ReversalIntegrationTest`、`ReversalCommitValidatorTest` | PARTIAL | 未做故障注入/重启校验 |
| AC-79 | 3 条各 1 件尾差 | `AftersaleServiceTest.ac79...` | **PASS（自动化）** | — |
| AC-80/81/82 | 历史额度/先换后退/多资格 | `AftersaleServiceTest` 部分 | PARTIAL | 未逐项复现 |
| AC-83/84 | D1 整单拒绝/逆序解除 | AC-83 后续 W− 阻断；AC-84 后续 W+ 不阻断（`ReversalReceiptDependencyTest`） | PASS（自动化） | 未接正式 CUI |
| AC-85 | 崩溃/恢复/单写者 | 无 | BLOCKED | A IF-04/05 |
| AC-86 | 三角色期初权限 | `AOperationsTest` 部分 | PARTIAL | 真实 CUI/权限 |
| AC-87/88 | IF-11 读取追溯 | `AftersaleDraftServiceTest` 真实 JSON 追溯 | PARTIAL | A 正式受控读取端口待对齐 |
| AC-89 | 已追溯草稿 CANCELLED | `AftersaleDraftServiceTest`：追溯后取消，保留 READ 事件、状态 CANCELLED，无退款/库存/核销变化 | PASS（自动化） | 未接正式 CUI 演示 |
| AC-90 | 追溯提交后中断重试 | `AftersaleDraftServiceTest` 同 read requestKey 重试只留一条 READ；`SharedOperationsIntegrationTest` 同 requestKey 重试 | PARTIAL | 未做真实进程中断注入 |
| AC-25/38/39/49/50 | 撤销场景 | — | N/A | 非验收 |

## 3. 本轮实际执行的测试

```powershell
./mvnw -B clean verify
```

关键专项证据：
- `AftersaleServiceTest`：13 项，含 AC-79、30 天/开封/质量/其他原因、同原行多明细；
- `AftersaleCommitValidatorTest`：6 项，含同原行多核销/原行缺失；
- `ReversalIntegrationTest`：整单冲销真实 JSON；
- `ReversalCommitValidatorTest`：D1 阻断/重复冲销拒绝；
- `StockOperationsIntegrationTest`：补货/报损真实提交；
- `AftersaleDraftServiceTest`：草稿创建/追溯/取消/删除；
- `SharedOperationsIntegrationTest`：CUI 售后链、同键重试。

## 4. 结论

- **可判 PASS 的自动化 AC**：AC-01、AC-05、AC-09、AC-12、AC-17、AC-19、AC-21、AC-30、AC-33、AC-34、AC-35、AC-47、AC-52、AC-79、AC-83/84、AC-89。
- 其余 B 相关 AC 多数为 **PARTIAL**，原因是缺少正式 CUI 入口、真实权限/恢复/故障注入或 A 的 IF-11 正式端口；不能把模块测试当作全部 AC 已通过。
- **BLOCKED 关键项**：AC-02（Main/pom）、AC-07/08（人工材料）、AC-85（A 恢复）、AC-87/88（A IF-11 读取准入）。






