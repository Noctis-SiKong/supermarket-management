# B 侧 AC → 测试/证据索引（2026-10-21）

> 口径：本表只记录"模块级测试/证据"，**不等于正式 AC 通过**。凡依赖 A 的 shared/IF-04/05、真实持久化/恢复或真实 CUI 入口的 AC，均标注为被阻塞或不完整。

## 1. 覆盖状态

| AC | 内容（B 相关） | 当前证据 | 状态 | 缺口/依赖 |
|---|---|---|---|---|
| AC-01 | 构建/运行 | FrameworkSmokeTest；`clean verify` BUILD SUCCESS | 部分 | 未重新验证干净克隆/离线 |
| AC-02 | CUI 可运行入口 | 无 `main`，pom 无 exec/Main-Class | 阻塞 | 待 A 的 Maven 框架/启动入口 |
| AC-03 | UTF-8 | pom 编码配置 | 部分 | 未在 Windows 控制台做全链路验证 |
| AC-04 | 登录与角色 | `LocalAuthServiceTest` | 部分 | 真实凭证来源待 A |
| AC-05 | 非法输入反馈 | `InputValidatorTest`、`CuiAppTest` | 部分 | 真实服务未接线 |
| AC-06 | 设计与实现对应 | `v2_1/README.md`、V2.1 快照 | 部分 | 最终图需在代码冻结后重绘 |
| AC-07 | 退出登录/程序 | `CuiAppTest` 菜单 0/exit | 部分 | 无真实 main |
| AC-09/14/15 | 售后资格/额度/已开封/质量 | `AftersaleServiceTest`、`QuotaCalculatorTest`、`QualityPairValidatorTest` | 部分 | 简化规则；真实数据/权限待 A |
| AC-12/13 | 库存两位置/来源不足 | `StockValidatorTest`、`StockServiceTest`、`LossServiceTest` | 部分 | 未做端到端余额重建 |
| AC-22/30 | 余额重建/流水一致性 | 无专门测试 | 未覆盖 | 需 A 的 IF-04/恢复夹具 |
| AC-33/34 | CR07-10 严格资格/原因 | `AftersaleServiceTest`（超期、开封非质量、质量/其他原因） | 部分 | 真实数据/权限待 A；已无店长例外 |
| AC-46/47 | 退款金额/30 天边界 | `QuotaCalculatorTest`、`AftersaleServiceTest` | 部分 | 日历日边界仅单测；真实时区数据待 A |
| AC-51/53 | 额度/剩余金额 | `QuotaViewBuilderTest`、`QuotaCalculatorTest` | 部分 | 多行顺序扣减已测；跨会话并发未测 |
| AC-58/82 | 质量同款零差配对 | `QualityPairValidatorTest`、`AftersaleServiceTest` | 部分 | 金额/库存真实提交未接 |
| AC-79/80/81 | 售后金额分配/替换品金额 | `SettlementCalculatorTest`、`AftersaleServiceTest` | 部分 | 款项记录/真实提交待 A |
| AC-10/52/59 | D1 冲销/依赖 | `ReversalServiceTest`、`DependencyCheckerTest` | 部分 | 仅阻断，不级联重建 |
| AC-83/84 | 冲销幂等/权限 | `ReversalServiceTest` | 部分 | 真实提交/幂等键待 A |
| AC-85 | 崩溃恢复/阻写 | 无 | 阻塞 | A 的 IF-04/05/恢复 |
| AC-87 | 跨人读取追溯 | 无 | 阻塞 | A 的 IF-11 方案 |

## 2. 测试清单（84 项，按类）

- `LocalAuthServiceTest` 7、`InputValidatorTest` 4、`CuiAppTest` 6
- `StockValidatorTest` 4、`StockServiceTest` 5、`ReplenishmentServiceTest` 4、`LossServiceTest` 4、`LossRecordTest` 2
- `AftersaleServiceTest` 9、`QuotaViewBuilderTest` 3、`QuotaCalculatorTest` 5、`QualityPairValidatorTest` 5、`SettlementCalculatorTest` 4
- `QueryServiceTest` 9、`ReversalServiceTest` 7、`DependencyCheckerTest` 4
- `FrameworkSmokeTest` 2

## 3. 使用限制

1. 以上测试全部运行在虚构端口/内存替身上，未写真实 `data/`。
2. 不得把本表"部分"当作 AC 通过；正式 AC 需在 A 交付 shared/IF-04/05 后按 J-01–J-07 执行并补证据。
3. B 新增业务码 `QUERY_FAILED`、`DEPENDENCY_BLOCKED`、`REVERSAL_FAILED`、`AFTERSALE_FAILED` 待通知 A 登记。


