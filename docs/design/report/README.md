# Implementation Class Diagrams (report set)

Eight diagrams for the final report, drawn from the delivered code (not from the
earlier design-only class diagram). Each figure has a Mermaid source (`*.mmd`) and a
2x PNG rendering (`*.png`) with the same warm-neutral style as the approved sample.

| # | File | Content | Suggested report use |
|---|------|---------|----------------------|
| 00 | `00_overview.png` | Layers and main components: entry, application facades, domain services, contracts, infrastructure | §3.5 architecture overview (full page) |
| 01 | `01_catalog_opening_receipt.png` | Product/opening, receipt create-correct-review, attachments, document header/line, submission port | §3.5.1 catalog and receiving |
| 02 | `02_sale_promotion.png` | Promotion/pricing, sale candidate, sale line snapshot, stock and money entries | §3.5.2 sales and promotions |
| 03 | `03_stock.png` | Stock business, validator/assembler, stock service, loss workflow, balances and positions | §3.5.3 stock maintenance |
| 04 | `04_aftersale.png` | Shared operations entry, aftersale engine as service factory, aftersale service, submission service, change-set assembler, commit/integrity validators, draft lifecycle, redemption/replacement/quota view | §3.5.4 after-sales |
| 05 | `05_reversal.png` | Reversal command, reconstructing-validator template and concrete validator, effective-dependency blocker, change-set assembly | §3.5.5 reversal |
| 06 | `06_query_auth_cui.png` | Query service and views, local authentication, credential store, identity resolver, roles, CUI entry | §3.5.6 query, authentication and UI |
| 07 | `07_shared_persistence.png` | Submission port, `CommitValidator` interface, reconstructing-validator template with its two concrete subclasses, working copy, change set, submission time, JSON store | §3.5.7 shared submission and persistence |

Five further classes implement `SubmissionPort.CommitValidator` directly (`SharedStockCommitValidator`,
`LossWorkflow`, `AftersaleCommitValidator`, `AftersaleDraftValidator`,
`AftersaleDraftDeletionValidator`); they are omitted from figure 07 to keep the
template-method story readable and are listed in `design/final/system.mmd`.

## Rendering

Rendered with Mermaid 11 (classDiagram), theme `base`, font 18, cream fill
(`#fdf6e3`), ochre border (`#b58900`), 2x device scale, then white margins trimmed.
The `*.mmd` files are the source of truth; edit those and re-render rather than
editing PNGs.

## Conventions

- Solid arrows: creation/ownership or call; dashed arrows: dependency; hollow
  triangle: realization/inheritance.
- Method lists are abbreviated with `...` for parameters that do not affect the
  figure's message; data classes show representative fields only.
- Role names follow the code (`ADMIN`, `MANAGER`, `CLERK`).
