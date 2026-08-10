# Release History

## v3.0.2 (current codebase state)

### Cloud Performance

- Batched inventory count line quantity updates into one transaction for Save Quantities and Complete Count.
- Moved inventory count save/complete work to a background JavaFX task so cloud writes do not freeze the app window.
- Moved Sales report import and Sales Period save work to background tasks.
- Moved Weekly Production usage report import/generation to a background task.
- Batch-loaded active production profile lines during Weekly Production report generation to reduce repeated cloud database round trips.

### Sales Import

- Updated POS Sales import to read fixed sales amount columns instead of guessing from the next currency value in the row.
- Gross sales now imports from zero-based column index `1`.
- Net sales now imports from zero-based column index `3`.

## v3.0.1

### Cloud Database / Sync

- Completed the current work-PC cloud database rollout using Aiven PostgreSQL `defaultdb`.
- Confirmed all current work PCs can connect to the cloud database without error.
- Cloud PostgreSQL is now the normal operating mode for configured work PCs.
- The cloud database is now treated as the accurate master data source.
- SQLite remains the safe default for fresh installs and fallback use.
- Upload This PC to Cloud and Download Cloud to This PC remain administrator migration/recovery tools, not daily sync actions.

### Sales / Reporting Schema

- Added schema migration 14 for net-sales fields on `sales_periods`.
- Current database schema version is 14.

## v3.0.0

### Cloud Database / Sync

- Added controlled hybrid database support with SQLite as the default mode and optional Aiven PostgreSQL cloud mode.
- Added local runtime database configuration at `%LOCALAPPDATA%\FoodInventory\database.properties`, seeded from safe bundled defaults on fresh installs.
- Added password-protected System module controls for testing cloud connection, switching next startup mode, uploading this PC to cloud, and downloading cloud to this PC.
- Added guarded two-way sync through `DatabaseSyncService`; cloud downloads back up the local SQLite database before replacement.
- Added PostgreSQL schema initialization/validation, lower-access app-user runtime support, and an internal PostgreSQL connection pool.
- Confirmed local SQLite upload to Aiven, cloud download back to SQLite, broad DAO/service smoke tests, and manual JavaFX UI testing.

### Performance / Reporting

- Improved Cloud PostgreSQL performance for Weekly Production, Freezer Pull, and Inventory Valuation by batching data loads, adding PostgreSQL indexes, and moving long UI loads to background tasks.
- Fixed Weekly Cost Report department filtering so food and alcohol reports use the selected count department instead of mixing all reporting categories.

### Order Guide

- Added schema migration 13 for `inventory_count_template_lines.order_guide_case_size`.
- Made the Order Guide `Case` column editable and persistent per count template line, while keeping product pack size as the fallback when no override is saved.

## v2.1.6

### Update / Installer

- Added a stable Windows `jpackage --win-upgrade-uuid` to release builds so future installers are linked as upgrades of the same installed application.
- Confirmed the downloaded installer cache is cleaned before each new update download under `%LOCALAPPDATA%\FoodInventory\Updates`.
- Documented that the v2.1.5-to-v2.1.6 update-ready prompt may still appear blank because that dialog is rendered by the installed v2.1.5 updater code; pressing Enter starts the default install action.

## v2.1.5

### Production / Freezer Pull

- Reworked Freezer Pull to load active Freezer Pull production items without a POS usage report.
- Added editable Monday-through-Sunday pull quantities directly in the Freezer Pull table.
- Added persistence for each item seven daily Freezer Pull quantities.
- Recalculated weekly totals when daily quantities are edited.
- Updated Freezer Pull printing for landscape letter output with compact single-page scaling.
- Confirmed Freezer Pull is working as intended and should not be changed unless explicitly requested.
- Confirmed all implemented features through this point are working as intended.
- Fixed the update-ready installer prompt so it appears after the download dialog closes instead of opening as a blank white dialog.

## v2.1.1 (previous codebase state)

### Production

-   Added production item yield factors for automated prep-yield conversion in Weekly Production
-   Added schema migration 12 for `production_items.yield_factor`

### Inventory / Invoice Handling

-   Added invoice subtotal allocation fields on `invoices`
-   Added `invoice_adjustments` for HST, freight, deposits, and other non-inventory charges
-   Preserved non-inventory adjustments outside inventory valuation
-   Fixed manual split-cost alcohol purchases so valuation fallback cost is derived from the actual each cost instead of being reset to zero
-   Added an alcohol-specific manual invoice workflow
-   Added line-level `HST Included` / `Bottle Deposit Included` handling for alcohol paper invoice totals
-   Added exact paper HST / bottle deposit entry for alcohol invoices
-   Added merchandise-category reconciliation so invoice balancing does not distort saved HST
-   Extended Invoice History breakdown to show saved invoice adjustments such as HST and Bottle Deposit

### Project State Notes

-   Current schema version in code is 12
-   Runtime database migrations auto-upgrade older databases on startup
-   `mvn clean test` passes on the current working tree

## v2.1.0

### Update System

-   Added in-app installer download
-   Added download progress dialog
-   Added update-ready prompt and installer launch flow
-   Retained GitHub release lookup and startup background update checks

## v2.0.6

### Update System

-   Added automatic GitHub update checking
-   Added application version display
-   Added background update service
-   Added update notification dialog
-   Added direct link to latest GitHub installer

### Planned

-   Future in-app installer download (no browser required)
