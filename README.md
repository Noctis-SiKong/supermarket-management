# 超市管理系统 · Supermarket Management

单店离线 Java CUI 课程项目。现有单体已装配真实 JSON 存储、认证、商品、收货、促销、销售、库存、售后与整单冲销；运行与本次验证状态见 [统一实现日志](./docs/P1_Implementation_Log.md)。自动化验证不代替用户验收、课程报告、视频或本人签署。

## 阅读顺序

1. [Spec：需求和业务规则基线](./docs/Project_Spec.md)（v0.7，已冻结为需求基线）。
2. [PRD：功能需求和验收映射](./docs/Project_PRD.md)（v0.7，已冻结为需求基线）。
3. [ERD：逻辑实体、关系和数据字典](./docs/Project_ERD.md)（v0.7，与需求基线同步；设计审阅项见第8节）。
4. [P1 预备分工与接口清单](./docs/P1_Preliminary_Work_Distribution_and_Interface_List.md)（v0.3，工作包与共享资产分工；当前跨模块约定和局部编码门见正式接口规范）。

5. [共享边界与接口规范](./docs/Shared_Boundaries_and_Interface_Spec.md)（v1.0.4正式版及代码交付附录；CR07-11的IF-11准入与取消留存已批准，接口实现/审计/联调分别交付）。

ERD核心审阅事项见其第8节；工作包见第4项，正式跨模块调用约定唯一维护于第5项。

## 范围与技术边界

P1为Java CUI，采用集合与文件持久化；已选Maven、JDK 17、UTF-8 JSON/Jackson。P2才扩展GUI和Derby；课程允许的GUI选型以Spec为准。ERD表达逻辑关系，不表示P1必须使用关系数据库。

已确认范围以Spec为准，包括收货审核、在售/在仓库存、补货、销售促销、按原销售行实付办理退换、数量及金额双额度核销和审计。当前基线从P1、P2均移除了商品日期、临期/过期、批次及过期拦截；不因发布到GitHub重新扩展范围。

## ERD图源与查看方式

ERD正文包含四个 `mermaid` 代码块。请优先打开 [ERD Markdown正文](./docs/Project_ERD.md)；`.mmd`链接用于下载或编辑源码，不保证单独打开文件时GitHub会自动渲染。

| 分视图 | 可编辑源码 |
|---|---|
| 公共单据与审计 | [01_common.mmd](./docs/erd/01_common.mmd) |
| 到货审核与库存 | [02_receipt_stock.mmd](./docs/erd/02_receipt_stock.mmd) |
| 销售与促销 | [03_sale_promotion.mmd](./docs/erd/03_sale_promotion.mmd) |
| 售后双额度 | [04_aftersale.mmd](./docs/erd/04_aftersale.mmd) |


## 路径基准与维护规则

以下定义仅适用于本仓库。**仓库根目录是包含`README.md`、`.gitignore`和`docs/`的顶层目录；clone后不再套一层`Project1`。**所有文档中的代码样式路径均为仓库相对路径；不记录或管理仓库外目录。

| 名称 | 逻辑定义与归属 |
|---|---|
| 仓库根目录 | 仓库顶层；本机checkout名为`Project1`，README、.gitignore、docs直接位于此处 |
| 仓库文档目录 | **仓库相对路径**`docs/`；共同维护的Spec/PRD/ERD主副本，可含中文，不是仅英文文件目录 |
| Markdown相对链接 | 只相对于该Markdown文件所在目录解析；代码样式的仓库相对路径不是同一种链接 |
| 运行数据根目录 | `DATA_ROOT`=仓库根目录下`data/`；由 DataRoot 校验项目标记、真实路径与可写性，不等于Markdown目录 |

### 文档主副本

仓库`docs/`是Spec/PRD/ERD的唯一协作主副本；不再维护仓库外副本，也不在文档中记录外部归档或备份位置。修改需求/设计时直接在仓库内评审，历史版本由Git记录。

### 计划路径与报告安排

文档中代码样式的`docs/evidence/...`、`docs/design/...`、`docs/decisions.md`或`docs/decisions/...`均为**仓库相对路径**。现有文档包括Spec/PRD/ERD/分工清单/共享规范，`docs/erd/`四个图源以及已创建的`docs/design/member_b/`和`docs/evidence/member_b/`（含B的V1/V2、AI评审、设计演进和框架复现记录）；仅`docs/decisions/`和`docs/decisions.md`仍为尚未创建的计划位置。不为了让计划“看起来完成”而创建空证据文件；也不新建重复路径治理记录。

工作阶段以仓库`docs/`的中文Spec/PRD/ERD为协作主副本；提交前将最终报告转为英文，并从提交包/最终交付目录移除中文工作稿，中文稿不作为并行最终报告。中文工作稿不是提交报告，不设正式§2.6/§3.7声明；正式声明只在英文最终报告中由本人按模板要求完成并签署，中文工作稿不替代声明。英文最终报告使用仓库根目录下的英文报告存放位置；最终文件名在报告装配时确定，本轮不新增报告子目录或改变Canvas安排；仓库根目录可包含中英文工作文档、代码和构建文件。

文档历史由Git记录，本文档不定义仓库外备份位置。运行数据上一版JSON快照、临时写文件及附件恢复副本属于运行时存储，使用下述已实现的`DATA_ROOT`子布局，**不得复制进仓库**。提交课程作业的ZIP/报告/演示证据不是运行数据，不用全局日志或压缩包通配符忽略它们。

### 运行数据与附件：当前布局和安全契约

已确认的是UTF-8 JSON单快照/schemaVersion、原子替换/上版恢复和附件相对路径＋哈希；`DATA_ROOT`已定为仓库根目录下`data/`，根解析与安全检查由 DataRoot 实现，损坏或不可写时明确阻断启动。详见[Spec运行路径设计待确认项](./docs/Project_Spec.md#101-运行路径契约与待确认设计)；不把剩余技术方案当作已批准部署规则，也不增加课程手工配置门槛。

旧文中的`data/`仅指运行数据逻辑根`DATA_ROOT`，旧`data/attachments/`统一表达为`DATA_ROOT/attachments/`；附件记录内只保存`attachments/<文件名>`，**相对于DATA_ROOT而非工作目录、JAR、仓库或Markdown目录**。根位置已定为仓库根`data/`；运行子布局为 snapshot.json、previous.json、writer.lock、*.next.json 和 attachments/；不自动创建业务货物。

路径安全契约：落盘的附件`relative_path`统一用`/`；拒绝盘符/UNC/根路径等绝对引用、URI、空路径、反斜杠、`.`/`..`段及解析后越出DATA_ROOT的引用。将根与目标规范化并校验最终真实位置（包括符号链接/联接点），以路径组成部分判断包含关系，不能用字符串前缀冒充。写入前及实际打开时均核查，避免链接把目标引到根外。CUI选择待导入源图片可使用用户本机路径，但**源绝对路径不得序列化成附件引用或写入Git材料**。

中文和空格是合法文件名字符，不用按空格分词或拼接Shell命令操作文件；目标使用独立 UUID 与 CREATE_NEW，碰撞或导入失败拒绝保存引用，不能默默覆盖现有文件或把源目录/用户名编码进目标名。复制/读取/校验失败不得保存“附件成功”引用，不覆盖已验证副本；没有附件时仍按既有规则允许审核，不因路径治理新增强制上传。对既有引用缺文件或哈希不符，不把它当作正常附件、不偷偷移除历史关联；完整恢复遇附件缺失、引用异常或哈希不符时阻写；未引用的导入副本不自动清理，不能擅改收货审核或业务恢复规则。附件单张上限已按`5,000,000`字节同步；失败不覆盖已验证副本，不发布无效引用。

### 忽略规则与提交前检查

当前未发现被跟踪的业务JSON、图片附件、课程PDF/DOCX或本地备份。`.gitignore`只对未跟踪文件生效，不会清除已经提交的路径/个人引用；本轮不重写历史或取消跟踪文件。旧提交仍可能保留原文引用，历史处理需要另行明确授权。

运行数据根已定为仓库根`data/`；忽略规则只覆盖仓库内路径，提交前仍须复查。当前针对仓库内误生成数据保留窄范围防线：根目录`data/`、`runtime/`、`attachments/`、`logs/`、`tmp/`、`backups/`、`.local/`，以及构建产物和明确的本机设置。课程源文件只按已知文件名防误拷贝，不全局忽略PDF/DOCX。样例请放明确跟踪的`docs/examples/`或后续测试样例位置，使用虚构/脱敏数据；目前没有这些样例文件。不要在真实运行数据目录中保存需要跟踪的唯一证据。

发布前检查Git diff、待提交清单、相对链接、路径/个人信息和数据文件；`.gitignore`不是敏感信息扫描器，改动不会自动上传GitHub。本轮v1.02发布后勘误提交的最终状态，以`git status`、`git log`和`git ls-remote`核验为准。

### 本轮Git核验记录

v1.02已发布：main提交`4d7b1f28c6d9caa0473092e77ab27aa1e724b983`，`git ls-remote origin refs/heads/main`返回同一提交，工作区干净；B设计成果及正式共享规范均已包含。本次发布后勘误提交以Git历史定位，不改变上述发布回执。

## 课程资料引用档案（不分发原文）

| 资料 | 必要定位与要求摘要 | 分发状态 |
|---|---|---|
| COMP603/ENSE600 2026 CJLU项目要求，`Projects1and2.pdf` | 阅读器/印刷p3–6：P1需求/真实设计迭代、Java CUI、集合/File I/O、OOP/SOLID、标准构建、日志/≤5分钟视频；p7–10：P2 GUI/Derby/Git/JUnit；p12–13：报告/源码/视频单ZIP | 未纳入仓库；具体日期仍核Canvas |
| Assignment 1报告模板，`Assignment1_Report_Template_CJLU2026.docx` | §1构建/凭证；§2.2需求ID/验收/E-D；§3.2–3.6范围、两版类图和真实评审/演化；§2.6/3.7本人反思及声明；附录A/B日志/贡献 | 未纳入仓库，不复制整份模板 |
| U2/U3及后续U4/U5/U6会话决定 | 保留Spec的来源代号、已确认规则和原章节定位；不以未分发附件冒称协作者已阅读原文 | 未纳入仓库，不记录个人路径/附件UUID |

本轮完成路径/Git核对及文档同步，沿用已记录的课程定位，不把历史全文阅读记录冒称本轮重新核读。

这些摘要只支持必要追踪，不代替课程原文或扩展业务需求；没有新增课程源文件/会话附件。

## 构建与运行

使用 JDK 17 和仓库唯一 Maven 工程。2026-10-21 的 PowerShell 7.6.5 是历史验证环境记录；2026-10-22 发布收尾重新核验的环境及结果见统一实现日志。脚本目标兼容 Windows PowerShell 5.1 与 PowerShell 7。一条命令复现离线构建、全部测试、可执行包依赖隔离及真实 JAR/CUI 冒烟：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-p1.ps1 -Offline
# 已安装 PowerShell 7 时也可执行（不要求安装）：
pwsh -NoProfile -File .\scripts\verify-p1.ps1 -Offline
```

首次依赖尚未缓存时去掉 `-Offline`。脚本只在其新建的临时独立项目根使用随机虚构账号、合法期初及销售，验证后清理该次临时目录，不改真实业务 data/，不输出或提交密码/哈希。逐步命令如下：

```powershell
# JAVA_HOME 已是 JDK 17 时无需重设；以下仅探测可用的本机临时工具。
$taskJdk17 = @((Join-Path $env:LOCALAPPDATA 'pdc-build-tools/jdk-17.0.20.1+1'), (Join-Path $env:TEMP 'p1-jdk17/jdk')) |
  Where-Object { Test-Path (Join-Path $_ 'bin/java.exe') } | Select-Object -First 1
if ($taskJdk17) { $env:JAVA_HOME = $taskJdk17 }
& "$env:JAVA_HOME/bin/java.exe" -version
./mvnw.cmd -B --no-transfer-progress clean verify
& "$env:JAVA_HOME/bin/java.exe" "-Dfile.encoding=UTF-8" -jar target/supermarket-management-0.1.0-SNAPSHOT-jar-with-dependencies.jar --check
# 首次在真实交互终端预置三角色账号；无默认密码、不回显密码，只存 B 哈希器的编码。
& "$env:JAVA_HOME/bin/java.exe" "-Dfile.encoding=UTF-8" -jar target/supermarket-management-0.1.0-SNAPSHOT-jar-with-dependencies.jar --init-accounts
# 以后正常启动：
& "$env:JAVA_HOME/bin/java.exe" "-Dfile.encoding=UTF-8" -jar target/supermarket-management-0.1.0-SNAPSHOT-jar-with-dependencies.jar
```

未找到上述临时 JDK 时选择已安装的 JDK 17；Enforcer 会拒绝 Java 8/其他主版本，不改为 Java 8 构建。首次工程依赖下载可能需要网络，业务运行离线；缓存齐全后加 `--offline`。本次实测平台为 Windows，其他平台命令未执行。

可执行包是带依赖的 `*-jar-with-dependencies.jar`，包含 Jackson；普通 JAR 没有内嵌依赖，不能把它单独启动视为已验证。主类为 `nz.ac.aut.pdc.supermarket.app.Main`，从可执行文件位置向上找项目标记，不把 shell 当前目录当 DATA_ROOT；搬动可执行文件时用 `--project-root <项目根>`。`--help` 查看参数，`--check` 实际装配并执行完整快照校验，不提交业务操作。

非交互启动可用 `--accounts <本地凭证JSON>` 初始化空存储：数组元素字段为 `accountId/username/passwordHash/role`，角色 ADMIN/MANAGER/CLERK，哈希兼容现有 B Sha256PasswordHasher。只初始化空存储，不能借此覆盖已有账号；本地文件放 `.local/` 等忽略目录，不上传密码、哈希或运行数据。无账号时明确提示登录未配置。`--init-accounts` 在已有快照时拒绝；密码/用户名输入中止不安装半套账号。

CUI 包含商品新增/修改、期初、收货/补正/审核及可选附件、促销、销售报价后模拟付款、补货、报损/处置/修正、受限历史/额度查询、单行与混合售后、取消/删除及整单冲销。售后先保存 IF-11 追溯再显示必要原单字段和本次结算；尚未确认时可退出并按显示的目标草稿号取消。`CANCELLED` 保留追溯且无退款、库存、额度影响。销售报价变化或工作副本过期拒绝成交，需重新报价确认。

请求结果未知先查原键。菜单15重试本次进程保存的原始请求；售后与冲销表单还允许输入原键及相同业务输入，支持重启后复用原结果。普通历史仍按店员本人、店长/管理员全部；售后凭证为精确原销售单号或打印的稳定销售行号，不提供跨人浏览。恢复异常会阻止启动，保留坏文件；管理员先备份完整 data/，使用 JsonStore 显式 PREVIOUS 恢复能力校验并恢复完整上一版及其附件，禁止手工拼接版本或静默清空。未在 CUI 中提供绕过损坏校验的写入口。

## 开发与发布边界

2026-10-21 v3 授权后由一个负责人维护完整单体，原 A/B 分工与设计保留为历史贡献和来源，不再以等待另一方接线作为实现状态。B 的 V1/V2 已获审计并包含于基线，不重做设计阶段。

本轮仅本地开发与验证。任何上传 GitHub、推送或合并 main，均须先展示具体差异供人工审核，再取得针对该次操作的明确批准；没有自动发布。使用独立工作树保护其他会话在途修改，实际分支和本地交付记录见统一实现日志。

文档历史由Git记录，本文档不定义仓库外备份位置；运行数据备份遵循`DATA_ROOT`（仓库根`data/`）内上述子布局，不能混用。课程交付仍以PDF、模板和Canvas要求为准。

## Maven框架与构建

工程使用固定版本 Maven Wrapper、JDK 17 Enforcer、JUnit 与 Jackson，以及 assembly 可执行包。当前命令、依赖与历史底座记录见 [Maven 框架说明](./docs/Maven_Framework.md)；当前实现和实际结果见 [统一实现日志](./docs/P1_Implementation_Log.md)。历史“空工程／尚无 Main／等待 A”只代表相应记录的形成时点。

IF-11 按已批准 [CR07-11](./docs/Project_Spec.md#175-cr07-11if-11-读取准入与局部取消留存) 实现；批准基线和课程 AC 不在本次改写。完整自动化测试证据、测试替身范围和真实链范围单独列明，不把课程人工材料算作已完成。
