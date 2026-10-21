# Supermarket Management System Functional PRD

## 1 Document Information and Authority

| Item | Content |
|---|---|
| Version/Date | 0.7, 2026-10-20, Asia/Shanghai; HEAD baseline + this round's quota-authorization rebuild |
| Functional baseline | [Current copy of the Spec](./Project_Spec.md) v0.7; the jointly maintained mainline is the repository docs/ |
| This document | [Current copy of the PRD](./Project_PRD.md); repository-relative path `docs/Project_PRD.md` |
| Status | v0.7 is frozen as the requirements baseline (2026-10-20, effective with this commit); approved and pending status is governed solely by Spec §17; D1/D2 have received explicit user approval; CR07-09 has been revoked and CR07-10 is in effect; no code/formal division of work |
| Revision/History | v0.7 was rebuilt under control from HEAD and synchronized with D1/D2; on 2026-10-20, after the user explicitly approved D1/D2, it was frozen as the requirements baseline; on 2026-10-20 SYNC-01–05 were synchronized under control and AC-86/87 were added (version number unchanged); on 2026-10-20 CR07-09 was added for an individual exception to store-manager return/exchange eligibility (version number unchanged); on 2026-10-20 CR07-10 revoked that exception and added the "Quality/Other" reason selection and other-reason text (version number unchanged); approval status appears only in Spec §17; historical versions are recorded by Git, and this document does not record external backup locations |

Course PDF hard constraints take precedence; the Spec is the functional baseline. U4 is the item-by-item audit message of 2026-10-20; the latest decisions were first written into the Spec, and this draft is synchronized accordingly. U3 is the original session attachment from that time (not included in the repository); §II authorizes taking the user's earlier single recommendation as a decision, and §IV–VI list the Q/A decisions. U2 is the original session attachment from the previous round (not included in the repository); its single recommendation is now incorporated under the U3 authorization, and is not re-suspended because an old Spec did not record it or because of the word "recommendation".

Previous version records were read in full; during the historical modelling phase the then-designated Spec/PRD were read, and C p3–5/p7–8 and the full text and tables of T were reviewed; the original version had fully read C: C `Projects1and2.pdf` (15 pages) and T `Assignment1_Report_Template_CJLU2026.docx` (body/13 tables/footer/footnotes and endnotes), the original Spec/PRD and the two user attachments, all of which are readable. C citations refer to the same pages 1–15 in reader and print; T is located by heading/Figure/table field, without guessing pagination. The DOCX was checked as read-only OOXML; print pagination was not verified; no PDF or template was modified. Evidence gaps for Canvas and actual product data are checked only in Spec §17 (historical mapping in §13), and no claim of verification is made.

E=Essential, a capability that P1 must currently provide; D=Desirable, an optional enhancement. Confirming a business rule, scheduling priority, and whether something is already implemented are different dimensions: the three original sub-capabilities of PRD-14 have all been revoked by U6 and are provided in neither P1 nor P2; the attachment support E in PRD-15 follows the already-confirmed essential capability and the original Spec mapping, and a single non-mandatory image upload does not make the capability D. This draft only writes documentation; no business code, diagrams, final report or test results were produced, and student reflection must not be ghost-written or submitted as AI translation.

Path conventions: this repository is the single master copy of the documentation; the repository root is determined by `README.md`, `.gitignore` and `docs/`; business rules and course submission requirements are unchanged. The report arrangement is unchanged: during the working phase the repository's English version is the collaborative mainline, and the corresponding Chinese version is kept by the owner outside the repository in the designated Chinese-version directory (user documentation convention, see S §17.6); before submission, the final English report and the submission package still do not contain Chinese working drafts. Course materials are not included in the repository.

U5 is this round's decision on promotion allocation and the dual after-sales quota; U6 is the subsequently added final scope decision. U6 governs: product production/shelf-life/expiry data, near-expiry/expired reminders, batch management/selection and expired-sale blocking are cancelled in both P1 and P2, and are not listed as pending or reserved for the future. Base stock is handled only as product + on-shelf/in-warehouse quantity, and no rule for selling expired products is inferred. The operator time, the promotion validity period and the existing 30-day after-sales period remain in effect.

### Path Conventions and Copy Identity

<!-- PATH-COPY-BEGIN -->
Path conventions: this file resides in the repository `docs/`; Markdown links are resolved relative to the directory containing this file, and the code-style `docs/evidence/...`, `docs/design/...`, `docs/decisions...` are all **repository-relative paths**. There is no additional `Project1` level under the repository root; directories outside the repository are neither recorded nor managed in the documentation.
<!-- PATH-COPY-END -->

Document history is recorded by Git, and this document does not define backup locations outside the repository. Runtime snapshot backups are based on `DATA_ROOT`. Currently `docs/evidence/`, `docs/design/`, `docs/decisions/` and `docs/decisions.md` have not yet been created; all are planned paths and cannot serve as evidence of completed work.

## 2 Product Overview and Objectives

Provide local PC Java CUI operations for the administrator, store manager and staff of a single physical supermarket: actual receipt review, two-position quantity stock, sales payment confirmation, single-promotion selection, customer returns and exchanges, and loss/voiding traceability. The goal is to make stock, the actual paid amount of the original transaction and after-sales outcomes verifiable, while retaining genuine course design/HAI evidence. Existing requirements are organized around what is lightweight, usable and genuinely needed, without adding mall/chain features or production metrics.

Single machine, single process, core offline, Maven/JDK17/JUnit4.13.2, Jackson UTF-8 JSON/schemaVersion, BigDecimal/RMB and Asia/Shanghai are all current baselines; the course prescribes Java/CUI, collections, File I/O and a standard build, while specific libraries/versions are user choices and are not misrepresented as course-specified. First-time acquisition of build dependencies and offline business operation are verified separately. In the future only a barcode-scanning/hardware interface is retained; no dedicated device is connected, and there is no payment gateway or held order.

## 3 User Roles and Permissions

| Formal role | Established responsibilities/permissions and operating policy | Spec basis |
|---|---|---|
| Administrator | Enters as administrator and performs system maintenance and repair; may technically review receipts but in principle does not handle routine review; may view all documents, correct loss registration and authorize voiding | §5; Q02/Q10, Q05 (including C) |
| Store manager | Purchasing/receipt registration and review, self-review permitted/no two-person requirement; query of all documents; loss registration and voiding authorization; may query/audit returns and exchanges, with no eligibility exception and no per-transaction approval | §5; Q04/Q05/Q08/Q10; CR07-10 |
| Staff | May submit and confirm routine returns and exchanges directly, without waiting for the store manager per transaction; may query only documents they handled themselves | §5; Q08/Q09/Q10 |

The specific operator must be traceable, and the back end is a CUI. Administrator/store-manager/staff accounts are preconfigured locally, with username + password and passwords stored as hashes, and there is no network authentication. Product maintenance/promotion configuration: store manager, with the administrator able to maintain and correct; sales/replenishment: staff and store manager; loss registration: registered by the store manager, corrected by the administrator; query: all for the store manager/administrator and own only for staff; the administrator may technically review receipts but in principle does not handle routine review. Other existing authorizations are not expanded. Receipt registration follows the store manager's responsibilities, and the corresponding operations are by default not open to unauthorized roles.

## 4 P1 Scope, Exclusions and P2 Extensions

### 4.1 P1 Scope

The original numbering mapping is retained; all three PRD-14 sub-items have been revoked, and the remaining PRDs correspond to the same-numbered FRs in the Spec. The three FR-14 sub-items have been revoked by U6; the remaining existing essential capabilities stay E. Authorized reversal voiding already belongs to P1 and is no longer a phase-pending item or a mere P2 candidate.

### 4.2 Exclusions and Boundaries

Not doing: chain/mall, fractional weighed quantities, purchase requisition/purchase order pre-approval, mandatory two-person review, per-transaction store-manager approval of routine returns and exchanges, a third non-sellable stock bucket, barcode scanning that depends on dedicated hardware, payment gateways, held orders, GUI upload pages. Replenishment/correction is separate from supplier returns, and purchase returns to suppliers are not included in P1. Finance/tax/payroll/membership and other functions that were never confirmed are not added. Blocking of expired-product sales has been cancelled by U6, and no rule for expired sales is inferred.

### 4.3 Course Requirement Mapping

| Course mapping ID | Verification conclusion | Spec and course location |
|---|---|---|
| K1 Requirements | Appropriate scale, core functions/scope/user interaction; requirement numbering and Essential/Desirable | S CR-01/15; C p3 §1.1, p4 rubric; T §2.1/2.2 |
| K2 Design | Use case diagram, scope/constraints/assumptions; class diagram including attribute-method relations; AI review and at least two design iterations; independent student reflection | S CR-02–05; C p3 §1.1/1.2, p5 rubric; T §2.6, 3.1–3.7 |
| K3 Implementation | Java, working CUI; encapsulation/inheritance/polymorphism/abstraction; interfaces/abstract classes/concrete classes; SOLID, modularity, correspondence with the final design | S TC-01–03/06; C p4 §1.3, p5 rubric |
| K4 Build and Data | Standard Maven or Gradle, compile and run, no project-specific manual configuration; appropriate Java collections and genuine File I/O persistence | S TC-04/05; C p4–5, p13 Submission Format; T §1 |
| K5 Interaction Quality | Clear feedback for invalid input, each step can be exited, no unhandled exceptions; an evaluation target for the excellent band | S FR-11, BR-18, NF-02; C p5 rubric 1.3 |
| K6 Process Deliverables | AI log, ≤5-minute video, source code, Word/PDF report, team contributions, version backup and a single ZIP | S CR-10–14; C p4 §1.4, p6, p12–13; T Appendix A/B |

In this text, S denotes this round's Spec. JDK17, RMB and decimal currency come from S TC-07 and BR-09 and are Spec user constraints; they are not misrepresented as course-specified versions or currencies. The project has chosen Maven and Jackson/JSON per U3; fixed package names, MVC and additional patterns have not been added as P1 course hard requirements. The P1 weight is 40% of the course, within which requirements are 20%, design 30%, implementation 30% and log/video 20% (S §3; C p3–6).

### 4.4 P2 Extensions Only

Only references Spec §14: P2-01 Java GUI (Swing/JavaFX); P2-02 Derby Embedded/JDBC or Hibernate/data access layer; P2-03 layering and adapter patterns; P2-04 JUnit4 with at least 5 database/key business tests; P2-05 genuine Git evolution/.git/paired GitHub links; P2-07 database excellent band with more than 3 distinct reads and 3 distinct writes. P2-06 is only a future stocktake discrepancy/adjustment candidate; ordinary authorized voiding is already in P1 and is not reserved again. There is no automatic migration commitment.

P2 is a significant extension of the same topic, and any special topic change requires prior lecturer approval (C p7). P1 does not implement GUI/Derby early; P1's use of the user-selected JUnit4.13.2 does not mean it takes on the P2 database test threshold early. Receipt/image/replenishment/promotion/after-sales/loss/ordinary voiding cannot be deferred to P2.

### 4.5 P2 Marking Check

The previous version checked C reader/print pages 7–10; this round re-verified the relevant hard constraints without changing the marking; the results below are all P2 internal weights and must not be conflated with the external weight of P2 as 60% of the course.

| Actual PDF item name | Location | Weight | Comparison with Spec |
|---|---|---:|---|
| Component 2.1 Graphical User Interface (GUI) | p7 requirement; p9 marking table | 20% | S §14 GUI 20%, consistent |
| Component 2.2 Database | p7 requirement; p9 marking table | 20% | S §14 DB 20%, consistent |
| Component 2.3 Software Functionality, Design, Implementation & Testing | p8 overall heading | 40% | S §14 "functionality/design/implementation/testing 40%", consistent |
| 2.3.1 Functionality & Usability | p8 requirement; p9 marking table | 15% | S §14 first sub-item 15%, consistent; not reclassified as architecture |
| 2.3.2 Architecture; the marking table gives Architecture & Design Patterns | p8; p10 | 10% | S §14 second sub-item 10% and P2-03 patterns/layering, compliant; the naming difference between the two PDF places is recorded as-is |
| 2.3.3 Version Control (Git / GitHub) | p8; p10 | 5% | S §14 third sub-item 5% and P2-05, compliant |
| 2.3.4 Testing (JUnit Framework); the marking table gives Testing, JUnit | p8; p10 | 10% | S §14 fourth sub-item 10% and P2-04, compliant |
| Component 2.4 AI Usage Log and Demonstration Video | p8–9 requirement; p10 marking table | 20% | S §14 "log and video 20%", consistent |

The PDF supports the existing marking items and the marking text is unchanged; the conclusion was closed in Spec §13 P2-SCORE. The business closure in this round's Spec comes from the U3 authorization and is not a marking correction.

## 5 Functional Requirements and Traceability

The numbering below follows the original PRD; the original FR-14 suffix is retained only for revocation traceability and is not a function. Each item's purpose/preconditions/flow/rules/outcome come from the Spec, and the operator scope follows §3/Spec §5; historical boundary traceability cites Spec §13; current approval status appears only in Spec §17, and no duplicate problem description or status is copied.

### PRD-01 Roles, Administrator Policy and Operator Traceability

Corresponds to Spec FR-01, BR-04/21, §5; E. Three roles/operators are identifiable; the administrator performs maintenance and repair, may technically review, but routine review is in principle done by the store manager; staff handle routine returns and exchanges directly, without requiring the store manager to be online. Entry/operations follow the role and the specific operator is recorded; queries are all/own. Identity interaction follows R-AUTH local account login.

Traceability: Spec §13 Q02; formal scenarios AC-09/10/28. The business specification has been incorporated into the baseline, and the whole item is not re-questioned merely because these entries still relate to detailed issues.

### PRD-02 Barcode Products with Integer Quantities

Corresponds to Spec FR-02, BR-05/06, §9; E. Fixed packaging means integers throughout the business; the barcode is the sole primary key, and the name is brand + product name. Items are located by barcode, and fractional business quantities are rejected. Every included product has a valid unique barcode; sampling is done before go-live, abnormal products are handled as manual exceptions or excluded for the time being, and actual data evidence checking cites Q03-DATA without reopening the primary key decision; for input boundaries see only R-INPUT.

Traceability: Spec §13 Q03; formal scenarios AC-21/29. The business specification has been incorporated into the baseline, and the whole item is not re-questioned merely because these entries still relate to detailed issues.

### PRD-03 Pending Actual Receipts and Approved Warehouse Entry

Corresponds to Spec FR-03, BR-03/07/19, §7.1; E. Actual arrival → create a pending record, with the quantity listed separately and not counted as on-shelf/in-warehouse → review by the store manager (self-review permitted) or a technically authorized administrator → on approval, a single entry into in-warehouse / on return for correction, nothing is booked. Pending records cannot be sold or replenished; re-approval after correction is recorded only once; repeated review is idempotent; absence of an image does not cause rejection. This is not purchase pre-approval or a supplier return.

Traceability: Spec §13 Q04 (including A); Q02; formal scenarios AC-04/11/24/26. The business specification has been incorporated into the baseline, and the whole item is not re-questioned merely because these entries still relate to detailed issues.

### PRD-04 Two Positions and Opening Stock Entries

Corresponds to Spec FR-04, BR-06/08/20, §7.1; E. Opening stock may be registered by the store manager/administrator, not by staff; for approval and synchronization see Spec §17 SYNC-01. The on-shelf and in-warehouse positions are mutually exclusive and non-negative; opening stock is recorded through opening inbound ledger entries, and balances aggregate ledger entries, so the cache can be rebuilt. Check balance → source ledger entries → inbound records for that product to verify anomalies; a stock-out at the corresponding position is rejected when insufficient, and totals cannot be used to bypass this. Position is not a product enabled status.

Traceability: Spec §13 Q05; the Q02 permission relation is R-AUTH; formal scenarios AC-12/13/22/30. The business specification has been incorporated into the baseline, and the whole item is not re-questioned merely because these entries still relate to detailed issues.

### PRD-05 On-shelf Sales, Payment Confirmation and Atomic Stock-out

Corresponds to Spec FR-05, BR-03/07/10/24/28; E. Only on-shelf items can be sold; in-warehouse items must first be replenished; quantities are integers, stock must be sufficient, and payment must be completed. Select items → merge the same product at the same price while keeping a stable line ID → candidate pricing → confirm payment received for the whole order/deduct stock once → save a snapshot of name/unit price/quantity/promotion/allocated discount/actual paid and the stable line ID. If any line fails, the whole order does not take effect; no gateway is connected and no orders are held.

Traceability: Spec §13 Q01/Q06; the Q02 permission relation is R-AUTH; formal scenarios AC-04/13/27/31. The business specification has been incorporated into the baseline, and the whole item is not re-questioned merely because these entries still relate to detailed issues.

### PRD-06 Single-promotion Order Pricing

Corresponds to Spec FR-06, BR-09/11/28, §7.3, ERD-C01/02; E. For the whole order the discount/amount payable is determined first, and a single promotion is selected without stacking; only applicable lines participate, weighted by the pre-discount subtotal, and are allocated using simple largest remainder in integer cents, with ties broken by ascending stable sales line ID. There is no multi-round capped recalculation; line discount/actual paid are non-negative, the discount does not exceed the original amount, and the total is conserved, otherwise the entire operation is rejected. The stable ID and the line-level actual paid snapshot are saved, and refunds are not based on the list price. CR07-02 and AC-41/77/78.

Traceability: Q07/M-ROUND/R-PROMO; AC-16/32/41/44/54/55.

The after-sales-specific read exception is executed per Spec §17 SYNC-02 and CR07-11: it locates precisely by the customer's original sales voucher, returns only the necessary fields, and does not expand ordinary cross-person historical permissions. IF-11 traceability returns the minimum original sales data needed to process after-sales only after IF-05 has saved reliably and successfully; if saving fails, nothing is returned for the time being. When an after-sales draft that has already successfully saved read traceability is cancelled, it enters CANCELLED, retaining the association between the actual reading operator—original sales order—target after-sales order, without physical deletion; drafts without read traceability are still deleted under the original rules. Cancellation produces no refund, stock or quota change, and this cancellation flow does not apply to completed after-sales. Specific fields/transaction steps are given in plan A and shared specification §5.4; centralized audit and real integration testing remain outstanding.

### PRD-07 Original-sale Returns and Partial Refunds

Corresponding Spec FR-07, BR-12/13/14/21/26/29, §7.4 and ERD-C03–09; E. The store clerk links the original sale and verifies the existing 30-day and eligibility rules; any non-compliance is rejected outright, with no case-by-case exception for the store manager; the return/exchange reason is selected as "quality/other", and "other" requires reason text, with the text not changing eligibility; read the original line's actual paid and both remaining quotas → compute at two decimals HALF_UP on P×q/Q with capping, use the remaining amount for the final settlement → atomically record the refund/redemption/loss disposition. Each original sale line maintains its after-sales redemption ledger independently. Let Q be the original purchase quantity and P the original line's actual paid; valid returned quantity Qr/amount Mr, exchanged quantity Qx/original-product offset amount Mx; remaining quantity = Q−Qr−Qx, remaining amount = P−Mr−Mx. Both 0≤Qr+Qx≤Q and 0≤Mr+Mx≤P must hold. An exchange first occupies the original sale line's quantity and actual paid amount, and subsequent returns may use only the remaining quota; an effective redemption must not be overwritten by a later exchange. The partial return/exchange quantity q must be positive and ≤ the remaining quantity; when not settled, the amount occupied this time = min(HALF_UP(P×q÷Q,2), remaining amount), and settling the remaining quantity for the last time occupies the entire remaining amount. A refund directly uses the actual paid stored on the original sale line, does not re-allocate the refund budget across lines, and does not recompute by original price or the current campaign. An exchange occupies the original product's offset quota, not the new product's selling price or the actual top-up/refund difference; an identical-item, identical-quantity quality exchange with zero difference must also redeem the original quantity and the corresponding actual paid quota. Each redemption records the original sale line, the source return/exchange document and its details, quantity, amount, type, operator, time, idempotency key and status; repeated submission of the same request does not occupy again. Refunds, redemptions and replacement on-shelf deductions are synchronized under the existing atomicity/duplicate-prevention rules, and a failure leaves nothing partially effective. A legally authorized reversal releases the original quantity/amount through linked reverse records, retaining the original record and reason, without physical deletion or duplicate release.

Traceability: Q08/R-REFUND/R-AFTERSALE-LEDGER; AC-09/15/17/33/46/54/56–59.

### PRD-08 Cross-product Exchanges and Price Differences

Corresponding Spec FR-08, BR-12/14/15/22/26/29, §7.4 and ERD-C03–09; E. The existing 30-day, opened/quality eligibility and on-shelf stock checks remain unchanged; no store-manager eligibility exception is set. The exchange reason is selected as "quality/other", and "other" requires reason text, with the text not changing eligibility. An exchange first checks the original line's remaining quantity/amount, computes by the original actual paid P/Q and occupies this offset; the new product's current price minus this offset yields a positive top-up, a negative refund or a zero exchange, and an identical-item, identical-quantity quality exchange with zero difference still occupies the original line's quota. Replacements deduct on-shelf only, and items handed back are not returned to the warehouse but recorded as loss/disposition; a return uses only the post-exchange remaining quantity/amount, and repeated requests are not redeemed twice.

Traceability: Q09/R-AFTERSALE-LEDGER; AC-14/18–20/34/56–59.

### PRD-09 Record Permissions, Deletion and Authorized Voiding

Corresponding Spec FR-09, BR-16/21; E. The store manager/administrator views all documents and a store clerk only their own; after-sales operator/time/original sale/product quantity/reason/result/disposition/status are complete. Drafts are generally deletable; an incomplete after-sales draft that has been successfully saved and is read for traceability by IF-11 is cancelled into CANCELLED with the three-way link retained, cancellation produces no refund/stock/quota change, and completed after-sales does not apply (CR07-11); confirmed documents are not physically deleted; store-manager or administrator authorized voiding retains the reason → reverse ledger entries → refund/stock synchronized reversal. Status-only changes are not allowed, and negative stock is not allowed; stock dependency follows Spec §7.5 D1: a valid negative ledger entry for the same product, the same location and a larger commit_ordinal; a newly added receipt neither directly blocks nor clears existing negative dependencies, and equal ordinals are not subsequent to each other. Only fully authorized reversals that are paired-reverse and have no after-sales dependency of their own exclude the entire downstream business group; failure of any line of the original document rejects all, with no partial voiding.

Traceability: Spec §13 Q10; formal scenarios AC-10/28/35. The business specification has been incorporated into the baseline, and the whole item is not re-questioned merely because these entries still link to detailed issues.

### PRD-10 Local JSON and Offline Recovery

Corresponding Spec FR-10, BR-17/24, TC-04/05/07–09; E. Maven/JDK17/JUnit4.13.2, Jackson UTF-8 JSON single snapshot/schemaVersion. Startup validation → business snapshot temporary write + atomic replacement/keep the previous version → restart recovery; corruption blocks writes and prompts recovery, and recovery checks referential and original actual-paid integrity. Storage ordering follows Spec §10.2 D2: root last_commit_ordinal, ledger entries require commit_ordinal, the same number within one commit, a single writer, and the counter and business/ledger taking effect atomically in the same snapshot; on failure last does not advance and nothing is partially visible, restart/full-backup recovery validates last ≥ the maximum ledger sequence number, and a successful commit with no stock also increments it. Under lock contention another process is read-only or write-refused; recovery blocks writes when attachments/references are missing or hashes/sequence numbers are abnormal. No COMMIT entity or separate counter file is added. Core business is offline, with attachments linked by relative path/hash; hardware has only a future interface boundary, with no implemented device or gateway.

Traceability: Spec §13 Q01/Q11/Q12; formal scenarios AC-02/03/23/36. The business specification has been incorporated into the baseline, and the whole item is not re-questioned merely because these entries still link to detailed issues.

### PRD-11 CUI Navigation, Error Feedback and Public English/Chinese

Corresponding Spec FR-11, BR-18, TC-01; E. All users enter/return/exit in the CUI following the prompts; menu or numeric errors give clear feedback and are retryable, each step can be exited and no unhandled exception occurs. P1 continues with the CUI, the JavaFX template is a placeholder only, and no GUI upload page is developed. Draft deletion follows Q10, the concrete navigation layout is left to later design, and the CUI decision is not reopened.

Traceability: Spec §13 B; formal scenario AC-05. The business specification has been incorporated into the baseline, and the whole item is not re-questioned merely because these entries still link to detailed issues.

Language flow (explicit user decision, Spec §17 CR07-12): English at startup when no language is specified, with planned support for `--lang en`/`--lang zh`; the login screen and the post-login menu can switch, logging out retains this run's choice, and a restart follows the parameter or defaults to English. Language is not persisted and does not change the current unsubmitted input/identity/business facts; preservation of user data in its original language, missing-text fallback and business-result independence are only referenced from Spec §10.3. The body of AC-91–100 is maintained only in Spec §11, and its expectations are settled but not executed; the language requirement is approved and not yet implemented. This paragraph does not change other PRD business processes/permissions/calculations.

### PRD-12 Warehouse-to-shelf Replenishment

Corresponding Spec FR-12, BR-06/20, §7.1; E. With sufficient integer quantity in-warehouse, select the product and quantity → in-warehouse −q, on-shelf +q → link the two-way ledger entries of the same commit. The total is unchanged and not double-counted; an insufficient source is rejected. No batch is selected, and authorization follows R-AUTH.

Traceability: Spec §13 Q05/Q06; Q02 permissions linked to R-AUTH; formal scenarios AC-12/21. The business specification has been incorporated into the baseline, and the whole item is not re-questioned merely because these entries still link to detailed issues.

### PRD-13 Loss, Loss Registration and Actual Disposition

Corresponding Spec FR-13, BR-14/25, §7.1; E. The store manager registers the loss and the administrator may correct it, with no tier-by-tier approval, recording operator, time and reason. Existing stock is deducted once at the source location; an item handed back by a customer is not returned to the warehouse and does not deduct existing goods again; later disposition does not deduct again, and there is no third bucket. A correction loses no links and produces no negative stock; a correction retains the original record plus the corrected version, the difference, and operator/time/reason.

Traceability: Spec §13 Q05 (including C); the settled part of Q02 permissions; formal scenarios AC-15/18/37. The business specification has been incorporated into the baseline, and the whole item is not re-questioned merely because these entries still link to detailed issues.

### PRD-14 Dates, Reminders and Batches (Withdrawn)

Withdrawn: product dates, near-expiry/expiry, batches and expired-sale blocking are removed entirely from P1 and P2; no extension point is retained. The numbering serves historical traceability only and is not a functional or acceptance requirement. Corresponding to the original suffixes of Spec FR-14, BR-23, §7.2, ERD-C10. The original PRD-14-DATE/REMIND/BATCH numbers serve only as withdrawal mapping; there is no product flow, role authorization, P2 reservation or effective acceptance.

### PRD-15 CUI Invoice Image Support

Corresponding Spec FR-15, BR-19/27, §7.1; E. The system must support image attachments (E), but they are not mandatory per document and are judged by the store manager as appropriate. Link the receipt record → at the CUI enter a local path to import or skip → if an image exists, copy it to DATA_ROOT/attachments/ and store the relative path/hash. Review remains possible without an image, and there is no GUI upload page; it is not a sales refund voucher. Formats are limited to JPG/PNG, ≤5,000,000 bytes per image and ≤3 per document; independent IDs are used for naming, content is reviewed manually, and the system only stores and performs technical validity checks, with no OCR/automatic voucher verification/content review, and the support capability/non-mandatory nature/priority are not reopened.

Traceability: Spec §13 Q04 (including A)/Q11/ATTACH-PRIORITY; Q02 permissions linked to R-AUTH; formal scenarios AC-11/40. The business specification has been incorporated into the baseline, and the whole item is not re-questioned merely because these entries still link to detailed issues.

### 5.1 Functional Traceability Matrix

K denotes the following course expression/implementation obligations and does not mean the course specifies supermarket business item by item. Question IDs reference only Spec §13; a parent Q does not serve as a duplicate pending-review list.

| PRD | Spec | Course source (if applicable) | AC/single question reference |
|---|---|---|---|
| PRD-01 | FR-01, BR-04/21, §5 | K1 general, C p3; no course clause specific to this supermarket | AC-09/10/28; Q02 |
| PRD-02 | FR-02, BR-05/06, §9 | K1 general, C p3; no course clause specific to this supermarket | AC-21/29; Q03 |
| PRD-03 | FR-03, BR-03/07/19, §7.1 | K1 general, C p3; no course clause specific to this supermarket | AC-04/11/24/26; Q04 (including A); Q02 |
| PRD-04 | FR-04, BR-06/08/20, §7.1 | K1 general, C p3; no course clause specific to this supermarket | AC-12/13/22/30; Q05; Q02 permissions linked to R-AUTH |
| PRD-05 | FR-05, BR-03/07/10/24 | K1 general, C p3; no course clause specific to this supermarket | AC-04/13/27/31; Q01/Q06; Q02 permissions linked to R-AUTH |
| PRD-06 | FR-06, BR-09/11, §7.3 | K1 general, C p3; no course clause specific to this supermarket | AC-16/32; Q07/Q12 |
| PRD-07 | FR-07, BR-12/13/14/21/26, §7.4 | K1 general, C p3; no course clause specific to this supermarket | AC-09/15/17/33/41; Q08 |
| PRD-08 | FR-08, BR-12/14/15/22/26, §7.4 | K1 general, C p3; no course clause specific to this supermarket | AC-14/18–20/34; Q09 |
| PRD-09 | FR-09, BR-16/21 | K1 general, C p3; no course clause specific to this supermarket | AC-10/28/35; Q10 |
| PRD-10 | FR-10, BR-17/24, TC-04/05/07–09 | K4, C p4–5/p13 | AC-02/03/23/36; Q01/Q11/Q12 |
| PRD-11 | FR-11, BR-18, TC-01 | K3/K5, C p4–5 | AC-05; B |
| PRD-12 | FR-12, BR-06/20, §7.1 | K1 general, C p3; no course clause specific to this supermarket | AC-12/21; Q05/Q06; Q02 permissions linked to R-AUTH |
| PRD-13 | FR-13, BR-14/25, §7.1 | K1 general, C p3; no course clause specific to this supermarket | AC-15/18/37; Q05 (including C); settled part of Q02 permissions |
| PRD-15 | FR-15, BR-19/27, §7.1 | K1 general, C p3; no course clause specific to this supermarket | AC-11/40; Q04 (including A)/Q11/ATTACH-PRIORITY; Q02 permissions linked to R-AUTH |

### 5.2 v0.7 Rule Synchronization and Supplementary Acceptance Traceability

Spec v0.7 §7.3/7.4 is the single algorithm definition, and §9.1 holds the subsequent ERD constraints; approval and pending status are found only in Spec §17.

| PRD | Spec | ERD constraint | Acceptance |
|---|---|---|---|
| PRD-05/06 | FR-05/06; BR-09/10/28; §7.3 | ERD-C01/02/09 | AC-31/41/44/54/55 |
| PRD-07/08 | FR-07/08; BR-12/13/15/29; §7.4 | ERD-C03–07/09 | AC-46/54/56–58 |
| PRD-09/10 | FR-09/10; BR-07/17/29; R-VOID | ERD-C07–09 | AC-35/36/52/57/59 |
| PRD-14 original numbering withdrawn | FR-14/BR-23; U6; §7.2 | ERD-C10; retained in neither P1 nor P2 | AC-42/43 are scope checks, the remaining old scenarios are withdrawn |

### 5.3 Runtime Path Boundary Reference

Attachment paths are relative to the explicit runtime data logical root DATA_ROOT, not the repository/Markdown directory. The source image import path is separate from the persisted `attachments/<filename>`; DATA_ROOT is set to the repository root data/, attachments are named with independent IDs and each image is ≤5,000,000 bytes; the remaining root resolution/sub-layout and the general failure flow reference only [Spec §10.1](./Project_Spec.md#101-runtime-path-contract-and-pending-design) PATH-D01/02, no separate question table is built, and review without an image is not changed; these pending design items do not block the ERD relationship review.

## 6 Main Business Flows and Quantity Effects

1. The receipt is registered as pending review and is not counted in either location; once the store manager approves, it enters in-warehouse in one step, and review is still possible without an image; no product date is entered and no batch is selected.
2. Replenishment moves in-warehouse −q/on-shelf +q, the total is unchanged, and only product and location quantities are validated; insufficient is rejected.
3. A sale selects products/quantities → identical product at identical price is merged → the candidate document picks the best promotion → the discount is allocated by the applicable line's original amount ratio and the discount/actual-paid snapshot is saved → confirming payment and the on-shelf deduction take effect atomically for the whole document.
4. A return links the original line → verify the period/eligibility and the shared remaining quantity/amount → refund per the original line's P×q/Q and the final-payment rule → save the redemption and loss disposition; items handed back are not returned to the warehouse.
5. An exchange links the original line → first occupies quantity/actual-paid quota → compute the top-up/refund difference between the new product and the offset (an identical-item, identical-quantity quality exchange with 0 difference still occupies quota) → deduct the replacement's on-shelf → record the handed-back disposition; subsequent refunds use only the remaining quota.
6. Loss registration and authorized voiding follow the established permissions; a reversal links the original redemption record and releases the quantity/amount in reverse, retains history, is idempotent and does not go negative on stock. Query permissions are unchanged.

Neither P1 nor P2 has product date, batch or expiry-handling steps. Operator time, the promotion validity period and the 30-day after-sales period are retained.

## 7 Acceptance Criteria and Execution Status

The only formal scenario body and rule status are in Spec §11, and approval status is in Spec §17; this PRD only traces FR/PRD→AC and does not maintain a parallel acceptance table. The AC mirror previously in this section was checked item by item against Spec and found identical at 73 entries, and the Chinese original has been saved; removing the duplicate body deletes/modifies none of its normative rules and expectations.

The existing AC-01–59, AC-77–90 and the withdrawn/technical-assertion statuses all reference Spec §11 directly, and a historical Ready is not equated with an actual test pass. The new language scenarios AC-91–100 link PRD-11, FR-11/NF-07 and CR07-12: the requirement is approved, the language feature is not yet implemented, and the tests are not executed. Other tests actually executed remain with their original evidence and are not retested or rewritten in this round.

## 8 Report Evidence Collection Plan

Per T's full template and S §15. The template has 13 table/figure containers: cover, §2.2 requirements, §2.6 declaration, §3.1/3.3/3.5 figures, §3.4 review, §3.6 evolution, §3.7 declaration, A.1/A.2/A.3, B. The jump from 2.2 to 2.6 is the template’s actual numbering; 2.3–2.5 are not invented.

| Template location and mandatory information | Collection stage and materials | Reused Spec record location/responsibility |
|---|---|---|
| Cover Project title, Canvas Group ID, Student1/2 name and student ID | Check the real identity during requirements review and before submission | Filled in by the student; unconfirmed group number/identity is not guessed |
| How to use, Table of contents | During report assembly delete the blue instruction boxes/grey placeholders/example rows and keep the numbering; where not applicable write Not applicable plus a one-line reason; update the TOC | `docs/evidence/report_checklist.md`; check the final Word/PDF version |
| §1 Project Setup / How to build and run | After development is approved, when actually implemented, record Maven/JDK17/JUnit4.13.2, tools, main class, the real build and run commands and applicable credentials; verify a clean directory and offline running | README, `docs/evidence/build_run.md`; do not copy unverified example commands |
| §2.1 topic and scope justification | From now on record the served user, scope, sizing basis and exclusions | Spec, PRD version, `docs/decisions.md`; decisions and not-executed status stay consistent |
| §2.2 table ID / Requirement / Description-acceptance criteria / Priority | At requirements confirmation and on every change, register PRD→FR→AC and the confirmation/conditional status; the original FR-14 capability is withdrawn per U6, and E/D is not inferred from whether an attachment is mandatory per document | Spec numbering is the baseline; this PRD maps it; do not write PD results as passed |
| §2.6.1 necessary requirements and why; 2.6.2 excluded/unrealistic features and why; 2.6.3 trade-off rationale | The student personally records observations and genuinely rejected suggestions at the time of the requirements discussion, and writes them up independently after the requirements phase ends | Student's personal notes; AI does not generate reflections or fabricate decisions |
| §2.6 declaration table Student name / Student ID / Signature / Date | Signed by the student after the independent reflection is complete | Filled in by the person; no signing on their behalf |
| §3.1 Figure1 Use case diagram; §3.2 scope constraints and assumptions | Initial design after requirements approval, with later changes synchronized to the boundary and approved roles | `docs/design/use_cases.*` and readable PNG/JPG; Spec constraints and decisions |
| §3.3 Figure2 Class diagram Iteration1 and initial description | Immediately preserve the original when the first actual design is completed | `docs/design/v1/`; classes/attributes/methods/relationships, time, rationale for responsibilities |
| §3.4 table # / Prompt summary / GenAI suggestion / Accepted-Rejected / Reason | At the time of the real AI review after V1, record the prompt, the product, the human acceptance/rejection and the reason | `docs/evidence/design_reviews.md`, the original session reference; do not manufacture AI errors |
| §3.5 Figure3 Class diagram Iteration2 final and structural description | V2 is formed after review revisions; when the implementation structure changes again, synchronize the final version | `docs/design/v2/` and later versions; interfaces/abstract classes/concrete classes and relationships correspond to the implementation |
| §3.6 table Change / Iteration1 / Iteration2 / Reason | Record the before/after difference and the reason at the time of each substantive design change | `docs/evidence/design_evolution.md`; do not substitute color changes for iteration |
| §3.7.1 AI suggestions; 3.7.2 errors/limitations; 3.7.3 improvements; 3.7.4 reasons for the final design's applicability | Collect facts when design reviews/implementation problems surface, and the student later independently analyses the specific classes/relationships/improvements | Student's personal notes linked to real design evidence; project conclusions must not be pre-written now |
| §3.7 declaration body and table Student name / Student ID | Declared by the person after the independent reflection is complete; the body requires a signature while the table lacks a signature date, so a photo of the person's signature is prepared per the user's recommendation; submit Word/PDF with the single ZIP, referencing Spec §13 Q14-FORM | Keep the original section and declaration, do not change the template structure, and handle it in the teacher's specific form |
| A.1 table Tool / Model-version / Mainly used for | From this collaboration onward record the actual tools and visible models, writing not specified for unknowns | `docs/evidence/ai_usage_log.md`; do not treat the example GPT-5 as fact |
| A.2 table # / Stage / Tool / Main prompt / What GenAI produced / What you changed-rejected and why | At the time of each key interaction in requirements, design, implementation and debugging, record a representative prompt and the human's reason for the change | Same as above plus the original records; in this round truthfully record the unified Spec/PRD convergence after U3 authorization, and do not record business implementation as complete |
| A.3 table Part / Mainly human-designed / Mainly AI-generated then reviewed / Notes | Check the real human-machine contribution at each stage | Student confirms; AI drafting is not attributed to human originality |
| B table Member name / Student ID / Main contributions / Contribution% | Retain the real design/code/test/report/video contributions at each stage, and finally confirm a total of 100%; for a single person write Individual project per the template and delete the table | `docs/evidence/contributions.md`; 50/50 must not be pre-filled |
| Implementation and test evidence (the template has no separate mandatory test chapter) | After development is later approved, collect builds, before/after business balances/actual paid/ledger entries, inputs and outputs; when executing AC with rule status Ready, record environment, version, expected/actual, defects and retest | `docs/evidence/acceptance_results.md`, `docs/evidence/screenshots/`, `docs/evidence/console/`; there are currently no results, and screenshots are not fabricated |
| ≤5-minute video and submission check (course deliverable) | After implementation is verified, genuinely demonstrate AI use, functionality and structure; before submission check the single ZIP/naming/source code/report/video and backup | `docs/evidence/demo_plan.md`, `docs/evidence/submission_checklist.md`; C p4/p6/p12–13 |

The log/evidence paths above are the planned locations in S §15, and this round creates no empty evidence pretending completion. Facts are recorded continuously at each stage, and existing evidence is organized at the report assembly stage. Focus on retaining receipt without image/self-review, replenishment quantity conservation, negative-stock rejection, clerk returns and exchanges, back-office visibility, non-stacking candidates, refund of the original actual paid, and defective-item disposition; historical boundary traceability references Spec §13; current approval status is found only in Spec §17, and approved rules may have settled expectations; the rule status of D1/D2-related scenarios is in Spec §17 and this table, and none is executed; actual test results are recorded only later.

Report arrangements follow S §15.4: during the working stage collaborate on the English version in the repository (the corresponding Chinese version is kept in a designated directory outside the repository); the English final report is produced before submission, the Chinese working draft is not submitted, and the English final report uses the English report location under the repository root; report arrangements are unchanged; historical versions are recorded by Git, and no report copies are maintained outside the repository. The final file name is determined at the assembly stage, and the platform specification is referenced only from Spec §13 Q13; this round generates no report. The working draft and the English final report keep section/FR/BR/AC/Q/figure-number, fact and status correspondence and use the same real evidence index (planned `docs/evidence/report_evidence_index.md`). The formal reflections and declarations of §2.6 and §3.7 are completed independently by the student only in the English final report; the Chinese working draft may retain source material but does not replace the formal declaration; submission reflection text is not ghost-written or machine-translated by AI (S §15.4); language-tool licensing questions are verified with the teacher by a human. Document history is recorded by Git, and this document defines no backup location outside the repository and is not mixed into `docs` (S §1 change record).

## 9 Decision Sources and Historical Migration

Previously, U3 §II authorized incorporating the user's earlier single suggestion into the Spec. The old PRO-Q01–14, PRO-A/B/C and attachment-priority candidates no longer serve as text pending confirmation; the complete history is retained by Git; the original Q01–Q14 and the A/B/C labels have all been located, with no speculative mapping. A has been assigned to Spec Q04, C to Q05, and B is an independent CUI decision. Voiding of ordinary authorization is now explicitly part of P1, and content that originally overlapped with P2 candidates is withdrawn from future items under the new authorization.

Spec §13 retains the historical mapping, and §17 is the main register of changes/open items for this round. This draft does not register again the options whose "answers are to be written into the Spec"; the decision to revoke FR-14 and other closed decisions reference only the original entries, the basis for attachment support E is in Spec §4/ATTACH-PRIORITY, and whether support is required/whether every order must attach it is no longer asked repeatedly. P2 scoring references only Spec P2-SCORE, which has been verified as consistent and is not treated as a pending item.

## 10 Single Reference for Decision Status

Spec §13 retains the original issue mapping, §17 is the sole change/open-item register for this round, and the original Q1–Q14 and A/B/C mappings are retained. The original 12 business sub-questions of U4 keep their confirmed mapping, and this round's status follows Spec §17; this draft establishes no separate pending options.

Product data sampling, Canvas group number/identity/platform time zone/language version and actual signing completeness are converted into execution or submission checks, and must not be claimed as verified. The user confirmed delivery before the end of the 23rd, preferably on the 22nd; the current plan is to submit on 2026-10-22 and complete before the end of 2026-10-23; the course still takes Canvas as authoritative. The signed photo of the student is prepared at report assembly as recommended by the user; the PDF only requires the Word/PDF report to be packed with the source code and video into a single ZIP, and does not prescribe the form of the photo signature.

## 11 Preliminary Work Distribution and Diagram Plan

### 11.1 Responsibilities and Handoffs

No implementation is performed in this stage; specific personnel, names, headcount and contribution ratios are assigned by the real team at implementation time and are not fabricated. A responsibility is a work scope, and one person may hold several; when the course team is formed, each member should participate significantly in design and implementation (C p2).

| Work scope (personnel unassigned) | Modules/materials | Dependencies | Handoff artifacts |
|---|---|---|---|
| Requirements/decisions and design | FR/BR, the Spec single status table, use cases/V1/review/V2 | The current v0.7 is frozen as the requirements baseline; the basis and approval status of D1/D2 are looked up only in Spec §17 CR07-06/07 | Adopted version/settled rules, actual diagram versions/decisions, closed IDs; answered Qs are not re-asked |
| Receiving/attachments/location (original division record, historical only) | FR-03/04/12/15, FR-14 revoked | No development division of labour in this round; the original record only synchronizes the removal of the revoked scope | No new or subdivided tasks |
| Sales/promotion | FR-05/06 | Settled on-shelf/atomic/snapshot; the exact stage references only M-ROUND etc. | Original sales line/actual paid snapshot, candidate pricing, stock impact; handed to after-sales/testing |
| Returns/exchanges/loss/query/voiding | FR-07/08/09/13 | Original sales, settled 30 days/difference/permissions; settled boundaries reference the original Spec IDs | Shared redemption, refund/return destination, reverse linkage; handed to stock/integration/testing |
| Common CUI/persistence/integration | FR-10/11 | Maven/JDK17/JUnit4.13.2/Jackson baseline, domain design | Genuinely runnable build commands, snapshot/attachment association and recovery, cross-module consistency; handed to all modules/testing |
| Acceptance/defects | Ready ACs and the boundary scenarios added in this round | Actual implementation version and data; no code in this round | Actual expected/actual, output, defect retest; no fabricated passes |
| Report/log/video | All T templates, Spec §15, §8 of this PRD | Real evidence for each stage, the student's own reflection/identity/contribution | The Chinese version (outside the repository) provides source material; before submission the English final report keeps the same facts; a genuine demonstration of ≤5 minutes, submission package check; reflection is not written by AI |

The handoff records versions, module dependencies, real verification results and Spec decision IDs; settled business rules must not be miswritten as completed implementation, nor may one residual boundary be turned into the whole feature being still unanswered.

### 11.2 Diagram Plan and Course Requirements

P1 plans 1 use case diagram (T §3.1 Figure1) plus 2 class diagrams (§3.3 Figure2 initial version, §3.5 Figure3 final); C p3 requires at least two genuine design iterations, AI critique and revision, with T §3.4/3.6 recording the reviews/evolution. Readable PNG/JPG files and diagram sources are kept; quantity does not replace substantive change.

The user's preference for generating code first and then generating diagrams from the code is retained; however, C p4 §1.3 explicitly requires implementation based on the final design, so the preference can only be realized within the scope that does not omit the preceding design/genuine iterations: requirements and V1 design recorded → genuine AI review/manual decision → V2 design → implementation after approval → class diagrams aided-generated from the actual code and checked for synchronization against the Spec/final design. All implementation must not be completed first and then two versions of history fabricated; this is an existing course boundary, referencing Spec PLAN-ORDER, and the user is no longer asked to answer in general terms or assumed by default to need a teacher exemption.

Class diagrams may be generated from real code and then checked for attributes/methods/relationships and semantics; use case diagrams are based on confirmed actors/requirements/system boundaries, and it is not claimed that menus or code can fully reconstruct user goals, permissions and use cases. At present only the plan is recorded; no code or diagrams are generated.

### 11.3 Synchronization and Delivery Checks

At present only the Spec/PRD/ERD documents are updated, historical versions are recorded by Git, and there is no code, diagram, final report or business test. Course materials/templates are read-only; the scoring items have been verified as consistent, and the original scoring names/categories/weights are unchanged. Settled business rules and tools are synchronized into the body/matrix/ACs, and no PRO candidates remain; the Spec is the single status table, and the PRD only references it. All 12 business sub-questions have been closed per U4; actual product data and platform evidence are still unverified and are truthfully retained as subsequent execution checks.


## 12 Changes and ERD Constraint References

The scope of this round and the reasons for AC revocation/rewriting are uniformly in Spec §16, and the PRD does not establish another issue table. The subsequent ERD follows Spec §9.1 ERD-C01–10: sales line promotion allocation/actual paid snapshot, original line 1:N after-sales redemption, dual caps on quantity and amount, idempotent auditing and reversal, stock by product plus location only. The original product date/expiry/batch capabilities have been fully revoked in both P1 and P2, with no ERD drawn and no task subdivision. Report materials continuously record this round's decisions and real evidence per T §2.1/2.2/3.2/3.4–3.6 and Appendix A, and the reflection is still written independently by the student; no implementation/test evidence is arranged for the revoked capabilities.

Report material additions for this round: the requirements stage preserves the original U5/U6 decisions, the user's reasons for the scope reduction and the version differences; the design stage records the sales line allocation snapshot and the dual-quota ledger/idempotency/reversal constraints (Spec §9.1); only after subsequent approved development and testing are the real inputs and outputs, balances, defects and retests of AC-54–59 collected. Mapping T §2.1/2.2, §3.2/3.4–3.6 and Appendix A; implementation or test evidence for product date/batch/expiry capabilities is no longer arranged. The reflection and signature are still completed by the student, and no final report or project conclusion is produced at this stage.

## 13 v0.7 Controlled Synchronization Notes

This version is rebuilt only from the requirements confirmed at HEAD plus the explicit authorization in Spec §17; the single register of versions, change requests and open items is [Spec §17](./Project_Spec.md#17-v07-change-control-and-single-pending-decision-register), and no separate Qs are created. After-sales eligibility per redemption and exchange_kind per replacement detail, with explicit linkage of same-quality zero-difference pairings, see CR07-01/AC-82; same-category splitting follows the original line's actual paid formula and the stable redemption ID, and the current submission settlement semantics see CR07-03/AC-79/80; same-order exchange priority is a new business rule CR07-04, see AC-81, and is not applied retroactively. Whole-order reversal takes no effect if any line fails, CR07-05/AC-83; D1/D2 specifications see Spec §7.5/10.2, and the sole approval see Spec §17 CR07-06/07; CR07-10 revokes the CR07-09 store-manager eligibility exception, and other reasons only record reason_text without changing eligibility; it is frozen as the requirements baseline, and formal division of labour/development does not start. AC-60–76 are placeholder numbers in an unapproved draft, not formal standards. Report collection per §8 and Spec §15/17 preserves real authorization, differences, design reviews and actual subsequent results; tests or student reflections are not pre-filled. Product date/batch capabilities are excluded in both P1 and P2 and are not reserved.

The M1–M5 consistency revisions and D1/D2 impact records of this round see Spec §17.3. AC-35/52/59/83–85 are synchronized verbatim per Spec §11, and Ready does not mean executed; after implementation is approved, real evidence of lock contention, snapshot failure/restart/complete recovery will be collected, mapped to report T §1/2.2/3.2/3.4/3.6 and Appendix A, and the reflection is completed independently by the student.

The controlled synchronization record references Spec §17 SYNC-01–05 and CR07-11: the two items read admission and retention cancellation of read drafts are approved; AC-87 updates references and AC-88–90 are added, expected Ready but not executed. The v0.7 version designation is unchanged, and approval is not a substitute for interface implementation, centralized auditing, real integration testing or acceptance.

Controlled revision CR07-12 (2026-10-22, v0.7 unchanged): the language process see PRD-11, the specification see Spec §10.3, and the sole acceptance AC-91–100 see Spec §11; the requirements are approved, not yet implemented/verified. That affected repository files are in English and the corresponding Chinese is stored outside the repository is a user management convention, not a course requirement; substantive source-code comments are bilingual Chinese-English, and the source code is not translated in this round.
