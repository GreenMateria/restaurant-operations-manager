# StoreOps Manager Compensation Pitch

## 1. Title

StoreOps Manager

A custom operations platform built to reduce manual restaurant administration, improve reporting accuracy, and make repeatable manager workflows easier to execute.

Prepared for a compensation discussion.

Release target: v4.2.0.

## 2. Why It Was Built

Daily restaurant operations rely on many small administrative workflows that are easy to underestimate: invoices, counts, ordering, sales, labour, production planning, backups, and reporting.

Before this app, those workflows were spread across manual spreadsheets, paper count sheets, repeated calculations, local files, and manager memory. That created duplicated effort, inconsistent results, and avoidable risk.

StoreOps Manager was built because the work already existed. The app turns that repeated work into a structured system.

## 3. What It Does

StoreOps Manager supports the main back-office workflows managers use to control cost and prepare operations:

- Product and supplier management
- Invoice importing, manual invoice entry, reconciliation, and purchase history
- Inventory count templates, count entry, printed count sheets, valuation, and order guides
- Food, alcohol, and supplies workflows
- Weekly cost reporting from inventory, purchases, and sales
- POS sales import and reporting-period sales entry
- Production setup, POS usage import, weekly prep planning, and freezer pull planning
- Labour setup, labour hours, daily labour cost, tip pool, and tip pool breakdown
- Cloud API mode, Store Login, location-scoped data, backup, restore, and update flows

## 4. What It Replaces

The app replaces or consolidates work that would otherwise be handled through:

- Spreadsheet inventory templates and manual count math
- Hand-built order guides
- Manual invoice re-entry and invoice total reconciliation
- Repeated product cost lookups and purchase-history tracking
- Manual weekly food/alcohol/supplies cost calculations
- Manual prep sheets built from POS usage
- Separate labour hour sheets, tip pool calculations, and payout breakdowns
- Informal local backups and administrator-only recovery steps
- Direct database access from workstations

## 5. Business Value

The value is not only that the app exists. The value is that it standardizes work that affects cost control, ordering, reporting, and manager time.

Expected benefits:

- Less time spent rebuilding the same spreadsheets and reports
- Fewer manual formula and transcription errors
- Faster invoice and inventory workflows
- More consistent order guides and count sheets
- Better visibility into food, alcohol, supplies, production, and labour costs
- Better continuity when different managers perform the same task
- Safer cloud-backed data access through API mode and Store Login
- A stronger foundation for multi-location rollout

## 6. Technical Scope

This is not a simple spreadsheet macro. It is a maintained desktop operations application with a real data model and cloud-backed architecture.

Scope delivered:

- JavaFX desktop application
- SQLite local backup/snapshot support
- AWS RDS PostgreSQL backend behind an API layer
- Store Login and location session tokens
- Location-scoped reads and writes for store-owned workflows
- Schema migrations through the current production data model
- Release packaging, installer generation, update checks, and checksum support
- Automated tests for core service logic
- Manager-facing UI polish for startup, login, dialogs, backups, and workflow windows

## 7. Compensation Framing

This work produced business software that replaces ongoing manual administration and creates operational value beyond normal shift responsibilities.

A fair compensation conversation can be framed around three points:

1. The app was built to solve real operational problems, not as a hobby exercise.
2. It saves manager time and reduces risk in workflows tied directly to cost control.
3. Continued use creates ongoing maintenance, support, training, and enhancement responsibility.

Possible compensation structures:

- One-time project bonus for the completed build
- Hourly or salary adjustment recognizing technical responsibility
- Monthly maintenance stipend for support, updates, backups, and fixes
- Defined project rate for future modules or multi-location rollout

## 8. Suggested Pitch

"I built StoreOps Manager because several recurring back-office workflows were being handled manually across spreadsheets, paper, and repeated calculations. The app now centralizes inventory, invoice handling, order guides, cost reporting, production planning, labour hours, daily labour cost, tip pool, backups, updates, and cloud-backed store login.

This has value because it saves manager time, improves consistency, reduces manual errors, and gives the business a more reliable operational system. I would like to discuss compensation for the completed work, and also how we should handle ongoing maintenance and future development if the company wants to keep using it."

## 9. Discussion Ask

Recommended ask:

- Recognize the completed app as a business-value project.
- Agree on compensation for the current version.
- Define ownership and expectations for future support.
- Decide whether future development is handled as paid project work, a role responsibility with adjusted compensation, or a separate maintenance arrangement.

## 10. Closing Position

StoreOps Manager is already doing work that would otherwise require many manual tools and repeated manager hours.

The compensation request is not only for code. It is for identifying an operational gap, designing the workflow, building the system, testing it, deploying it, and continuing to support something the business can rely on.
