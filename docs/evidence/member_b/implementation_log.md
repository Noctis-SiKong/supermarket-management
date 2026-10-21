# B 骨架实现记录（auth / cui / stock）

版本/日期：v0.1，2026-10-21（Asia/Shanghai）
分支：`member-b/implementation`（基于 main `04abd6a`）
性质：模块骨架 + 测试替身；**非真实持久化、非联调、非 AC 验收**。仅证明接口边界、纯规则与单元测试可跑通。

## 1. 范围与文件

| 包 | 文件 | 职责 |
|---|---|---|
| `auth` | `Role`、`SessionRef`、`AuthResult`、`AuthService`、`CredentialStore`、`PasswordHasher`、`Sha256PasswordHasher`、`LocalAuthService` | IF-01 登录/会话/身份解析；凭证只读端口；三态结果 |
| `cui` | `InputValidator`、`CuiApp` | 输入格式/基础边界校验；最小登录/退出 CUI 壳 |
| `stock` | `Position`、`StockBalance`、`StockChangeLine`、`StockEntry`、`StockCheckResult`、`StockPorts`、`SubmissionGateway`、`StockOperations`、`StockValidator`、`StockService` | IF-07 余额读取、变动校验、候选流水；IF-05/04/06/03 的 B 侧适配端口 |
| 测试替身（`src/test/.../fixture`） | `FakeWorkingCopy`、`FakeProductCatalog`、`FakeIdGenerator`、`FakeSubmissionGateway`、`InMemoryCredentialStore` | 只读快照/商品/固定 ID 时钟/捕获式提交/内存凭证；均标"虚构/非生产" |
| 测试 | `LocalAuthServiceTest`(7)、`InputValidatorTest`(4)、`StockValidatorTest`(4)、`StockServiceTest`(4) | 登录/会话/输入边界/库存规则/候选组装 |

## 2. 实际构建与测试（2026-10-21）

- 环境：JDK 17.0.12、Maven 3.9.11（Wrapper 3.3.4）
- 命令：`./mvnw.cmd --batch-mode --no-transfer-progress clean verify`
- 结果：**BUILD SUCCESS**；`Tests run: 21, Failures: 0, Errors: 0, Skipped: 0`
  （框架冒烟 2 + auth 7 + cui 4 + stock 4 + stock 4）

## 3. 接口与设计决策（自我提问记录）

1. **如何不复制 A 公共类型？** 在 `stock.StockPorts` 定义 B 侧最小只读端口（WorkingCopyView/ProductCatalog/IdGenerator），提交能力用 `SubmissionGateway` 端口；A 的 `shared/` 代码交付后以适配器替换，不把 A 的类型复制进 B。
2. **如何保证 IF-07 不保存？** `StockService` 只读 `WorkingCopyView`、只返回 `StockCheckResult` 候选；没有提交/写文件入口。
3. **如何让规则可测？** `StockValidator` 是纯函数（输入变动组+余额，输出可选拒绝），不依赖工作副本；`StockService` 只做编排与候选组装。
4. **CUI 如何低耦合？** `CuiApp` 目前只依赖 `AuthService` 接口；业务菜单后续通过构造器注入各 `*Operations` 接口，不直接依赖具体实现。
5. **错误如何表达？** 用 sealed 三态（`AuthResult`、`StockCheckResult`、`SubmitOutcome`），区分 PREPARED/REJECTED/FAILED；不用 boolean/null。
6. **替身放哪？** 全部在 `src/test/.../fixture`，不进 `src/main`，不写真实 `data/`；无法证明持久化/恢复/AC。

## 4. 已知边界与未证明

- 未接 A 的 `shared/` 公共代码与 IF-04/05 实现；`SubmissionGateway` 是捕获式替身，**不证明原子提交/幂等/恢复**。
- `SessionRef` 为骨架类（包内构造），未实现会话过期/持久化；密码哈希用 SHA-256 教学演示，正式应换带盐 KDF。
- CUI 仅有登录/退出骨架，业务菜单、错误码呈现、退出路径的完整交互待接模块。
- 未实现 aftersale/query/reversal；IF-11 真实读取待 A 方案。
- 未执行任何正式 AC；21 项测试仅验证模块逻辑与边界。

## 5. 下一步

1. 实现 `stock` 补货/报损用例服务（依赖 IF-07 + IF-13 候选），用替身测试。
2. 实现 `aftersale` 纯计算（QuotaCalculator/QuotaViewBuilder/QualityPairValidator）与编排骨架。
3. 接 CUI 菜单（登录后分派到各 Operations 接口）。
4. A 交付 `shared/` 后适配真实 WorkingCopy/ChangeSet/IF-05，再做 J-01–J-07 联调与 AC 证据。
