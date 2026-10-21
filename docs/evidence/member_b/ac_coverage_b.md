# B 侧 AC → 证据索引（2026-10-21 更新）

> 汇总报告见 [`AC_SUMMARY_B_2026-10-21.md`](AC_SUMMARY_B_2026-10-21.md)；逐项证据见
> [`ac_execution_b_2026-10-21.md`](ac_execution_b_2026-10-21.md)。
>
> **不要把模块测试/集成测试当作全部 AC 已通过。**

## 1. 当前基线

- 分支：`member-b/merge-main-2026-10-21`
- 最新提交：`b1e8b77`
- 全量命令：`./mvnw -B clean verify`
- 结果：**176 tests / 0 failed / 0 error / 1 skipped**（BUILD SUCCESS）
- 1 skipped：A `AttachmentTest` 的符号链接平台差异

## 2. 状态汇总

| 状态 | AC |
|---|---|
| PASS（自动化） | AC-01、AC-05、AC-09、AC-12、AC-17、AC-19、AC-21、AC-30、AC-33、AC-34、AC-35、AC-46、AC-47、AC-52、AC-58、AC-79、AC-80、AC-82、AC-83/84、AC-89 |
| PARTIAL | AC-03、AC-06、AC-09、AC-10、AC-12–15、AC-17、AC-22、AC-30、AC-33/34/35、AC-37、AC-46/47、AC-51–59、AC-80–84、AC-86–90 |
| BLOCKED | AC-02（Main/pom）、AC-07/08（人工材料）、AC-85（A 恢复）、AC-87/88（A IF-11 准入） |
| N/A | AC-25/38/39/49/50（已撤销） |

## 3. 自动化证据索引

- 售后规则：`AftersaleServiceTest`（13）、`AftersaleCommitValidatorTest`（6）
- 售后真实提交：`SharedOperationsIntegrationTest`、`ReversalIntegrationTest`
- 售后草稿/IF-11：`AftersaleDraftServiceTest`、`AftersaleDraftValidator` 相关测试
- 补货/报损：`StockOperationsIntegrationTest`、`StockOperationValidatorTest`
- 冲销：`ReversalServiceTest`（7）、`DependencyCheckerTest`（4）、`ReversalCommitValidatorTest`（2）
- 认证/CUI：`LocalAuthServiceTest`（7）、`SharedIdentityResolverTest`（2）、`CuiAppTest`（9）、`InputValidatorTest`（4）
- 查询：`QueryServiceTest`（9）

## 4. 使用限制

1. 全量测试运行在真实 A `JsonStore` 的测试根目录/临时目录，不写生产 `data/`。
2. PASS 仅表示本轮自动化证据覆盖该 AC 核心数据，正式课程验收仍需 CUI 演示、报告/视频和人工确认。
3. 因无 `Main`/pom 入口，AC-02 及需要 `java -jar` 的正式演示仍阻塞。
4. AC-87/88 的 A 侧 IF-11 受控读取端口尚未确认；B 侧只实现草稿/追溯编排，不宣称正式联调完成。






