# Archive (read-only history)

This folder keeps superseded documents so the current set stays small and
unambiguous. Nothing here should be edited to match new code; current material lives
in `docs/design/` and the authoritative requirements stay at `docs/` root.

## `design_member_b/`

The Member B design set from 2026-10-20/21, kept as design history:

| Item | What it was |
|------|-------------|
| `class_v1.mmd`, `class_v1.png` | First class diagram (32 types), design stage |
| `class_v2.mmd` | Second class diagram (~50 types), superseded by the implemented model |
| `v2/rendered/*.png` | Eight hand-rendered module diagrams from V2 |
| `v2_1/` | "Current implementation snapshot" from 2026-10-21 (also superseded) |
| `design_notes.md` | Working notes and decisions while designing |
| `if07_contract.md` | IF-07 draft contract (v0.3); the current contract is `docs/Shared_Boundaries_and_Interface_Spec.md` |

Why archived: the delivered code no longer matches these models in several places
(e.g. `BusinessTransaction`, `SubmissionGateway`, CR07-09 exception fields, the old
query/reversal interfaces). The report uses `docs/design/report/`, and the
source-generated full model is `docs/design/final/`.

Historical evidence files may still refer to these paths; references were updated
when the folder moved, and the content of the records was not rewritten.
