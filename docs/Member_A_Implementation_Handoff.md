# A 首批代码交接（已推送 member-a/p1-implementation，待 B 集中审计）

> 2026-10-21 v3 当前状态：完整单体与正式 CUI 已统一接入，详见仓库 docs/P1_Implementation_Log.md。下文保留形成时点的历史结论、数字及发布回执，不表示本轮仍“等待 A/B”，也不授予本轮推送权限。
日期：2026-10-20，Asia/Shanghai。起点 `main / 04abd6a17f8a1ccfe1bc2a6593922bf3dc017c3a`，起点工作区干净；本地分支 `member-a/p1-implementation`。本次提交并推送至 `origin/member-a/p1-implementation`，不合并 main，不修改冻结 Spec/PRD/ERD 或 B 的设计/实现。

收尾观察到其他会话对6份基线/共享文档的未提交修订（含 CR07-10）；已保留且不归入本次开发改动。A 代码不实现售后资格/授权例外，IF-11 提案不依赖被撤销字段。代码/交接文件本次保留本地未提交，避免把并发文档变更混入提交。

最终还出现3份 B 设计/演进文件的并发修改；本会话同样未编辑/暂存。最终 `clean verify`：26项测试全通过，失败/错误/跳过均0；生产 Java 文件21个。A 源码、测试、交接和证据已提交并推送至 `member-a/p1-implementation`；CR07-11 文档由单独提交同步。

代码契约对应正式共享规范 **v1.0.2**，这是第一批 Java 签名（以前没有生产 Java 签名，因此没有旧代码签名迁移）。运行 JSON schemaVersion=1 是本次实现的格式版本，不是规范版本。

已完成的 A 代码：

| 范围 | 实际文件/行为 |
|---|---|
| 公共类型 | `src/main/java/nz/ac/aut/pdc/shared/`：OperationContext/ActorContext/Role、Result、WorkingCopy、ChangeSet、DocumentHeader/DocumentLine/DocEvent、MoneyEntry/StockEntry、SaleLineSnapshot、IdClock |
| IF-04/05 | `shared/SubmissionPort.java`；`infrastructure/JsonStore.java`：认证先行，四项匹配结果查询，幂等先于旧 wc 拒绝，锁内主责复核，统一分配序号，单 JSON 原子替换及上一版，启动/显式上一版恢复，OS 写锁 |
| IF-03 | `shared/IdClock.java`：UUID 请求键、由请求键+用途派生可重建稳定 ID、String.compareTo 升序、注入 Clock、Asia/Shanghai |
| WP-01 / IF-06 | `supermarket/catalog/Product.java`；`supermarket/app/AOperations.java`：商品创建/维护及同 wc 商品读取，合法来源期初单/行/指定位置流水候选 |
| WP-02 | AOperations：实收待审、退回补正、现有行修改/未入账行增删、再审、自审、通过同次 W+、审核版本检查及原行不重复通过；无图仍可审核 |
| 附件 / IF-09/16 | `infrastructure/AttachmentStore.java` / `DataRoot.java`：独立 UUID 名、PNG/JPG 文件技术检查、字节上限、相对路径/哈希校验、路径与链接越界拒绝；引用随收货同次提交 |
| WP-04 / IF-10 | `supermarket/promotion/Promotion.java`、`supermarket/sales/Pricing.java`、AOperations：活动参数、有效日界、单活动择优、整数分最大余数、同余数稳定 ID、合并销售行、付款完成才提交、不可变实付快照、款项与 S− 同次提交、提交再核活动有效性 |
| 普通原行投影 | `AOperations.readSaleLine`：真实认证端口解析后，店员本人/店长管理员可读最少 SaleLineSnapshot；这不是 IF-11 跨人售后特例实现 |

真实认证、库存非负/余额/转移校验、售后双额度、D1、查询 CUI 及业务 CUI 仍由 B 提供。当前是可编译业务库，没有业务 Main-Class；不能把 JAR 当成可运行完整 CUI 系统。

关键 Java 调用（类型名以源码为准）：

```java
Result<WorkingCopy> SubmissionPort.open(OperationContext op);
Result<CommitReceipt> SubmissionPort.submit(
    OperationContext op, BusinessRequest original, WorkingCopy wc, ChangeSet changes);
Result<CommitReceipt> SubmissionPort.lookupResult(OperationContext op, BusinessRequest original);

Result<Void> CommitValidator.validate(OperationContext op, ActorContext actor,
    BusinessRequest original, WorkingCopy baseline, ChangeSet completeChanges, Instant acceptedAt);

Result<AOperations.Prepared> AOperations.prepare(
    SubmissionPort store, OperationContext op, BusinessRequest original);
```

`Result.Status` 区分 VALUE/PREPARED/SUCCESS/NOT_FOUND/REJECTED/FAILED。lookupResult 的四种状态按正式规范；只有 SUCCESS 是已提交。A 预备入口发现已经提交时返回 ALREADY_COMMITTED 提示，消费者读取 lookupResult 的原 CommitReceipt，不重复准备。新调用始终先保存 requestKey 和原始请求，未知结果先查，读取失败不能换新键。

`RecordData(table,id,version,json)` 是不可变增量记录载体，json 必须是对象文本；`data()` 每次解析新节点，不能修改基线。`ChangeSet(inserts,patches,removals)` 不带完整快照；兼容双参构造器令 removals 为空。Patch 只带 recordId/expectedVersion/changedFields；Removal 当前仅支持 A 的未入账收货补正行删除，不开放生效事实删除。不同调用新增候选用 Builder.merge 合并一次；同键同内容合并一次，同键异内容 EVENT_CONFLICT。更新同一记录由协调者先形成一个最终补丁。

WorkingCopy 是内部协调能力，**不要返回给 CUI**，也不要把 JsonStore 或 authenticationView 暴露给 CUI。B 的受限查询在内部句柄上过滤后只返回允许 DTO；不能直接把 allRecords 当成查询输出。Store 用句柄对象身份、当前进程签发登记、actor/operation/target 及 baseLast 拒绝伪造/跨操作/旧句柄。重启必须重新 open。

B 的适配输入：

1. **认证**：B 提供 `OperationContext.IdentityResolver` 适配器，接收不透明会话对象，验证是否由其签发，返回可信 accountId/Role。A 不创建可信会话。持久账号格式 `JsonStore.Credential`；装配时可向完整构造器注入 B 预置的哈希账号列表，仅空库初始化使用，不覆盖既有账号。认证专用 `authenticationView()` 不要求先登录，且不进入 WorkingCopy。
2. **库存**：B 保持 IF-07 唯一所有者。A 的 `AOperations.StockDependency` 是 A 消费端适配要求，不是重写 B 的接口。prepare(wc,overlay,completeGroup) 接收调用前非库存 overlay 和尚未合并的完整组，返回仅本次新增的 STOCK_ENTRY delta；A 校对影响并合并一次。review(wc,original,completeChanges) 在 IF-05 锁内检查完整候选，不生成/合并/保存。validateRecovered(state) 调用 B 自有库存恢复校验。稳定流水 ID/eventKey/来源行由 A 请求提供，候选 commitOrdinal=0，提交后全组赋同一正值。
3. **B 写业务**：向 JsonStore 构造器的 operation→CommitValidator 注册自身主责复核器（含权限、原始意图/effects、引用/不变量）；未注册返回 FAILED/VALIDATOR_UNAVAILABLE。校验器不再次 submit、生成变更或写文件。B 恢复校验须与 A.validateRecovered 组合；A 不验证/复制 B 的双额度和 D1 算法。注册表在构造时冻结，最后集成时装配。
4. **类型适配**：采用正式 `shared/` 公共类型；早期交接包的 supermarket.common/persistence 占位路径由正式共享规范 §8 覆盖。A 业务仍在 supermarket.catalog/promotion/sales/app；没有创建或修改 B 的 stock/auth/aftersale/query/reversal/cui 包。B 自有单据可映射到共享 DocumentHeader/Line，不重复定义共享款项类型。
5. **IF-11**：共享最少字段形态已交付，真实专用读取尚不提供。[方案](./design/IF11_Proposal_A.md)列出了字段、步骤及失败/崩溃语义；本次用户已明确批准两项规则并按Spec CR07-11同步，共享文档升级v1.0.3。真实专用读取仍待A实现、B集中审计及联调后接入；不新增全局开工门。

DATA_ROOT 技术布局：装配方传入明确项目根 `DataRoot.forProject(Path)`；验证 README/pom/docs 标志后只使用根下 data/，不依 CWD/JAR 位置猜测。不增加必须手工编辑绝对路径的启动配置；B CUI 交付后由 A 最终装配工程根发现/启动入口。当前内部库需要明确根参数，独立发布 JAR 的自动定位和可运行入口尚未交付。

data/ 下：snapshot.json（当前）、previous.json（完整上版）、snapshot.next.json/previous.next.json/recovery.next.json（未提交临时文件）、writer.lock、attachments/。临时文件不加载。替换不支持或失败就拒绝保存；原子替换后不再执行可能返回失败的文件操作。恢复用显式 RecoveryMode.PREVIOUS，验证所选完整快照及其引用附件后原子安装；不自动回退损坏当前库、不拼接另一个版本的计数/记录。附件文件不可变保存，上版引用的文件原地保留；不自动清理未关联副本、不静默删除失效历史关联。文件复制失败无成功引用；独立命名碰撞用 CREATE_NEW 失败，不覆盖，可重新导入获取新 ID。源路径从不进入附件记录。

验证命令、实际结果及限制见[本轮验证记录](./evidence/member_a/implementation_validation.md)。本文件交付实现及适配说明，不冒称 B 已收到/已审计、真实联调、正式 AC 通过或全链离线运行通过。课程原件/模板未纳入本次阅读输入；仅阅读仓库已有引用，不能声称重读原件。

IF-11批准更新（2026-10-20）：两项业务裁决已关闭，现行规则按Spec CR07-11及共享规范v1.0.3（2026-10-21已升v1.0.4，技术补充见共享规范§5.2.1及下方补记）。已有Java交付仍对应v1.0.2且未实现真实专用读取；不要将此前代码26项模块测试当作新增AC-88–90通过。B负责已读取未完成售后草稿CANCELLED取消编排，不适用于已完成售后。

## 推送补记（2026-10-20）

用户随后明确允许推送代码。A 源码、测试、本交接及证据已提交到 `member-a/p1-implementation` 并推送 `origin`；合并 `main` 仍需单独决定。此前“未推送/未提交”的记录保留为交接形成时点的历史；CR07-11 文档使用单独提交，未混入 A 代码提交。

2026-10-21修复交接（共享契约v1.0.4）：B的CommitValidator增加末尾Instant acceptedAt，必须使用锁内唯一业务时刻；JsonStore可注入IdClock::now。已移除AOperations的生成时间缓存和恢复活动日期兜底。新AFTERSALE_DRAFT_DELETE使用DraftDeletion(documentId,expectedVersion)及完整removals，由B校验售后可删性、A检查版本/闭合组/引用红线并原子保存；不支持任意表删除。详细兼容影响以正式共享规范§5.2.1为准。本次不改Spec/PRD/ERD，不实现B售后/认证/库存，也不启用真实IF-11专用读取。B须以本次最新本地修复提交审计/适配，不只采用293d3b6；真实联调待其实现交付。

## v3 统一实现补记（2026-10-21）

当前代码交付 `4a67050`；真实 CUI 的退款、整单冲销及原键跨重启重放已由 FullRuntimeTest 验证，AC-35/87/88/90 不再因“未接正式 CUI／等待 A”而阻塞编码。完整命令、真实链/替身范围及尚未执行的人工材料统一见 docs/P1_Implementation_Log.md。本补记不改写上方历史 PASS/PARTIAL/BLOCKED 形成时点的证据，不代替用户或课程验收。仅本地提交，未上传 GitHub。
