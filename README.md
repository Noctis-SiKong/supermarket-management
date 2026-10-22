# Supermarket Management System

A single-store offline Java CUI course project. The existing monolith is already wired with real JSON storage, authentication, products, receiving, promotions, sales, stock, after-sales and whole-order reversal; for run status and the verification status of this round, see the [unified implementation log](./docs/P1_Implementation_Log.md). Automated verification does not replace user acceptance, the course report, the video or the student's own signature.

## Project Demonstration Video

[Watch or download the Project 1 demonstration video](./Project1-Demonstration-Video.mp4) (4 minutes 37 seconds, 1080p).

The MP4 includes English narration and burned-in English subtitles; original Chinese development prompts are retained as evidence. It demonstrates system functionality, AI-assisted development and prompt engineering, human review, and the overall code structure. No separate audio or subtitle files are needed.

## Quick Start

Applies to Windows + JDK 17; full instructions are in [Build and run](#build-and-run) below.

For Chinese terminal input, configure UTF-8 input/output as shown in the language section below.

1. Make sure `JAVA_HOME` points to JDK 17 (Enforcer rejects Java 8 and other major versions; the `java` on `PATH` is not necessarily 17).
2. Build: `.\mvnw.cmd -B --no-transfer-progress clean package`
3. Initialise the accounts once (**a real terminal is required**, interactive input, no default accounts):
   `& "$env:JAVA_HOME\bin\java.exe" -jar target\supermarket-management-0.1.0-SNAPSHOT-jar-with-dependencies.jar --init-accounts`
   Follow the prompts to set a username and password for ADMIN/MANAGER/CLERK; once creation finishes you **go straight into the CUI** and can log in immediately.
4. Start it later: `& "$env:JAVA_HOME\bin\java.exe" -jar target\supermarket-management-0.1.0-SNAPSHOT-jar-with-dependencies.jar`
5. To check storage recovery and registration validation only, without entering the business flow: append `--check` to the start command; to view the options: append `--help`.

Note: `--init-accounts` only initialises empty storage, refuses to run when a snapshot already exists, and never overwrites existing accounts; passwords are not echoed and only the SHA-256 hash is stored. IDEs and redirected environments have no console, so use `--accounts <local-credentials.json>` to pre-provision accounts instead. Only the JAR with dependencies can be started on its own; do not use the plain JAR.

## Reading Order

1. [Spec: requirements and business-rule baseline](./docs/Project_Spec.md) (v0.7, frozen as the requirements baseline).
2. [PRD: functional requirements and acceptance mapping](./docs/Project_PRD.md) (v0.7, frozen as the requirements baseline).
3. [ERD: logical entities, relationships and data dictionary](./docs/Project_ERD.md) (v0.7, in sync with the requirements baseline; design review items are in section 8).
4. [P1 preliminary work distribution and interface list](./docs/P1_Preliminary_Work_Distribution_and_Interface_List.md) (v0.3, work packages and shared-asset ownership; the current cross-module conventions and local coding gates are in the formal interface specification).

5. [Shared boundaries and interface specification](./docs/Shared_Boundaries_and_Interface_Spec.md) (v1.0.4 formal release plus the code-delivery appendix; the IF-11 admission and cancellation retention for CR07-11 are approved, with interface implementation / audit / integration delivered separately).

The core ERD review items are in section 8 of the ERD; the work packages are in item 4, and the formal cross-module calling conventions are maintained only in item 5.

## Scope and Technical Boundaries

P1 is a Java CUI using collection and file persistence; Maven, JDK 17 and UTF-8 JSON/Jackson have been chosen. Only P2 extends to a GUI and Derby; the GUI options the course allows are governed by the Spec. The ERD expresses logical relationships and does not mean P1 must use a relational database.

The confirmed scope is governed by the Spec and covers receipt review, on-shelf/in-warehouse stock, replenishment, sales promotions, returns and exchanges settled against the actual paid amount of the original sales line, redemption against both quantity and amount quotas, and auditing. The current baseline removes product dates, near-expiry/expiry, batches and expiry blocking from both P1 and P2; publishing to GitHub does not bring that scope back.

## ERD Diagram Sources and How to View Them

The ERD body contains four `mermaid` code blocks. Prefer opening the [ERD Markdown body](./docs/Project_ERD.md); the `.mmd` links are for downloading or editing the sources, and GitHub is not guaranteed to render them automatically when the file is opened on its own.

| Sub-view | Editable source |
|---|---|
| Common documents and audit | [01_common.mmd](./docs/erd/01_common.mmd) |
| Receipt review and stock | [02_receipt_stock.mmd](./docs/erd/02_receipt_stock.mmd) |
| Sales and promotions | [03_sale_promotion.mmd](./docs/erd/03_sale_promotion.mmd) |
| After-sales dual quota | [04_aftersale.mmd](./docs/erd/04_aftersale.mmd) |


## Path Baseline and Maintenance Rules

The definitions below apply only to this repository. **The repository root is the top-level directory containing `README.md`, `.gitignore` and `docs/`; after cloning there is no extra `Project1` level.** Code-style paths in all documents are repository-relative paths; directories outside the repository are neither recorded nor managed.

| Name | Logical definition and ownership |
|---|---|
| Repository root | Top level of the repository; the local checkout is named `Project1`, with README, .gitignore and docs directly here |
| Repository docs directory | **repository-relative path** `docs/`; the jointly maintained master copies of the Spec/PRD/ERD, may contain Chinese, and is not an English-only file directory |
| Markdown relative links | Resolved only relative to the directory containing that Markdown file; a code-style repository-relative path is not the same kind of link |
| Runtime data root | `DATA_ROOT` = `data/` under the repository root; `DataRoot` validates the project marker, the real path and writability, and is not the Markdown directory |

### Document Master Copies

The repository's `docs/` is the single collaborative master copy of the Spec/PRD/ERD; no copies outside the repository are maintained, and external archive or backup locations are not recorded in the documents. Requirement/design changes are reviewed directly in the repository, and Git records historical versions.

### Planned Paths and Report Arrangements

Code-style paths in the documents such as `docs/evidence/...`, `docs/design/...`, `docs/decisions.md` or `docs/decisions/...` are all **repository-relative paths**. The existing documents comprise the Spec/PRD/ERD/work-distribution list/shared specification, the four diagram sources under `docs/erd/`, and the already created `docs/design/member_b/` and `docs/evidence/member_b/` (containing B's V1/V2, AI review, design evolution and framework reproduction records); only `docs/decisions/` and `docs/decisions.md` remain planned locations that have not been created yet. Empty evidence files are not created just to make the plan "look complete", and no duplicate path-governance record is added.

During the working phase the English Spec/PRD/ERD in the repository's `docs/` are the collaborative main line (the corresponding Chinese versions are kept in a designated directory outside the repository); before submission the final report is converted to English and the Chinese working drafts are removed from the submission package/final delivery directory, so the Chinese drafts are not a parallel final report. A Chinese working draft is not a submission report and carries no formal §2.6/§3.7 declaration; the formal declaration is completed and signed by the student only in the English final report, following the template requirements, and a Chinese working draft does not replace it. The English final report uses the English report location under the repository root; the final file name is settled when the report is assembled, and this round adds no report subdirectory and does not change the Canvas arrangement; the repository root may contain English working documents, code and build files.

Document history is recorded by Git, and this document does not define backup locations outside the repository. The previous-version JSON snapshot of runtime data, temporary write files and attachment recovery copies are runtime storage, use the implemented `DATA_ROOT` sub-layout described below, and **must not be copied into the repository**. The ZIP/report/demo evidence submitted for the course assignment is not runtime data, so do not ignore it with global log or archive wildcards.

### Runtime Data and Attachments: Current Layout and Security Contract

What has been confirmed is a single UTF-8 JSON snapshot/schemaVersion, atomic replacement/previous-version recovery, and attachment relative paths plus hashes; `DATA_ROOT` is set to `data/` under the repository root, root resolution and the security checks are implemented by `DataRoot`, and corruption or a non-writable location blocks start-up explicitly. See [Spec runtime path design open items](./docs/Project_Spec.md#101-runtime-path-contract-and-pending-design) for details; the remaining technical options are not treated as approved deployment rules, and no manual configuration hurdle is added for the course.

The `data/` in older text refers only to the runtime data logical root `DATA_ROOT`, and the old `data/attachments/` is uniformly expressed as `DATA_ROOT/attachments/`; an attachment record stores only `attachments/<filename>`, **relative to DATA_ROOT rather than to the working directory, the JAR, the repository or the Markdown directory**. The root location is set to `data/` under the repository root; the runtime sub-layout is snapshot.json, previous.json, writer.lock, *.next.json and attachments/; business goods are not created automatically.

Path security contract: the `relative_path` of a persisted attachment always uses `/`; absolute references such as drive letters/UNC/root paths, URIs, empty paths, backslashes, `.`/`..` segments and references that resolve outside DATA_ROOT are rejected. The root and the target are normalised and the final real location is validated (including symbolic links/junctions), with containment decided by path components — a string prefix must not pass for it. Checks happen before writing and when actually opening, so that a link cannot point the target outside the root. When choosing a source image to import in the CUI, a user-local path may be used, but **the source absolute path must not be serialised into an attachment reference or written into Git material**.

Chinese characters and spaces are legal filename characters; do not split on spaces or concatenate shell commands to operate on files. Targets use an independent UUID with CREATE_NEW; on collision or import failure the reference is refused, and existing files must not be silently overwritten nor the source directory/user name encoded into the target name. A copy/read/validation failure must not store an "attachment succeeded" reference, and a validated copy must not be overwritten; when there is no attachment, review is still allowed under the existing rules, and no mandatory upload is added for path governance. For an existing reference whose file is missing or whose hash does not match, do not treat it as a normal attachment and do not silently remove the historical association; a full restore blocks writing when an attachment is missing, a reference is abnormal or a hash does not match; unreferenced imported copies are not cleaned up automatically, and receipt review or business recovery rules must not be changed at will. The per-attachment limit has been synced at `5,000,000` bytes; a failure does not overwrite a validated copy and does not publish an invalid reference.

### Ignore Rules and Pre-Submission Checks

No tracked business JSON, image attachments, course PDF/DOCX or local backups have been found so far. `.gitignore` applies only to untracked files and will not clear paths/personal references that are already committed; this round does not rewrite history or untrack files. Older commits may still retain references to the original text, and handling history requires separate explicit authorisation.

The runtime data root is set to `data/` under the repository root; the ignore rules cover only in-repository paths, so checks are still required before submission. A narrow defence line is currently kept for data mistakenly generated inside the repository: the root-level `data/`, `runtime/`, `attachments/`, `logs/`, `tmp/`, `backups/`, `.local/`, plus build outputs and explicit machine-local settings. Course source files are guarded against accidental copying only by known filenames; PDF/DOCX are not ignored globally. Put samples in the explicitly tracked `docs/examples/` or in the later test-sample location, using fictitious/desensitised data; there are currently no such sample files. Do not keep unique evidence that needs to be tracked inside a real runtime data directory.

Before release, check the Git diff, the pending-commit list, relative links, paths/personal information and data files; `.gitignore` is not a sensitive-information scanner, and changes are not uploaded to GitHub automatically. The final state of the post-release errata commits for this round's v1.02 release is determined by verification with `git status`, `git log` and `git ls-remote`.

### Git Verification Record for This Round

v1.02 has been released: main commit `4d7b1f28c6d9caa0473092e77ab27aa1e724b983`, `git ls-remote origin refs/heads/main` returns the same commit, and the working tree is clean; B's design results and the formal shared specification are both included. The post-release errata commits are located through Git history and do not change the release receipt above.

## Course Material Reference Archive (Source Text Not Distributed)

| Material | Required locator and summary of requirements | Distribution status |
|---|---|---|
| COMP603/ENSE600 2026 CJLU project requirements, `Projects1and2.pdf` | reader/print p3–6: P1 requirements/real design iteration, Java CUI, collections/File I/O, OOP/SOLID, standard build, log/≤5-minute video; p7–10: P2 GUI/Derby/Git/JUnit; p12–13: report/source/video in a single ZIP | Not included in the repository; specific dates still to be checked on Canvas |
| Assignment 1 report template, `Assignment1_Report_Template_CJLU2026.docx` | §1 build/credentials; §2.2 requirement ID/acceptance/E-D; §3.2–3.6 scope, the two class diagrams and the real review/evolution; §2.6/3.7 own reflection and declaration; appendices A/B log/contribution | Not included in the repository; the whole template is not copied |
| U2/U3 and the later U4/U5/U6 session decisions | Retain the Spec's source codes, confirmed rules and original section locators; do not claim, on the basis of an undistributed attachment, that collaborators have read the source text | Not included in the repository; personal paths/attachment UUIDs are not recorded |

This round completed the path/Git checks and the document sync, reusing the course locators already recorded, and does not pass off earlier full-text reading notes as a fresh reading in this round.

These summaries only support the necessary traceability and do not replace the course source text or extend the business requirements; no course source files/session attachments were added.

## Build and Run

Uses JDK 17 and the repository's single Maven project. The PowerShell 7.6.5 run on 2026-10-21 is a historical verification-environment record; the environment and results re-verified during the 2026-10-22 release wrap-up are in the unified implementation log. The scripts aim to be compatible with Windows PowerShell 5.1 and PowerShell 7. One command reproduces the offline build, all tests, executable-package dependency isolation and a real JAR/CUI smoke test:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-p1.ps1 -Offline
# Can also be run where PowerShell 7 is installed (installation not required):
pwsh -NoProfile -File .\scripts\verify-p1.ps1 -Offline
```

Drop `-Offline` when the dependencies are not cached yet for the first run. The script only uses random fictitious accounts, a legal opening balance and sales in a temporary standalone project root that it creates, cleans up that temporary directory after verification, does not touch the real business data/, and neither prints nor commits passwords/hashes. The step-by-step commands are as follows:

```powershell
# No need to reset JAVA_HOME when it is already JDK 17; the following only probes for an available local temporary tool.
$taskJdk17 = @((Join-Path $env:LOCALAPPDATA 'pdc-build-tools/jdk-17.0.20.1+1'), (Join-Path $env:TEMP 'p1-jdk17/jdk')) |
  Where-Object { Test-Path (Join-Path $_ 'bin/java.exe') } | Select-Object -First 1
if ($taskJdk17) { $env:JAVA_HOME = $taskJdk17 }
& "$env:JAVA_HOME/bin/java.exe" -version
./mvnw.cmd -B --no-transfer-progress clean verify
& "$env:JAVA_HOME/bin/java.exe" "-Dfile.encoding=UTF-8" -jar target/supermarket-management-0.1.0-SNAPSHOT-jar-with-dependencies.jar --check
# Pre-provision the three role accounts once in a real interactive terminal; no default password, passwords are not echoed, and only the encoding of B's hasher is stored.
& "$env:JAVA_HOME/bin/java.exe" "-Dfile.encoding=UTF-8" -jar target/supermarket-management-0.1.0-SNAPSHOT-jar-with-dependencies.jar --init-accounts
# Normal start-up thereafter:
& "$env:JAVA_HOME/bin/java.exe" "-Dfile.encoding=UTF-8" -jar target/supermarket-management-0.1.0-SNAPSHOT-jar-with-dependencies.jar
```

If the temporary JDK above is not found, choose an installed JDK 17; Enforcer rejects Java 8/other major versions, and the build is not changed to Java 8. The first download of project dependencies may need the network, business runs are offline; add `--offline` once the cache is complete. The platform actually tested this time is Windows; the commands have not been executed on other platforms.

The executable package is the `*-jar-with-dependencies.jar` with dependencies, including Jackson; the plain JAR has no embedded dependencies, and starting it on its own must not be treated as verified. The main class is `nz.ac.aut.pdc.supermarket.app.Main`, which looks for the project marker upwards from the executable's location and does not treat the shell's current directory as DATA_ROOT; when moving the executable, use `--project-root <project-root>`. Use `--help` to view the options, and `--check` to actually wire up and run the full snapshot validation without committing business operations.

A non-interactive start can use `--accounts <local-credentials.json>` to initialise empty storage: the array element fields are `accountId/username/passwordHash/role`, the roles are ADMIN/MANAGER/CLERK, and the hashes are compatible with the existing B Sha256PasswordHasher. It only initialises empty storage and cannot be used to overwrite existing accounts; keep the local file in an ignored directory such as `.local/`, and do not upload passwords, hashes or runtime data. With no accounts, the system clearly reports that login is not configured. `--init-accounts` refuses when a snapshot already exists; aborting password/username input does not install a half set of accounts.

The CUI covers product creation/editing, opening balances, receiving/correction/review with optional attachments, promotions, simulated payment after a sales quote, replenishment, loss registration/disposal/correction, restricted history/quota queries, single-line and mixed after-sales, cancellation/deletion and whole-order reversal. After-sales first saves the IF-11 trace and then shows the necessary original-order fields and the current settlement; while it is not yet confirmed you can exit and cancel by the displayed target draft number. `CANCELLED` keeps the trace and has no effect on refunds, stock or quota. A change in the sales quote or an expired working copy rejects the transaction and requires a new quote confirmation.

When the outcome of a request is unknown, look up the original key first. Menu 15 retries the original request saved by the current process; the after-sales and reversal forms also allow entering the original key with identical business input, so the original result can be reused after a restart. Ordinary history remains scoped to the clerk themselves, or to everything for the manager/administrator; after-sales proof is an exact original sales order number or a printed stable sales line number, and no cross-person browsing is offered. A recovery anomaly blocks start-up and keeps the bad files; the administrator first backs up the complete data/, then uses JsonStore's explicit PREVIOUS recovery capability to validate and restore the complete previous version and its attachments; hand-splicing versions or silently clearing data is forbidden. No write entry point that bypasses the corruption checks is provided in the CUI.

## Development and Release Boundaries

After the 2026-10-21 v3 authorization, a single owner maintains the complete monolith; the original A/B division of work and design are retained as historical contribution and provenance, and waiting for the other party to connect is no longer treated as an implementation status. B's V1/V2 have been audited and are included in the baseline; the design phase is not redone.

This round covers local development and verification only. Any upload to GitHub, push, or merge into main must first present the specific diff for human review, then obtain explicit approval for that specific operation; there is no automatic publishing. An independent worktree protects in-flight changes from other sessions; the actual branch and local delivery record are in the unified implementation log.

Document history is recorded by Git; this document does not define backup locations outside the repository. Runtime data backups follow the above sub-layout under `DATA_ROOT` (the `data/` directory at the repository root) and must not be mixed. Course delivery remains governed by the PDF, template, and Canvas requirements.

## Maven Framework and Build

The project uses a pinned Maven Wrapper, JDK 17 Enforcer, JUnit and Jackson, and an assembly executable package. For the current commands, dependencies and historical baseline record, see [Maven Framework Notes](./docs/Maven_Framework.md); for the current implementation and actual results, see [Unified Implementation Log](./docs/P1_Implementation_Log.md). The historical wording "empty project / no Main yet / waiting for A" only reflects the point in time when the respective record was written.

IF-11 is implemented per the approved [CR07-11](./docs/Project_Spec.md#175-cr07-11-if-11-read-admission-and-local-cancellation-retention); the approved baseline and the course AC are not rewritten in this change. Complete automated test evidence, test-double scope and real-chain scope are listed separately; course manual materials are not counted as completed.

## English and Chinese

English is the default. Chinese is a public supported language. Select the startup language:

```powershell
# Configure this terminal for UTF-8 input/output (Windows PowerShell 5.1 and PowerShell 7):
[Console]::InputEncoding = [Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
$OutputEncoding = [Text.UTF8Encoding]::new($false)
& "$env:JAVA_HOME/bin/java.exe" "-Dfile.encoding=UTF-8" -jar target/supermarket-management-0.1.0-SNAPSHOT-jar-with-dependencies.jar --lang en
& "$env:JAVA_HOME/bin/java.exe" "-Dfile.encoding=UTF-8" -jar target/supermarket-management-0.1.0-SNAPSHOT-jar-with-dependencies.jar --lang zh
```

At the login username prompt or authenticated main menu, enter `language`, then `en` or `zh`. If a username is `language` or `login`, enter `login` first, then the literal username. Business menu numbers remain unchanged. Switching does not log out, submit anything, discard quoted/pending requests, change original request keys or rebuild business operations. No per-field switch is added; those menu boundaries have no unsubmitted form, so the form-preservation subcheck is not applicable there. The selection survives logout within this process. Restarting uses `--lang en|zh`, or English if omitted. Invalid/missing values are rejected with `INVALID_LANGUAGE` and a nonzero exit. Existing CLI rules otherwise remain unchanged.

System prompts, labels, help and feedback follow the selected language. User-entered product names, units, reasons, opinions, usernames and stored historical data remain verbatim, including Chinese data in English mode. Stable business codes/IDs and stored enum values are unchanged. Chinese missing text falls back to English; missing English or both missing displays `TEXT_UNAVAILABLE Text unavailable.`. Resources for both languages are included in the JAR. No language environment variable, configuration file or account preference is added, and language is absent from business snapshots, fingerprints and idempotency.

CR07-12 is implemented; automated and real packaged-runtime verification evidence for AC-91–100 is in the [implementation log](./docs/P1_Implementation_Log.md#7-2026-10-22-public-englishchinese-implementation). Manual acceptance is pending. Chinese comment text is removed from Java sources (English-only substantives), while Chinese UI resources and Chinese mode remain. The English repository/external Chinese document arrangement is a user decision, not a course mandate to exclude Chinese drafts. No final report or signature is produced by this feature.
