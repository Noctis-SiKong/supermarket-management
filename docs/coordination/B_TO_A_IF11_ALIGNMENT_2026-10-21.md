# B → A：IF-11 / CR07-11 接口对齐请求（2026-10-21）

B 已按 CR07-11 完成 B 侧编排：售后 DRAFT 创建、`READ_FOR_AFTERSALE` 追溯、`CANCEL_AFTERSALE` 取消、DRAFT 最终提交。合并树当前 151 tests 通过。以下需要 A 确认/补接口，避免 B 继续按推断实现：

## R1. IF-11 读取准入

- 现状：B `SharedOperations` 先创建 DRAFT，再写 `READ_FOR_AFTERSALE` 追溯，随后从 A `WorkingCopy` 读取原销售行，再提交售后。
- 问题：B `SharedSaleReader` 直接读 `DOCUMENT/DOC_LINE/SALE_LINE`；A `AOperations.readSaleLine` 仍是普通历史投影，店员只能读本人记录，不是 IF-11 跨人凭证例外。
- 请求 A 明确：
  1. IF-11 是否由 A 提供 `readSaleLineForAftersale(op, wc, voucher/lineId)` 类的受控读取端口；还是允许 B 在 DRAFT+追溯保存后直接读 `WorkingCopy`？
  2. 若由 A 提供，返回字段清单/拒绝码；B 将替换 `SharedSaleReader`，不复制 A 的定位/权限算法。
  3. 若允许 B 直读，请确认“先保存追溯、再返回最少字段”的边界由 B 保持。

## R2. 事件形状

- B 当前写：
  - `DOC_EVENT.action="READ_FOR_AFTERSALE"`，`document_id=目标售后`，`actor_id=读取人`，`detail_snapshot={originalSaleLineId,barcode,quantity,amount,returnedLineId}`；
  - `DOC_EVENT.action="CANCEL_AFTERSALE"`，取消时 `DOCUMENT.status=CANCELLED`。
- 请求 A 确认这两个 action 与 `detail_snapshot` 字段符合 A 侧校验/恢复约定；如字段名需调整，请给最终映射。

## R3. DRAFT 最终提交

- B 对已有 DRAFT 的最终售后提交改为：
  - 对 `DOCUMENT` 打补丁 `status=CONFIRMED, confirmedBy=提交人`；
  - 追加 `DOC_EVENT(CONFIRM)`、`DOC_LINE/REDEMPTION/REPLACEMENT/STOCK_ENTRY/MONEY_ENTRY`；
  - 不再重复插入 `DOCUMENT/AFTERSALE`。
- 请求 A 确认 `JsonStore` 的公共校验/`DraftDeletionGuard` 接受该 DRAFT→CONFIRMED 补丁路径，且 `AFTERSALE` 扩展仍只保留 `document_id`。

## R4. 取消与删除边界

- B 已实现：有 `READ_FOR_AFTERSALE` 的 DRAFT 只能 `CANCELLED`（`AftersaleDraftService.cancel`）；无追溯的草稿仍走既有 `AFTERSALE_DRAFT_DELETE`。
- 请求 A 确认取消路径与 `DraftDeletionGuard` 的 `DRAFT_HAS_READ_TRACE`/`DRAFT_HAS_EFFECTS` 判定不冲突。

## 当前证据

- 提交：`f9da4e1 feat(member-b): add IF-11 read trace and aftersale draft lifecycle`
- 合并树：151 tests；新增 `AftersaleDraftServiceTest`（建草稿→追溯→取消）。
- 未声称：A 侧 IF-11 接口已交付、真实跨人授权联调完成、AC 已验收。
