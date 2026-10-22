# P1 Unified Implementation and Local Delivery Log

The historical v3 delivery on 2026-10-21 removed the A/B development boundary under user authorization and completed one monolith. It did not change Spec/PRD/ERD v0.7, shared v1.0.4 or CR07-10/11. Functional delivery was `4a67050`; reproducible verification and the additional cross-document reversal reference check were `08df45a`; delivery documentation was `4328a11`. These were local deliveries, not a new GitHub release. The language implementation of 2026-10-22 is recorded separately in §7; the behavior-preserving validator refactor is recorded in §8.

## 1. Historical v3 starting point and WIP protection

The four initial checks produced the following actual output (INTEGRATED was derived from LASTEXITCODE):

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

That base already contained B's `6408320`; no duplicate fetch/merge was performed. Another session had switched the original Project1 branch, so implementation continued in the sibling Project1-member-a worktree on member-a/p1-delivery. At the time of the v3 record, the original tree remained on member-a/p1-complete / 6408320 with its early WIP preserved, without reset, overwriting or cleanup. The specified-root delivery was established later in §6.

The default shell had selected Java 8. The actual v3 build used the already available Microsoft OpenJDK 17.0.20.1+1, Maven Wrapper 3.9.11 and PowerShell 7.6.5. No second Maven skeleton was created and no historical JDK17 result was invalidated. README uses environment-relative discovery rather than personal absolute JDK paths.

## 2. Delivered implementation

Paths below are relative to `src/main/java/nz/ac/aut/pdc/`.

| Module | Code and delivered behavior |
|---|---|
| Startup/assembly | supermarket/app/Main, Application, RepositoryBIntegration: real storage, authentication, stock, quota, aftersale, queries, reversal and CUI. BApplication delegates to the same assembly. The dependency JAR includes Jackson; --check and first-account initialization do not seed business goods or default passwords. |
| Product/opening/receipt/sale | supermarket/app/AOperations, catalog/Product, promotion/Promotion, sales/Pricing: unified submissions; first approved receipt W+, correction of unposted lines, stable sale lines, zero prices, single-activity selection and discount allocation. |
| Attachments | infrastructure/AttachmentStore, DataRoot: JPG/PNG, 5,000,000 bytes, three per receipt, independent IDs, relative references/hash and safe roots. Missing attachments do not make review mandatory. |
| Stock/loss | stock/StockBusiness, SharedStockAdapter, StockValidator, LossWorkflow: rebuild original intent and compare complete effects, exact integer parsing, roles and prefix commit-group recovery; deduct store loss once, never on disposal; retain original quantity and append administrator corrections with before/after facts. |
| Aftersale/quota | aftersale/AftersaleEngine, AftersaleService, AftersaleCommitValidator: same-algorithm revalidation, stable ascending redemption IDs, exchange before return, cumulative dual quotas, window/opened/reason rules and quality pairing; one net money entry with stock, redemption and returned-item loss facts atomically. |
| IF-11 | sales/OriginalSaleAccess and aftersale/AftersaleDraftAccess: trusted identity, exact credential, single admission, atomic target/trace creation and trace-before-return; fresh working copy after success, original-key lookup after unknown outcome, legacy read-event compatibility without rewriting history. |
| Draft lifecycle | aftersale/AftersaleDraftService/Validator/DeletionValidator; infrastructure/DraftDeletionGuard: traced drafts cancel to CANCELLED without money/stock/quota effects; eligible unread drafts delete as a whole group atomically, with lookup before existence checks and replayable deletion. |
| Whole-document reversal/D1 | reversal/EffectiveDependencies, ReversalCommitValidator, ReversalChangeSetAssembler: rebuild all inverse effects, reject omissions/extras/cross-document references; MANAGER/ADMIN permitted, CLERK rejected; supports APPROVED receipts and excludes fully reversed dependency groups. |
| CUI/queries | cui/CuiApp, SharedOperations, MaintenanceOperations; query/QueryService: complete menus, quote-before-confirmation, mixed aftersale, role-scoped history and original-key retry. No WorkingCopy or credential hashes are exposed to the CUI. |
| Shared/persistence | shared/, infrastructure/JsonStore, SubmissionTime: sole write entry, original request/identity/key/fingerprint/version/full-delta checks, OS writer lock, ordinal, temporary write/atomic replacement/complete previous snapshot, recovery write blocking and no nested submit. |

The historical single-redemption ReversalService and SubmissionGateway prototype payloads live only in test sources. They do not ship in the production JAR. B branches, historical commits and V1/V2 designs were retained; there is one authentication, saver, stock and quota implementation. LocalAuthService/Sha256PasswordHasher were reused with the real credential view.

## 3. Contracts and compatibility

Shared specification §11 records exact IF-11 request/result/event fields, legacy detail compatibility, roles and actual stable error codes. No IF number was added. SubmissionPort.CommitValidator retains its v1.0.4 six-argument signature. WorkingCopy remains an internal immutable handle.

IF-05 obtains acceptedAt once under its lock after idempotency/version checks. New DOCUMENT.createdAt, DOC_EVENT.occurredAt, RECEIPT_REVIEW.reviewedAt and current confirmedAt use that business time; existing creation/original-sale times remain unchanged. Promotions are checked at that same instant. Candidates are compared exactly after the managed-field matrix is applied; historical/user times are not ignored. Replay never re-times records and recovery has no activity-date workaround.

DraftDeletion(documentId) uses expectedVersion=0 and per-Removal versions; the older two-argument request remains supported with its additional target-version check. A validates the complete business-approved deletion group, versions and reference closure before saving atomically. It is not arbitrary table deletion.

IF-11 returns VALUE AftersaleReadAccess with a trace CommitReceipt, not a completed aftersale. Event IDs derive stably from read keys and record the actual returned line IDs. Legacy target-READ and originalSaleLineId/returnedLineId details are parsed without rewriting. Subsequent business opens a new baseline and reads quota/stock from it. Admission and retention remain CR07-10/11; no additional rule decision was introduced.

## 4. Historical v3 verification (2026-10-21)

| Actual command/run | Historical result |
|---|---|
| Opening v3 JDK17 `./mvnw.cmd -B -q clean verify` | In-flight baseline: 184 tests, zero failures/errors/skips. |
| Final `./mvnw.cmd -q clean verify` | 200 tests, zero failures/errors/skips; dependency JAR produced. |
| `./scripts/verify-p1.ps1 -Offline` | Offline 200 tests, zero failures/errors/skips; package isolation, JAR --help/--check and real CUI passed, exit 0, three legal business commits. |
| Packaged CUI | Random fictional three-role credentials; real login, product creation, legal opening quantity 5, sale quantity 1 and history. One product, one sale, two stock entries; password not output; isolated root only. |
| Terminal JAR --init-accounts --check | Exit 0; three fictional role accounts, zero business records, ordinal 0; real console and services, no dependency doubles. |
| JAR inventory | Main/ObjectMapper present; no FictionalDependencies, CrashAfterCommitProbe or old ReversalService/SubmissionGateway prototypes. |
| git diff --check/staging review | Passed; no data/, target/, passwords/hashes, course originals or attachments included. |

An initial unquoted -Dfile.encoding=UTF-8 invocation failed PowerShell parsing; the quoted command was actually rerun successfully. Development compile/fixture failures were corrected and never counted as passes. Counts above came from final Surefire XML and script receipts.

Real-chain evidence: FullRuntimeTest covers real receiving, approval, replenishment, sale, cross-person aftersale, rejected dependency reversal with unchanged snapshot, then reversal of aftersale/sale/replenishment/APPROVED receipt with zero remaining balances. Other cases cover stable-ID rounding, mixed net settlement, quality zero-difference exchange after price changes, disposal/administrator corrections and CUI refund/reversal replay across login/restart.

Failure/recovery evidence includes pre-replacement IOException, no IF-11 data returned on save failure, all-or-nothing draft deletion, original-key lookup/retry, actual child-JVM halt after a durable trace, another JVM's writer-lock refusal, corrupt-startup blocking and legacy events not rewritten. SubmissionTimeTest covers midnight/idempotent times; StoreTest, AttachmentTest and DraftDeletionTest cover structures, attachments and complete previous-snapshot recovery. Evidence for AC-02/85/87/88/90 is not formal acceptance of every AC.

Double boundaries: FictionalDependencies, DraftDouble, old reversal plans and capturing ports are module fixtures, not real integration. FullRuntimeTest uses real business dependencies; file faults/lost responses use explicit hooks/wrappers; legacy-event fixtures and the abrupt-crash probe are test-only. These do not certify power-loss or cross-platform filesystem behavior.

## 5. Historical delivery limitations

The v3 functionality and P0/P1 corrections were delivered without required placeholders waiting for B. The historical conclusion was full P1 development delivered, awaiting user acceptance, not completion of the later language feature. Linux/macOS, formal course acceptance, student report/video/ZIP and signatures were not executed or produced. No reflection/signature was ghostwritten. Automated results do not replace manual course deliverables. Upload/push/main merge requires review of the exact diff followed by explicit approval for that operation.

## 6. Release engineering cleanup (2026-10-22, before language work)

Initial actual trees: Project1 on member-a/p1-complete / 6408320, empty index, three tracked and eight untracked WIP files; Project1-member-a on member-a/p1-delivery / 4328a11, clean. Content, real Main/assembly/tests and history (4a67050/08df45a) established the complete source; old hashes alone did not choose it and B was not merged again.

Only verification/docs changed. The PS1 uses UTF-8 BOM, generated Java credentials use UTF8Encoding(false), native stdin and Console.OutputEncoding are separately configured/restored, and JDK stderr is explicitly captured for PS5.1. Cleanup checks absolute prefix/identity and root/child reparse points; unsafe directories are retained, never followed. All verification uses isolated temporary data.

Windows PowerShell 5.1.26100.8521 and the already bundled PowerShell 7.6.5 were both available; neither was newly installed. With Microsoft JDK 17.0.20.1+1 and Maven 3.9.11, both commands exited 0, each with 200 tests, zero failures/errors/skips, real packaged CUI exit 0 and three business commits:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-p1.ps1 -Offline
pwsh -NoProfile -File .\scripts\verify-p1.ps1 -Offline
```

Those 200 counts belong to release cleanup, not this feature. Earlier A=36 is in evidence/member_a/implementation_validation.md; B=176 with one skip is in evidence/member_b/ac_execution_b_2026-10-21.md; v3 opening=184 and final=200 are in §4. Do not add independent branch totals or substitute 184 for the final total.

Old WIP comparison: five files were byte-identical to completion (pom.xml, SaleLineSnapshot, AftersaleReadAccess, BIntegration, OriginalSaleAccessTest); six differed (JsonStore, AftersaleReadRequest, Application, Main, OriginalSaleAccess, ApplicationTest) and were retained as potentially unique, never silently integrated. A fresh external timestamp backup saved staged/unstaged patches, eleven WIP files and 376 ignored build files with verified SHA-256. No git clean was used; the final verification rebuilt target only after preserving its previous contents. No absolute backup location is recorded here.

Original WIP stash f33c6b7c3e4e1d302323988682d135d1ae255bd4 was verified against all eleven files, the 6408320 base and the empty index; Git newline normalization aside, contents match and original bytes remain backed up. Additional engineering stash 87fbbd99ebe36ea82cb9e7e8479811f12268cffd was accidentally created in the complete tree and immediately applied back; it was also retained. No branches, worktrees, stashes or backups were deleted.

The new local member-a/p1-release branch in Project1 received complete source and cleanup commits 3ec36a5/279fe21; the specified root was actually verified. No push, main merge or remote change occurred. Subsequent requirements commits are distinct from these historical receipts.

## 7. 2026-10-22 Public English/Chinese implementation

The actual starting branch was member-a/p1-release, HEAD 0617f9cd37a638a85b46053c287c4a0ffc703b6d, with a clean worktree/index and no untracked files. Spec v0.7 already approved CR07-12 and solely maintains AC-91–100 in §11; PRD-11 references them. The baseline offline PS5.1 script actually passed 200 tests and the packaged CUI. Implementation uses the independent member-a/p1-language branch, preserving existing stashes/worktrees. A fresh backup of affected documents/diffs and existing external Chinese originals was read/hash-verified before editing; corresponding Chinese files remain outside the repository under the user convention.

Implemented: Main validates --lang en|zh, defaults to English, rejects missing/invalid language with fixed English INVALID_LANGUAGE/nonzero exit and applies language to help. Other options retain their parser rules. RepositoryBIntegration injects a process-local Texts into CuiApp. Login/main menus accept language followed by en/zh without renumbering business menus. A literal login entry preserves access to accounts named language or login. Logout retains the same Texts; new runs default to English unless an argument selects Chinese. No per-field switch, queue, preference store or business field was added.

Resources: paired UTF-8 en.properties/zh.properties in src/main/resources/nz/ac/aut/pdc/supermarket/i18n; Texts centralizes parameter formatting, labels, observable fallback and immutable authorized projection rendering. No user value is used as a dictionary key. SharedOperations supplies display content, stable error codes and parameters independently of the current language; OperationFeedback retains its two-argument constructor and adds display rendering. JsonStore, shared IF-01/04/05/11 signatures, authentication, permissions, quotas, price allocation, clocks and business persistence algorithms are unchanged. ERD and the formal shared specification are unchanged because no storage/public IF contract changed.

Chinese absence falls back to English. Missing English or both missing returns exactly TEXT_UNAVAILABLE Text unavailable. Resource keys and format placeholders are checked. System text is English in English mode; user names/units/reasons/opinions and historical values, including an existing Chinese customer-return disposition, remain verbatim rather than being rewritten to appear monolingual. Quote/query views use labels and structured fields instead of raw collection/record toString or JSON output; stored structured event snapshots are rendered without changing their values.

On 2026-10-22, per user decision, Chinese comment text was removed from all 102 changed Java files (255 comment lines deleted, 0 Chinese comment lines remaining); the existing English comments were kept, empty comment blocks were collapsed and no business statement changed. Chinese UI resources, Chinese mode and the external Chinese working documents were retained. An earlier bilingual pass had added English summaries in 84 production and 14 historical test-fixture files; English-only comments were not duplicated. Existing assertions that depended on Chinese UI assert the English default, retaining their business assertions. Chinese string literals used by validation, tests and the Chinese resource bundle remain; they are data, not comments.

### Automated and packaged evidence

The final sequential runs used Microsoft OpenJDK 17.0.20.1+1 and Maven Wrapper 3.9.11:

| Actual command/environment | Exit and result |
|---|---|
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-p1.ps1 -Offline`, Windows PowerShell 5.1.26100.8521 | 0; 214 tests, zero failures/errors/skips; default-English and Chinese packaged CUI exits both 0; three legal commits per isolated run. |
| `pwsh -NoProfile -File .\scripts\verify-p1.ps1 -Offline`, bundled PowerShell 7.6.5 (reported by the earlier session) | 0; same 214/0/0/0 and both packaged CUI exits 0. Not re-run on the current host, where `pwsh` is not installed; the PowerShell 5.1 row above is the reproducible evidence. |

Surefire XML supplies the count. The original 200 tests remain, plus 14 language tests (TextsTest, LanguageRuntimeTest and TextCoverageTest). Packaged runs use fictional English product/unit/source samples and verify login, product, legal opening 5, sale 1, history, one product, one sale, two stock entries and matching 3.33 sale amounts. Chinese user data is separately checked through real-service tests and an actual Chinese terminal account initialization. Both resource files and the real Main are present in the dependency JAR; production test-prototype isolation checks remain in force. CLI subprocess checks confirm invalid/missing --lang exit 1, Chinese help exit 0 and invalid language even with help exit 1.

Real terminal evidence after the UTF-8 correction: Chinese role/username/password prompts display correctly, passwords are not echoed; three fictional Chinese usernames were saved verbatim, zero business records and ordinal 0, initialization/check exit 0. Main uses explicit UTF-8 output streams because Windows console encoding can override file.encoding; initialization prompts use those streams while Console.readPassword retains non-echoed input. README explains UTF-8 terminal input/output. Earlier malformed format calls, missing field labels, a test's invalid role, the mistaken substring-based language-field check and an accidentally overlapping build (cleanup/class-loading errors) were corrected; those failed runs are not counted as passes. Final verification was sequential.

A follow-up audit of this branch found that the English query view could print `TEXT_UNAVAILABLE` for the `VOIDED` and `NEEDS_CORRECTION` document statuses and lacked the `CANCEL_AFTERSALE` action label, plus four projected field names (`productId`, `eventKey`, `commitOrdinal`, `replacementLineId`). Those labels were added to both resources and `TextCoverageTest` now asserts that every displayed enum/status/action value and the four projected fields resolve in both languages, raising the suite from 212 to 214 tests.

The regular verification script cleaned its own guarded temporary roots. Three separately created interactive fixtures were retained after the tool rejected recursive cleanup with only `blocked by policy`; they contain only this invocation's fictional data and are outside Git. Their locations are recorded solely in the external evidence manifest, never here. All tests/runs avoided real business data/.


### Acceptance trace (not a second scenario table)

| Spec reference | Implementation/test evidence; manual acceptance remains pending |
|---|---|
| AC-91/92/93 | Main language/default/help parsing; LanguageRuntimeTest startupArgumentsAreExplicitAndInvalidOnesFailBeforeStorage; real packaged default/en/zh help and CUI. Includes --lang without a value. |
| AC-94/95 | Real-auth login/main switching, same actor/state/ordinal, logout retention and new-instance reset; no unsubmitted form exists at the switch boundaries, so only that subcheck is not applicable. |
| AC-96/97 | Chinese user-data preservation; equivalent English/Chinese CUI workflows compare product, stock, money and quote totals; real packaged sale amounts are equal. |
| AC-98 | Same original key across language switches and retries; unchanged snapshot bytes/ordinal and no duplicate effects or language fields. |
| AC-99 | Real success/rejection and injected pre-replacement IOException; selected-language feedback with unchanged stable codes. Authentication technical failure uses an explicitly marked failing AuthService double. |
| AC-100 | Injected missing-text maps in TextsTest; shipped resource key/placeholder parity. No production failure injection or missing-resource configuration is added. |

Statuses are separate: implemented; automated verification passed where recorded; real packaged runtime passed where recorded; manual acceptance pending. Runtime automation is not all-course-AC approval. Remaining manual work includes full human bilingual wording/navigation review, an actual user's terminal/environment check, course report/video/ZIP and personal signing. Linux/macOS were not run. Course originals/Canvas were not re-verified during this feature; the previous document's source records remain historical. No business-rule conflict requires a new user decision. No push, main merge or remote setting change is authorized or performed.

## 8. Reconstructing validator template and final design evidence (2026-10-22)

Start: clean main at `99e2a73d987377b7ae031a685118f296d0c84a2e`, with no staged, unstaged or untracked changes; created independent local branch `member-a/p1-validator-template`. Applicable AGENTS files were checked; none were found. Existing documentation originals and their Git differences were saved outside the repository and SHA-256/readability verified before editing. Existing external Chinese copies were protected separately.

Added `infrastructure.AbstractReconstructingCommitValidator`; migrated only `StockOperationValidator` and `ReversalCommitValidator`. The shared template reconstructs, propagates owner results, applies existing SubmissionTime, strictly compares the complete candidate with shared.CandidateEquality, then invokes domain review. Concrete rules and the original reversal exception boundary remain local. The six-argument public interface, registry and JsonStore call boundary are unchanged. Aftersale/draft/receipt/IF-11 validators remain unchanged; no blanket deletion rule, business change, JSON model, migration, language change or removed-comment restoration was introduced.

Actual verification (JDK 17.0.20.1+1; Maven Wrapper 3.9.11):

| Command | Result |
|---|---|
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-p1.ps1 -Offline` before edits | Exit 0; 214 tests, zero failures/errors/skips; English/Chinese packaged CUI exit 0, 3 legal business commits |
| `.\mvnw.cmd --offline -q '-Dtest=ReconstructingValidatorTest,StockOperationValidatorTest,ReversalCommitValidatorTest,AftersaleCommitValidatorTest,DraftDeletionTest,SubmissionTimeTest,FullRuntimeTest' test` after refactor | Exit 0; 42 tests, zero failures/errors/skips |
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-p1.ps1 -Offline` after refactor | Windows PowerShell 5.1.26100.8521; exit 0; 219 tests, zero failures/errors/skips; English/Chinese packaged CUI exit 0, 3 legal business commits |
| `pwsh -NoProfile -File .\scripts\verify-p1.ps1 -Offline` after refactor | PowerShell 7.6.5; exit 0; 219 tests, zero failures/errors/skips; English/Chinese packaged CUI exit 0, 3 legal business commits |

The five new tests exercise both real validators through CommitValidator, strict deltas/times, permission/exception boundaries, result metadata and post-review ordering. A clearly test-only Probe isolates template behavior; existing DraftDeletionTest also retains its labelled permissive business double. These are not substituted production dependencies. Early targeted runs failed because of fixture mistakes (invalid barcode, an allowed role assumed forbidden, and a deduplicating builder); fixtures were corrected before the passing targeted and full runs. No business implementation was changed to accommodate those mistaken assumptions. Full regression retains dual-quota/allocation, D1/APPROVED receipt/full inverse group, draft/correction deletion, failure/recovery, time and original-key replay coverage. Verification uses temporary data, never real business data.

[Final design evidence](./design/final/README.md) includes the complete system Mermaid source/SVG, source-hashed inventory (128 production files / 206 named types), a supplementary template diagram, rationale and exact video impact. Types are counted by reflection including all nested named types; this is not a replacement for earlier statistics made under different scopes. V1/review/V2 and the finished video are preserved. Actual video frames and matching SRT identify the final-structure segment and the historical 214-test card/voice requiring a limited update if used as current evidence. No video was edited or re-recorded.

Spec AC-06 now links implementation/automatic evidence and explicitly leaves human acceptance pending; PRD links the same evidence. Requirements and ERD remain unchanged. Repository documents are English and corresponding Chinese evidence is retained outside the repository under the established user convention. This supplies factual report material, not reflection/signature or course acceptance. No push, main merge or remote change was performed.
