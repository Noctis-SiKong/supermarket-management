# P1 Maven 框架与运行

当前更新：2026-10-22。单一 Maven 工程已有真实业务及 Main/assembly；运行命令见 README，验证见 P1_Implementation_Log.md。下文 2026-10-20 的空工程/冒烟数字保留为历史记录，不代表当前实现状态；Spec/PRD/ERD 未修改。

## 2026-10-20 历史底座：范围与文件

- `pom.xml`：单一Maven项目、Java release 17、构建/报告UTF-8、JUnit 4.13.2、Jackson databind 2.21.2；编译器、资源、测试和JAR插件固定版本。Enforcer拒绝非JDK17构建。
- `mvnw`、`mvnw.cmd`、`.mvn/wrapper/maven-wrapper.properties`：官方Maven Wrapper 3.3.4，Maven 3.9.11，分发文件SHA-256校验；无需系统安装Maven。脚本来自官方only-script分发，不包含Wrapper JAR。
- `src/main/java/`：标准业务源码位置，当时只用`.gitkeep`保留空目录；包所有权见[交接包§2.2](./Handoff_Package_For_B.md#22-代码包与公共类型边界)，领域类与接口待设计，不在底座阶段批量创建空类/空接口。
- `src/test/java/nz/ac/aut/pdc/build/FrameworkSmokeTest.java`：仅验证JDK17执行、JUnit发现和Jackson中文UTF-8往返；不实现存储、认证或库存等业务。
- `target/`：构建输出，已有忽略规则，不提交；没有创建data/或业务JSON。

这里的版本是工程/依赖版本，不是需求基线版本。不包含GUI、数据库、业务服务或统一持久化实现；没有Main-Class或业务入口，当时生成JAR不能视为可运行系统。

## 使用方法（当前仍适用）

安装/选择JDK17，将JAVA_HOME指向其安装根，并使终端使用该JDK；不把本机安装路径写进仓库。Windows PowerShell在仓库根执行：

```powershell
./mvnw.cmd -v
./mvnw.cmd --batch-mode --no-transfer-progress clean verify
```

Linux/macOS计划用法为`sh ./mvnw --batch-mode --no-transfer-progress clean verify`，本轮未在这些平台执行。首次Wrapper及依赖下载需网络；核心业务离线约束不代表首次工程依赖可凭空获得。缓存齐全后Windows可用`./mvnw.cmd --offline --batch-mode --no-transfer-progress clean verify`。

默认终端若是Java8，不代表项目改用Java8；应选择JDK17。构建文件明确强制JDK17。资源/源码UTF-8与测试JVM文件编码在工程设置，不依赖Windows默认编码。

## 2026-10-20 历史验证与交接状态

本轮Windows实际使用Microsoft OpenJDK 17.0.8、Maven 3.9.11验证（仅记录版本，不记录本机安装路径）：

| 实际命令 | 结果 |
|---|---|
| `./mvnw.cmd -v` | 确认Maven 3.9.11及Java 17.0.8 |
| `./mvnw.cmd --batch-mode --no-transfer-progress clean verify` | BUILD SUCCESS；JUnit4发现2项框架测试，失败0、错误0、跳过0 |
| `./mvnw.cmd --offline --batch-mode --no-transfer-progress clean verify` | BUILD SUCCESS；相同2项框架测试通过，说明本机已有缓存下可离线构建 |

测试报告在被忽略的`target/surefire-reports/`；未将带本机路径的原始构建日志复制到仓库。由于无业务源码，离线打包提示空JAR，这是当时的骨架状态；不以增加虚假业务主类消除该提示。未执行业务AC或全链验收。框架已随本次交付提交进入仓库，B可从该提交取得；B实际拉取、按交接包§4.2复现前，不记“已交付/可用”。本地可构建不等于B已复现。

成员B继续按交接包先做依赖登记、IF-07基础契约、V1、真实评审、人工决定/V2；其本人设计和接口条件满足且本框架在B副本实际可用后，只在统一框架内开发已授权范围。A的共享持久化与IF-11方案仍未实现，相关接入局部等待，不能以工程底座替代业务设计。

## 来源

选型依据：Spec §10 TC-07、BR-24，PRD-10；本次用户明确授权开发agent实现A侧Maven框架。技术来源：[Apache Maven Wrapper](https://maven.apache.org/tools/wrapper/index.html)、[Maven 3.9.11发布说明](https://maven.apache.org/docs/3.9.11/release-notes.html)、[Jackson 2.21.2发布说明](https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.21.2)，核查日期2026-10-20。课程材料未复制入仓库。

## 2026-10-21 单体装配补记

`Main` → `Application` → 真实 B 认证/库存/额度/冲销与 A 业务/JsonStore；原 BApplication 仅委托同一装配。assembly-plugin 3.7.1 打包 Jackson，Main-Class 指向真实 Main；以 README 的带依赖 JAR 运行。JDK 17.0.20.1、Maven 3.9.11 的本次命令及结果统一见 P1_Implementation_Log.md，不覆盖上方历史复现证据。

## 2026-10-22 当前打包与验证

当前 `src/main/java/` 已包含业务源码；普通 JAR 与 assembly 带依赖 JAR 均设置真实 `nz.ac.aut.pdc.supermarket.app.Main`。独立运行使用 `target/supermarket-management-0.1.0-SNAPSHOT-jar-with-dependencies.jar`（内含 Jackson），不能只以 Main-Class 声明替代实际运行验证。README 说明首次账号配置、`--project-root`、`--check` 及正式 CUI 启动；不自动建立业务货物。

`powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-p1.ps1 -Offline` 在 Windows PowerShell 5.1 实跑通过；PowerShell 7 的本轮实际版本、命令和结果见统一实现日志。验证使用独立临时数据；本机已有依赖缓存，不代表任意新机器无需首次下载。脚本中文源码为 UTF-8 BOM，生成账号 JSON 为 UTF-8 无 BOM，并分别设置及还原原生命令输入与控制台输出编码。上方“无 Main／空 JAR／等待 A”只保留为 2026-10-20 的历史结论，不适用于当前交付。自动化结果不替代课程验收、报告、视频和签署。
