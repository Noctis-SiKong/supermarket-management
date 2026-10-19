# P1最小Maven框架

日期：2026-10-20。由成员A负责、开发agent执行。仅工程底座；Spec/PRD/ERD v0.7及六工作包不变。

## 范围与文件

- `pom.xml`：单一Maven项目、Java release 17、构建/报告UTF-8、JUnit 4.13.2、Jackson databind 2.21.2；编译器、资源、测试和JAR插件固定版本。Enforcer拒绝非JDK17构建。
- `mvnw`、`mvnw.cmd`、`.mvn/wrapper/maven-wrapper.properties`：官方Maven Wrapper 3.3.4，Maven 3.9.11，分发文件SHA-256校验；无需系统安装Maven。脚本来自官方only-script分发，不包含Wrapper JAR。
- `src/main/java/`：标准业务源码位置，当前只用`.gitkeep`保留空目录；包所有权见[交接包§2.2](./Handoff_Package_For_B.md#22-代码包与公共类型边界)，领域类与接口待设计，不在底座阶段批量创建空类/空接口。
- `src/test/java/nz/ac/aut/pdc/build/FrameworkSmokeTest.java`：仅验证JDK17执行、JUnit发现和Jackson中文UTF-8往返；不实现存储、认证或库存等业务。
- `target/`：构建输出，已有忽略规则，不提交；没有创建data/或业务JSON。

这里的版本是工程/依赖版本，不是需求基线版本。不包含GUI、数据库、业务服务或统一持久化实现；没有Main-Class或业务入口，生成JAR不能视为可运行系统。

## 使用方法

安装/选择JDK17，将JAVA_HOME指向其安装根，并使终端使用该JDK；不把本机安装路径写进仓库。Windows PowerShell在仓库根执行：

```powershell
./mvnw.cmd -v
./mvnw.cmd --batch-mode --no-transfer-progress clean verify
```

Linux/macOS计划用法为`sh ./mvnw --batch-mode --no-transfer-progress clean verify`，本轮未在这些平台执行。首次Wrapper及依赖下载需网络；核心业务离线约束不代表首次工程依赖可凭空获得。缓存齐全后Windows可用`./mvnw.cmd --offline --batch-mode --no-transfer-progress clean verify`。

默认终端若是Java8，不代表项目改用Java8；应选择JDK17。构建文件明确强制JDK17。资源/源码UTF-8与测试JVM文件编码在工程设置，不依赖Windows默认编码。

## 验证与交接状态

本轮Windows实际使用Microsoft OpenJDK 17.0.8、Maven 3.9.11验证（仅记录版本，不记录本机安装路径）：

| 实际命令 | 结果 |
|---|---|
| `./mvnw.cmd -v` | 确认Maven 3.9.11及Java 17.0.8 |
| `./mvnw.cmd --batch-mode --no-transfer-progress clean verify` | BUILD SUCCESS；JUnit4发现2项框架测试，失败0、错误0、跳过0 |
| `./mvnw.cmd --offline --batch-mode --no-transfer-progress clean verify` | BUILD SUCCESS；相同2项框架测试通过，说明本机已有缓存下可离线构建 |

测试报告在被忽略的`target/surefire-reports/`；未将带本机路径的原始构建日志复制到仓库。由于无业务源码，离线打包提示空JAR，这是当前骨架状态；不以增加虚假业务主类消除该提示。未执行业务AC或全链验收。框架已随本次交付提交进入仓库，B可从该提交取得；B实际拉取、按交接包§4.2复现前，不记“已交付/可用”。本地可构建不等于B已复现。

成员B继续按交接包先做依赖登记、IF-07基础契约、V1、真实评审、人工决定/V2；其本人设计和接口条件满足且本框架在B副本实际可用后，只在统一框架内开发已授权范围。A的共享持久化与IF-11方案仍未实现，相关接入局部等待，不能以工程底座替代业务设计。

## 来源

选型依据：Spec §10 TC-07、BR-24，PRD-10；本次用户明确授权开发agent实现A侧Maven框架。技术来源：[Apache Maven Wrapper](https://maven.apache.org/tools/wrapper/index.html)、[Maven 3.9.11发布说明](https://maven.apache.org/docs/3.9.11/release-notes.html)、[Jackson 2.21.2发布说明](https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.21.2)，核查日期2026-10-20。课程材料未复制入仓库。
