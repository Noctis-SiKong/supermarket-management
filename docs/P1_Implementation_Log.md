# P1 统一实现与本地交付日志（v3，2026-10-21）

本轮用户 v3 取消 A/B 开发边界，由一个负责人完成单体；不改变 Spec/PRD/ERD、共享规范 v1.0.4、CR07-10/11 的业务批准。功能代码提交 `4a67050`，一键复现工具及跨单反向引用追加验证提交 `08df45a`；最终文档随本分支本地 HEAD 交付。没有推送、合并 main、修改远端或上传 GitHub。旧发布回执只对应旧提交。

## 1. 实际起点与在途保护

v3 收到后的四项核验原始输出（PowerShell 用 LASTEXITCODE 打印 INTEGRATED）：

```text
member-a/p1-delivery
6408320
 M pom.xml
 M src/main/java/nz/ac/aut/pdc/infrastructure/JsonStore.java
 M src/main/java/nz/ac/aut/pdc/shared/SaleLineSnapshot.java
?? src/main/java/nz/ac/aut/pdc/shared/AftersaleReadAccess.java
?? src/main/java/nz/ac/aut/pdc/shared/AftersaleReadRequest.java
?? src/main/java/nz/ac/aut/pdc/supermarket/app/Application.java
?? src/main/java/nz/ac/aut/pdc/supermarket/app/BIntegration.java
?? src/main/java/nz/ac/aut/pdc/supermarket/app/Main.java
?? src/main/java/nz/ac/aut/pdc/supermarket/app/RepositoryBIntegration.java
?? src/main/java/nz/ac/aut/pdc/supermarket/sales/OriginalSaleAccess.java
?? src/test/java/nz/ac/aut/pdc/supermarket/app/ApplicationTest.java
?? src/test/java/nz/ac/aut/pdc/supermarket/sales/OriginalSaleAccessTest.java
INTEGRATED
```

该 HEAD 已含 B 的 `6408320`，跳过重复整合，没有 fetch/merge。此前原 Project1 被其他会话切换分支，为保护在途工作，已在并列的 `Project1-member-a` 独立工作树及 `member-a/p1-delivery` 分支继续开发；与用户历史记录的分支名不同，以以上实际结果为准。在 2026-10-21 该记录形成时，原 Project1 仍在 `member-a/p1-complete / 6408320`，已观察到原在途文件仍在；没有重置、覆盖或清理它们。当时最终实现位于独立工作树；2026-10-22 指定根交付位置更新见第6节。

默认 shell 曾指向 Java 8；本次选择已存在的 Microsoft OpenJDK **17.0.20.1+1**，Wrapper Maven **3.9.11**，PowerShell **7.6.5**。未另建 Maven 骨架，未否定历史 JDK17 构建记录。README 用环境变量探测工具位置，不保存个人机器绝对路径。

## 2. 实现位置与主要变更

以下路径均相对于本工作树的 `src/main/java/nz/ac/aut/pdc/`：

| 模块 | 代码与实际行为 |
|---|---|
| 启动／装配 | `supermarket/app/Main,Application,RepositoryBIntegration`：真实存储、认证、库存、售后、查询、冲销与 CUI；BApplication 仅兼容委托。带依赖 JAR 含 Jackson；支持 --check、首次账号初始化及本地哈希配置，无默认密码或自动业务货物 |
| 商品／期初／收货／销售 | `supermarket/app/AOperations`、catalog/Product、promotion/Promotion、sales/Pricing：沿用已有闭环，统一入口保存；审核首次 W+、补正增删未入账行、稳定销售行、零价及单活动择优／分摊 |
| 附件 | infrastructure/AttachmentStore、DataRoot：JPG/PNG、5,000,000 字节、每单3张、独立 ID、相对路径／哈希、安全根；缺图不阻审核 |
| 库存／损耗 | stock/StockBusiness、SharedStockAdapter、StockValidator、LossWorkflow：原始请求重建与完整比较、整数精确解析、角色、前缀提交组恢复；报损一次扣存，处置不重扣，管理员修正保留原数量及追加版本／差额／前后审计 |
| 售后／双额度 | aftersale/AftersaleEngine、AftersaleService、AftersaleCommitValidator：同一算法复核完整增量；稳定 redemption_id 升序、先换后退、累计数量／金额、窗口／开封原因、替换和特殊质量配对；一笔净款项与库存／核销／顾客交回损耗同次保存 |
| IF-11 | sales/OriginalSaleAccess、aftersale/AftersaleDraftAccess：真实身份和精确凭证，唯一准入；原子新建目标／读取追溯，先可靠保存再返最少字段；成功后 fresh open；未知结果先查同键；旧事件兼容读取、不改写 |
| 草稿生命周期 | aftersale/AftersaleDraftService、AftersaleDraftValidator、AftersaleDraftDeletionValidator；infrastructure/DraftDeletionGuard：有追溯只能 CANCELLED，无财务／库存／额度影响；无追溯合规草稿整组原子删除，先查结果再查存在性，删除可重放 |
| 全单冲销／D1 | reversal/EffectiveDependencies、ReversalCommitValidator、ReversalChangeSetAssembler：严格重建目标全部反向影响，拒绝少交／夹带／跨单引用；MANAGER/ADMIN 允许、CLERK 拒绝；支持真实 APPROVED 收货，完整撤销的依赖组排除后可继续冲销 |
| CUI／查询 | cui/CuiApp、SharedOperations、MaintenanceOperations；query/QueryService：完整菜单、成交／售后报价后确认、单行和混合售后、普通历史角色范围、原键重试；CUI 不拿 WorkingCopy 或密码哈希 |
| 公共／持久化 | shared/、infrastructure/JsonStore、SubmissionTime：唯一写入口、原始请求／身份／键／指纹／版本／完整增量、OS 单写者、提交序号、临时写／原子替换／完整上一版、恢复异常阻写、禁止嵌套 submit |

旧单核销反向计划原型 ReversalService 等及 SubmissionGateway 载荷移到测试源码保留历史夹具，生产包不带它们。未删除 B 分支、历史提交或 V1/V2；不新增第二套认证、保存器、库存或额度算法。现有 LocalAuthService、Sha256PasswordHasher 依 v3 保留并接真实凭证视图。

## 3. 公共契约与兼容

共享规范 §11 登记精确 Java IF-11 请求／结果、事件字段、旧 detail 兼容、角色及所有实际细分错误码；不新增 IF 编号。SubmissionPort.CommitValidator 保留 v1.0.4 六参签名。WorkingCopy 为内部不可变句柄，不向 CUI 直传。

IF-05 在锁内通过 businessTime 只取一次 acceptedAt，先核对幂等及版本再复核本次意图。新 DOCUMENT.createdAt、DOC_EVENT.occurredAt、RECEIPT_REVIEW.reviewedAt 和本次确认 confirmedAt 按该时刻赋值；既有创建／原销售时间不变。促销比较使用该时刻，候选经字段矩阵正规化后精确比较，不忽略历史／用户时间；重放不重新取时，恢复没有活动日期兜底。

草稿请求可用 DraftDeletion(documentId)（expectedVersion=0，版本在各 Removal 中）；原双参请求仍兼容且额外检查目标版本。删除必须是业务批准的目标整组，包含实际关联记录；A 检完整集合、所有版本和引用闭合后同次保存，失败原状态不变。不是任意表删除。

读取返回 VALUE 的 AftersaleReadAccess，内含追溯 CommitReceipt，不代表售后完成。新事件 ID 从读取键稳定重算，保存实际返回行 ID。旧 target-READ 及 originalSaleLineId/returnedLineId detail 只兼容解析，未改写。后续最终操作重新 open 并在同一新基线读额度／库存及提交。角色和留存仍按 CR07-10/11，无新待裁决业务变化。

## 4. 实际验证

| 实际命令／执行 | 结果 |
|---|---|
| v3 开工 JDK17 `./mvnw.cmd -B -q clean verify` | 在途基线184 tests，0失败／错误／跳过 |
| 本轮最终 `./mvnw.cmd -q clean verify` | 200 tests，0失败／错误／跳过；可执行带依赖包产出 |
| `./scripts/verify-p1.ps1 -Offline` | 离线重跑200 tests，0失败／错误／跳过；打包隔离、JAR --help/--check 及真实 CUI 全部通过，CUI退出0、合法业务提交3笔 |
| JAR真实 CUI | 随机虚构三角色哈希初始化，真实登录→商品新增→合法期初5→销售1→历史查询；1商品、1销售、2库存流水，未输出密码；隔离临时项目根，不写真实业务数据 |
| JAR --init-accounts --check（真实终端） | 退出0，3个虚构角色、0业务记录、ordinal=0；未调用测试替身；交互输入仅本机临时数据 |
| JAR 内容核对 | 含 Main 与 ObjectMapper；无 FictionalDependencies、CrashAfterCommitProbe、旧 ReversalService/SubmissionGateway 测试原型 |
| `git diff --check`／暂存差异检查 | 通过；未纳入 data/、target/、密码／哈希配置、课程原件或附件 |

PowerShell 首次未给 -Dfile.encoding=UTF-8 加引号导致 Java 参数解析失败，已修正 README 并实际重跑带引号命令成功。开发中严格复核暴露的旧不完整测试夹具及编译问题已修复，失败运行没有记作通过；上述数字来自最终 Surefire XML 与脚本实际回执。测试未跳过，不用历史跳过数字替代本次结果。

**真实链证据**：FullRuntimeTest 使用生产认证、库存、额度、草稿、IF-11、JsonStore、CUI、冲销；正常收货→审核→补货→销售→跨人售后；依赖冲销被拒且快照不变；撤销售后→销售→补货→APPROVED 收货后成功、余额归零。另覆盖同类稳定 ID 尾差、混合售后净款项、调价后质量零差换货、处置／管理员修正、真实 CUI 退款及冲销原键跨重启重放。

**失败与恢复**：真实文件替换前 IOException、保存失败无数据返回、整组删除失败无部分可见、未知结果先查询、重试无重复追溯、实际子 JVM halt 后重启复用原追溯、另一 JVM 单写者拒绝、损坏快照启动阻写、旧事件不改写。跨午夜与幂等时间的专门测试见 SubmissionTimeTest；低层结构／附件／完整上一版恢复见 StoreTest、AttachmentTest、DraftDeletionTest。AC-02/85/87/88/90 对应真实入口场景有自动证据，不将其扩写为全部 AC 正式验收通过。

**替身边界**：保留 FictionalDependencies、DraftDouble、历史单核销计划与捕获端口等纯模块夹具，其结果不算真实联调。FullRuntimeTest 的主链和 CUI 用真实业务依赖；文件故障与响应丢失用明确钩子／包装器，旧事件兼容另用标记的历史写入夹具；实际进程崩溃 Probe 仅在测试源码，不进生产 JAR。不是电源故障或跨平台文件系统认证。

## 5. 交付与未覆盖范围

功能及关键 P0/P1 修复已实现，无因等待 B 而保留的必需功能占位；不再需要 B 提供接口。没有新的业务规则裁决问题。代码、运行命令与证据可供人工审阅，结论为“P1 全量开发完成，待用户验收”。

未执行 Linux/macOS、课程正式验收签字、学生视频／报告／ZIP验收；没有代写个人反思或签署。200项自动化测试不代表所有课程 AC 或人工作业已完成。GitHub 上传／推送／合并 main 仍未授权，必须先展示本次具体差异、人工审核后另获本次明确批准。

## 6. 2026-10-22 发布前工程收尾

修改前实际状态：指定 Project1 为 `member-a/p1-complete / 6408320`，暂存区为空，3 个 tracked 修改及8个未跟踪文件；并列完成版为 `member-a/p1-delivery / 4328a11`，工作区干净。这两个分支分别被对应工作树检出。根据后者的 `4a67050` 真实装配、`08df45a` 验证工具、实际 Main/assembly/测试源码及本轮复跑选择完整交付来源，没有凭历史哈希直接选版，也没有重复合并 B。

本轮只改验证脚本和必要文档，没有改业务源码、规则或共享接口。脚本以 UTF-8 BOM 保存；Java 账号 JSON 用 WriteAllText/UTF8Encoding(false)，与 PS5.1 的 UTF-8 BOM 默认行为隔离；中文 CUI 断言同时配置原生命令输入编码与 Console.OutputEncoding，并在 finally 还原。JDK 探测显式捕获 stderr，避免 PS5.1 在 Stop 下误报。保留原有绝对临时路径／前缀检查，并新增目标身份及子目录 reparse point 检查；不能确认安全就保留目录，不跟随链接删除。全部验证数据仅在本次生成的临时根；真实 data/ 未用于验证。

本机重新发现 Windows PowerShell **5.1.26100.8521**，以及 Codex 随附可调用的 PowerShell **7.6.5**（并非新安装）。两版解析及执行均通过；JDK Microsoft **17.0.20.1+1**、Maven Wrapper **3.9.11**。2026-10-21 的 PS7.6.5 记录仍是其原时点证据。

| 本轮实际命令（完整交付工作树） | 退出码及结果 |
|---|---|
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-p1.ps1 -Offline` | 0；200 tests，0失败／错误／跳过，打包依赖隔离和真实 CUI 退出0，合法业务提交3笔 |
| `pwsh -NoProfile -File .\scripts\verify-p1.ps1 -Offline` | 0；同上，200 tests，真实 CUI 退出0，合法业务提交3笔 |

汇总来自本轮生成的 Surefire XML 和脚本回执；测试内预期的拒绝启动信息不等于脚本失败。旧口径独立保留：A 单分支36项见 `evidence/member_a/implementation_validation.md`；B 时点176项、1跳过见 `evidence/member_b/ac_execution_b_2026-10-21.md`；v3开工在途184项及最终200项见第4节。不能以184代替最终200，也不能把各分支数字相加作为当前总数。

旧 WIP 逐文件比对结果：5个字节一致（pom.xml、SaleLineSnapshot、AftersaleReadAccess、BIntegration、OriginalSaleAccessTest）；其余6个不同（JsonStore、AftersaleReadRequest、Application、Main、OriginalSaleAccess、ApplicationTest），按可能独有内容保留，不混入交付。仓库外新建时间戳备份的 manifest 包含源分支／HEAD、暂存及未暂存补丁、11个 WIP 文件、376个 ignored 构建文件及 SHA-256，已逐项重读核验；切换前未清除 ignored 文件，也未使用 git clean；最终验证按既有脚本重建 target，旧构建内容已完整备份。完整清单及逐文件比较在该外部备份，不在仓库记录其绝对路径。

原 WIP 保存于 stash `f33c6b7c3e4e1d302323988682d135d1ae255bd4`，已核验11个文件集合、原6408320基线和空 index，Git 换行正规化以外内容一致；原始字节另由备份保留。另有误在完成版创建、随后已 apply 恢复的本轮工程改动 stash `87fbbd99ebe36ea82cb9e7e8479811f12268cffd`，同样保留，不代表原 WIP。未删除分支、工作树、stash 或备份。

指定根使用新本地 `member-a/p1-release` 分支承接本轮完成版实际 HEAD；具体提交以 Git 回执为准，不抢占完成版工作树的分支。指定 Project1 根已再次实际执行上述 PS5.1 完整命令，退出0，200项零失败／错误／跳过、真实 CUI 退出0；由此确认指定根已运行修订后的完整交付代码。仅本地交付，无推送、main合并或远端配置修改。课程正式验收、报告、视频、ZIP及本人签署仍需人工完成；测试替身／真实链范围沿用第4节，没有增加业务能力或扩大验收结论。
