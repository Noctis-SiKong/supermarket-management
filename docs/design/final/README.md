# Final production design evidence — 2026-10-22

This is the current implementation view for Spec CR-07, TC-02/03 and AC-06. It supplements, and does not rewrite, the genuine [B V1](../member_b/class_v1.mmd), [V2](../member_b/class_v2.mmd), reviews and subsequent V2.1 snapshots. Those snapshots describe their historical scope; they are not the complete current system diagram. This update introduces no requirement CR or data-model change. Human design/AC-06 review remains pending.

## Complete system diagram

- [Complete system source](system.mmd) and [complete scalable image](system.svg): **128 production Java source files, 206 named types, 224 relationships**. Open the SVG in a browser and zoom/scroll at native text size; it is intentionally a large system sheet, not a slide-sized overview.
- [Machine-readable inventory](inventory.json) records fully qualified names, source paths, LF-normalized source SHA-256 (independent of Git checkout line endings), declared fields and key methods. Every production source file is represented. Nested named classes, records, enums and interfaces are included. JDK/third-party types, synthetic/anonymous/local classes, generated record accessors/Object methods and enum `values/valueOf` methods are excluded. The UML uses erased, shortened parameter/return types; the inventory retains full field types. Inherited methods are shown at their declaring class. Private helper methods are omitted; constructors and public/protected methods are shown.
- Relationships include actual inheritance/interface implementation, declared field-type associations, and selected source-reviewed calls. Arrows labelled `field type` describe references, not ownership or composition; this is not an exhaustive call graph. `SubmissionGateway`, `ReversalService` and `FictionalDependencies` are not production types and do not appear as production implementations.
- [Validator detail source](validator-template.mmd), [SVG](validator-template.svg) and [PNG](validator-template.png) are supplementary partial views of the same production types. They do **not** substitute for the complete system diagram.

The diagram generator is [scripts/generate-final-class-diagram.py](../../../scripts/generate-final-class-diagram.py). After the normal JDK 17 build, `python scripts/generate-final-class-diagram.py` regenerates source/inventory. Optional `--mermaid-js <existing-local-library>` renders SVG using an existing Edge browser and Python Playwright. No runtime accounts or business snapshots are read, no browser/package installation is performed, and class inspection disables initialization. Generated SVGs are committed so viewing needs no renderer or network. The supplemental PNG was captured from the rendered SVG in the existing browser.

## Real abstraction and production path

`infrastructure.AbstractReconstructingCommitValidator` implements the unchanged six-argument `SubmissionPort.CommitValidator.validate(OperationContext, ActorContext, BusinessRequest, WorkingCopy, ChangeSet, Instant)` contract. Its template:

1. Calls the concrete `reconstruct` implementation with the original request, baseline and `acceptedAt`.
2. Propagates the entire rejection/failure result, including code, field, record ID and detail.
3. Applies the existing `SubmissionTime.assign` to the reconstructed expected candidate.
4. Compares the complete expected and actual delta through the existing `shared.CandidateEquality.same`; no time fields are globally ignored.
5. Returns the concrete mismatch result, or invokes `review` only after exact equality.

`StockOperationValidator` and `ReversalCommitValidator` already shared this ordered reconstruction/time/comparison flow, so both now extend the template. Stock keeps operation/permission checks and `StockBusiness.prepare`; reversal keeps manager/admin admission, target/request parsing, effective-state and D1 checks, inverse assembly and `SharedStockAdapter.review`. The template has no business-type switch, mode flags, persistence, state mutation, general deletion restriction or common exception catch.

The template's `validate` is deliberately overridable: reversal checks authorization first and wraps `super.validate` in its **existing** RuntimeException-to-INVALID_INPUT boundary, including normalization, equality and stock review. Stock retains its existing exception behavior. This preserves the distinct error policies instead of inventing one error mapping. The default `review` has a real role: stock reconstruction already performs its domain checks; reversal requires an additional stock review after equality.

Actual assembly remains `Main → Application / RepositoryBIntegration → BCommitValidators.create → JsonStore`'s registered validator map. On submit, `JsonStore` resolves the registered `CommitValidator` and calls it under its existing lock with the same accepted time. Dynamic dispatch reaches the template and each concrete reconstruction; reversal's wrapper preserves its previous envelope. Registration and JsonStore were not changed. Validators never submit or write files.

`AftersaleCommitValidator` is deliberately **not migrated**: it retains draft/trace admission, eligibility, dual-quota reconstruction and its own exception boundary. The two migrated validators already provide meaningful common-flow reuse; broadening the change is unnecessary to establish actual abstraction. Draft deletion, IF-11, receipt correction, loss workflow and A operations keep their existing validators. No removed historical comments are restored, and language functionality is unchanged.

## Verification and evidence limits

Baseline: clean `main` at `99e2a73d987377b7ae031a685118f296d0c84a2e`; work began on `member-a/p1-validator-template`. JDK 17.0.20.1+1, Maven Wrapper 3.9.11. Baseline PowerShell 5.1 verification passed **214** tests. The completed refactor passed **219** tests (five new tests), zero failures/errors/skips, independently under Windows PowerShell 5.1.26100.8521 and PowerShell 7.6.5:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-p1.ps1 -Offline
pwsh -NoProfile -File .\scripts\verify-p1.ps1 -Offline
```

Both scripts exited 0, performed the existing offline clean build/JAR dependency checks, and reported English and Chinese packaged CUI exits 0 and three legal business commits. Temporary verification data was isolated; no real `data/` was used. These are actual packaged-runtime checks, not all-course acceptance.

The targeted command also exited 0 (**42 tests**):

```powershell
.\mvnw.cmd --offline -q '-Dtest=ReconstructingValidatorTest,StockOperationValidatorTest,ReversalCommitValidatorTest,AftersaleCommitValidatorTest,DraftDeletionTest,SubmissionTimeTest,FullRuntimeTest' test
```

`ReconstructingValidatorTest` calls both real concrete validators through `CommitValidator`, checks valid normalized candidates, wrong acceptance time, missing/extra/tampered/cross-document/removal candidates, original permissions and exception boundaries. Its explicitly test-only `Probe` checks rejection/technical-failure metadata propagation, ordering of post-review, and the absence of a blanket removal ban; it is not real business integration. Its receipt baseline is a unit fixture, not evidence that goods were established through a UI. Initial test-fixture errors (barcode, authorized role and builder deduplication) were corrected before the successful runs; production rules were not changed to make them pass.

Existing full regression covers after-sale dual caps, stable allocation/amount results, D1, APPROVED-receipt reversal and complete inverse effects, real-storage draft deletion, receipt correction removals, failure/restart, permissions, managed times and original-key replay. `DraftDeletionTest` includes its already-labelled permissive business double; `FullRuntimeTest` exercises real assembly and storage. Existing failure-injection tests remain tests, not evidence of a real disk failure. Human review of diagram readability/semantics and course AC-06, report, video, ZIP and personal signing remain outstanding.

## Design evolution and video handoff facts

V1/review/V2 remain genuine historical design evidence. This final evolution removes duplicated commit-validation orchestration from two concrete validators and makes polymorphism/abstraction visible in a real saving path. It does not retrospectively claim that this abstract base existed in V1/V2 or that the preceding review approved this new implementation. This section supplies factual report material, not personal reflection or a signature.

The tracked `Project1-Demonstration-Video.mp4` was read only; SHA-256 `af1dc9f7e297a5ff1a4c0e7ac8955660c4f015af28f81fd0b610e6f0cf50f462` matches the existing final cut `10-project1-final-human-ai.mp4`. Its matching English SRT and actual frames at 03:39, 03:55 and 04:12 were checked. Required local editing handoff (no video was changed):

| Existing interval / shot | Effect and proposed limited replacement |
|---|---|
| 03:18.117–03:35.459, V1→V2 narration | Retain the genuine historical explanation; do not rewrite it to claim the new template existed then. |
| 03:37.050–03:50.180, final structure / sale path | Existing broad sale path remains valid. Supplement its final-design shot with the new CommitValidator → abstract template → stock/reversal inheritance and the JsonStore validation call. Record the new source/template code as the KF-C2 interface/abstract/concrete evidence; do not replace unrelated sale demonstrations. |
| 03:51.717–04:04.734, Result and collections | Existing code/meaning remains valid; no compulsory re-recording. A template inset may be added if needed for OOP evidence. |
| 04:06.117–04:20.872, durable boundary and 214-test card; 04:16.192–04:20.872 spoken number | The old 214 count is authentic historical evidence. If presented as the current final verification, replace only the card/number with the new 219 result; otherwise label it explicitly as the pre-refactor run. |

Video editing/re-recording and final human acceptance require the owner's subsequent work. This change does not overwrite the existing video, its narration, historical diagrams or other evidence. No push, main merge or remote change was performed.
