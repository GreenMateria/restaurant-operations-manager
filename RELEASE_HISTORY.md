# Release History

## v2.1.6 (current codebase state)

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
