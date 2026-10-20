# B 侧框架复现记录

日期：2026-10-20（Asia/Shanghai）｜成员：B（CGwal）｜交付提交：`f554cc24259c9dfb68b9ebabd3a49f2a19479704`（与 `origin/main` 一致）
性质：交接阶段复现记录；仅验证工程底座，不验证任何业务规则或 AC。

## 1. 环境核对

| 项目 | 实际值 | 结论 |
|---|---|---|
| 操作系统 | Windows 11 amd64 | 可用 |
| Git | 2.54.0.windows.1 | 可用 |
| JDK | Oracle JDK 17.0.12（构建时显式设置 `JAVA_HOME`） | 符合 Enforcer `[17,18)` |
| Maven | Wrapper 3.3.4 → Apache Maven 3.9.11 | 符合 Wrapper 配置 |
| 默认 JDK | 本机终端默认 JDK 21 | **本项目不得使用默认 JDK**；所有构建显式切 JDK 17 |
| 工作区 | `git status` 干净；HEAD 与远端 main 一致 | 交付提交已取得 |

Maven 用户设置（本机全局）使用自定义本地仓库与阿里云中央镜像；不影响仓库内 `pom.xml` 的 JDK 17 配置，构建已验证通过。

## 2. 实际执行的命令与结果

| # | 命令 | 实际结果 |
|---|---|---|
| 1 | `git status --short --branch` | `## main...origin/main`（无改动） |
| 2 | `git rev-parse HEAD` | `f554cc24259c9dfb68b9ebabd3a49f2a19479704` |
| 3 | `git remote -v` | `origin https://github.com/Noctis-SiKong/supermarket-management.git` |
| 4 | `git ls-remote origin refs/heads/main` | `f554cc2…`（与本地 HEAD 一致） |
| 5 | `./mvnw.cmd -v` | Maven 3.9.11；Java 17.0.12（Oracle）；Wrapper 3.3.4 |
| 6 | `./mvnw.cmd --batch-mode --no-transfer-progress clean verify` | BUILD SUCCESS；`Tests run: 2, Failures: 0, Errors: 0, Skipped: 0` |
| 7 | `./mvnw.cmd --offline --batch-mode --no-transfer-progress clean verify` | BUILD SUCCESS；同样 2 项测试通过（缓存可用） |
| 8 | 远端全新克隆（同一提交）后 `./mvnw.cmd --offline --batch-mode --no-transfer-progress clean verify` | BUILD SUCCESS；2 项测试通过（交付提交自足可构建） |

测试报告位于被忽略的 `target/surefire-reports/`；本记录不复制原始日志与本机路径入仓库。

## 3. 结论与边界

- 结论：**框架在 B 副本可用（已复现/可用）**；交付提交可从远端独立取得并构建。
- 验证范围：仅工程底座（JDK 17 执行、JUnit 发现、Jackson 中文 UTF-8 往返）。
- 不验证业务规则、库存、金额、持久化实现或任何正式 AC；当前无业务主类、无业务实现，JAR 为空属预期。
- 未提交、未推送；`target/`、临时克隆与本机路径不进入仓库。
- AI 使用留痕：本步已登记至 [ai_usage_log_b.md](./ai_usage_log_b.md) 第 1 条（阶段＝交接/环境）。

## 4. 备注与下一步

- 本机默认 JDK 21 与 Enforcer `[17,18)` 冲突：后续每次构建前显式设置 `JAVA_HOME` 指向 JDK 17 安装根，并保留 `mvnw -v` 输出作为环境证据。
- 下一步：书面接受交接（WP/包目录/IF 边界/材料取得核对）→ 依赖登记 → IF-07 基础契约 → B 范围类图 V1。
