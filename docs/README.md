# Documentation index

This directory holds the project's current documents, evidence and archived material.
Read this page first; it says which copy is authoritative and which copies are only
history.

## 1. Current requirements and contracts (authoritative)

| File | Role |
|------|------|
| `Project_Spec.md` | Requirements baseline and change control (frozen sections; CR register in §17) |
| `Project_PRD.md` | Product requirements and acceptance criteria (AC-01 … AC-100) |
| `Project_ERD.md` | Data model, tables, keys and persistence constraints |
| `Shared_Boundaries_and_Interface_Spec.md` | Current cross-module contract (submission port, working copy, change sets, error codes) |
| `Maven_Framework.md` | Build/verification setup, JDK and Maven Wrapper notes |
| `P1_Preliminary_Work_Distribution_and_Interface_List.md` | Work packages and shared-asset ownership (v0.3) |
| `P1_Implementation_Log.md` | Delivered scope, verification records and evidence pointers (current) |

When a requirement changes, update the Spec/PRD through the CR process; do not edit
the archived design documents to match new code.

## 2. Implementation evidence

| Path | Content |
|------|---------|
| `evidence/member_a/implementation_validation.md` | Member A implementation evidence, verification commands and limits |
| `evidence/member_a/gpt/` | Member A GPT session exports: per-role transcripts, tool-call logs and raw `rollout-*.jsonl` sessions (2026-10-19 … 2026-10-21) |
| `evidence/member_a/deepseek/` | Member A DeepSeek main-line audit archives (zip) for the deadlock and audit-2.0 rounds |
| `evidence/member_b/` | Member B design review, AC coverage/execution, AI usage log, implementation log and design evolution |

Evidence files are snapshots of work performed on the dates they state. Paths inside
them were updated during the 2026-10-22 documentation cleanup when files moved; the
facts and dates were not changed.

## 3. Design and diagrams

| Path | Use |
|------|-----|
| `design/report/` | **Current class diagrams for the report** (8 PNG + Mermaid sources, generated from the delivered code) |
| `design/final/` | Full generated class diagram (`system.mmd`/`system.svg`), abstract-validator detail, type inventory |
| `design/use_cases/` | P1 use-case diagrams (`use_case_v1.svg`, `use_case_v2.svg`) |
| `design/IF11_Proposal_A.md` | IF-11 read-and-trace proposal: approved rules, kept as a historical proposal; the delivered flow is implemented in `AftersaleDraftAccess`, `AftersaleDraftService` and `OriginalSaleAccess` |
| `erd/` | ERD Mermaid sources (common, receipt/stock, sale/promotion, aftersale) |
| `archive/design_member_b/` | Superseded design set: V1/V2/V2.1 class diagrams, rendered PNGs, design notes, IF-07 draft |

Choose diagrams by purpose:

- Report class diagrams → `design/report/*.png` (do not reuse the V2 rendered set).
- "Design corresponds to code" evidence → `design/final/`.
- Design history / how the model evolved → `archive/design_member_b/`.

## 4. Process records (historical, not requirements)

| Path | Content |
|------|---------|
| `process/Handoff_Package_For_B.md` | Handoff package used to start Member B work |
| `process/Member_A_Implementation_Handoff.md` | Member A implementation handoff |
| `process/coordination/` | A/B alignment messages (2026-10-21) |

These describe how work was coordinated at a point in time. They do not override the
Spec, PRD, ERD or the shared interface specification.

## 5. Working copies outside the repository

Chinese working translations and development notes are kept outside the repository,
under `..\docs\` (repository parent), so they are not part of a submission archive:

- `..\docs\zh\` — Chinese translations of the Spec/PRD/ERD/README and the P1 log.
- `..\docs\notes\` — planning prompts, review notes, issue summaries.
- `..\docs\_backups\` — pre-change backups.

The English documents in this directory are the versions intended for submission.

## 6. Path policy

- All paths in these documents are repository-relative unless stated otherwise.
- `docs/` root holds the authoritative requirements; `docs/design/` holds current
  design assets; `docs/archive/` is read-only history; `docs/evidence/` holds dated
  evidence; `docs/process/` holds historical coordination records.
- New documents go into the folder matching their role, and this index is updated in
  the same commit.
