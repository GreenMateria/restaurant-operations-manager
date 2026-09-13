# PROJECT_REFERENCE.md

_Last Updated: Friday, September 11, 2026_

This document is the primary reference for the **StoreOps Manager** project.

Read this first when starting a new development session, using Codex in IntelliJ, or making changes that affect multiple modules.

---

# 1. Project Overview

**Application name:** StoreOps Manager  
**Previous name:** Food Inventory Manager  
**Primary package:** `ca.foodinventory`

The application is a Windows desktop operations system for a restaurant. It is designed around the actual weekly workflows used by restaurant managers, including:

- Product and supplier management
- Invoice importing and reconciliation
- Inventory count templates
- Inventory count entry
- Inventory valuation
- Weekly cost reporting
- Order guide generation
- Alcohol inventory
- Weekly production and prep planning
- Database backup and restore
- Automated packaging and release publishing
- Automatic in-application update checks and installation

Printed count sheets, prep sheets, and order guides are core parts of the workflow.

---

# 2. Technology Stack

- Java 25
- JavaFX 25
- Maven
- SQLite
- PostgreSQL on AWS RDS behind the Cloud API
- Apache POI for Excel imports
- Git and GitHub
- GitHub Releases
- WiX 7
- `jpackage`
- IntelliJ IDEA Community Edition
- PowerShell release automation

The application uses hand-built JavaFX layouts rather than FXML.

---

# 3. Project Location and Runtime Data

Typical project root:

```text
C:\Food Inventory
```

Packaged runtime database:

```text
%LOCALAPPDATA%\FoodInventory\food_inventory.db
```

Packaged runtime database configuration:

```text
%LOCALAPPDATA%\FoodInventory\database.properties
```

Update downloads:

```text
%LOCALAPPDATA%\FoodInventory\Updates
```

Important:

- A `food_inventory.db` file in the project directory may not be the active database.
- The packaged application uses the database under `%LOCALAPPDATA%`.
- Cloud API mode is the normal desktop startup mode.
- SQLite is retained as a local cloud-snapshot and backup/recovery file, not as a daily operating mode.
- Fresh installs seed `database.properties` from safe bundled defaults when the local config file does not exist; source-controlled defaults must keep credentials blank.
- Release-built installers can include an ignored generated `database-release.properties` resource that configures API mode once per release from secret values supplied at packaging time.
- For the current work PCs, Cloud API mode is the normal operating mode, AWS RDS PostgreSQL remains the backend data store, and the cloud database is the accurate master data source.
- Direct desktop Cloud PostgreSQL mode has been retired from normal desktop use. PostgreSQL credentials should not be needed on client PCs.
- Cloud snapshot download is an administrator backup/recovery tool, not routine daily sync.
- Never delete, replace, or reset the runtime database unless explicitly requested.
- Database schema changes must be made through migrations.
- Current development version is v4.0.0; latest stable release is v3.1.2.
- Current database schema version in code is 21.
- API layer stack `esm-operations-api` is deployed in AWS and the desktop app uses API mode for normal workflows.
- Store Login is enabled by release packaging with `location.login.required=true`.
- Desktop API clients include the store session token with normal business requests, and the deployed API scopes store-owned reads/writes to the resolved location.
- Current API-mode migrated areas are Food Department workflows, Alcohol Department workflows, Supplies Department workflows, Production workflows, Reporting/Sales workflows, Labour Setup, Labour Hours, Daily Labour Cost, Tip Pool, Tip Pool Breakdown, GFS product guide import persistence, product purchase history, and administrative cloud snapshot download.
- Current API-mode gaps are deferred variance reporting and local backup/export polish.
- v3.1.1 completed the desktop API migration so normal users no longer need to configure direct client database connections.
- v3.1.1 added a database status indicator for clearer runtime mode/connection visibility.

---

# 4. Application Architecture

The desktop application follows this practical layered structure:

```text
UI View
   ↓
Service / Business Logic
   ↓
API Client
   ↓
HTTPS API
   ↓
AWS RDS PostgreSQL
```

Main packages:

```text
ca.foodinventory
ca.foodinventory.ui
ca.foodinventory.dao
ca.foodinventory.model
ca.foodinventory.service
ca.foodinventory.database
```

Responsibilities:

## UI

JavaFX screens, dialogs, tables, navigation, preview, and printing.

## DAO

SQL queries and persistence for a specific model area.

## Model

Plain Java objects used by DAOs, services, and JavaFX tables.

## Service

Business logic, imports, reports, updater logic, calculations, and file operations.

## Database

Database startup, schema migrations, migration runner, and connection management.

---

# 5. Application Startup

The main application class is:

```text
ca.foodinventory.MainApp
```

The packaged launcher is:

```text
ca.foodinventory.Launcher
```

At startup, the application:

1. Loads or creates the local database configuration.
2. Applies release API configuration when bundled.
3. Starts in Cloud API mode for normal operation.
4. Shows Store Login when API mode is active and `location.login.required=true`.
5. Loads the main dashboard.
6. Displays the installed application version.
7. Starts a background GitHub update check.
8. Shows an update dialog when a newer release is available.

The startup update check does not block the main UI.

---

# 6. Main Modules

The dashboard is organized around these operational areas:

- Food Department
- Alcohol Department
- Supplies Department
- Purchasing
- Reports
- Sales
- Production
- Labour Management
- System

The Labour Management module is complete and accepted as working as intended in the v4.0.0 development build. It includes an administrator-protected Labour Setup screen, Labour Hours, Daily Labour Cost, Tip Pool, and Tip Pool Breakdown. Labour Hours uses an inventory-count-style list of saved weeks and opens a separate spreadsheet-style pop-out window for manual Shift 1 / Shift 2 hour entry. Daily Labour Cost uses a weekly start/end date range with Monday-Sunday rows, editable daily net sales, BOH labour dollars, FOH labour dollars, labour percentages, and a total row. Tip Pool uses editable daily tip-out pool amounts, tip-pool eligible employee hours from Labour Hours, calculated employee tip allocation by day, and weekly totals. Tip Pool Breakdown allocates saved tip pool amounts over a selected date range, subtracts configured uniform deductions per worked day for applicable employees only, rounds net payouts to the nearest nickel, and supports compact black-and-white printing.

All module views should remain reachable through the main navigation/back controls. Do not add redundant in-section return buttons where the left navigation already provides the route.

---

# 7. Product Management

Products are the central records used by purchasing, inventory, valuation, order guides, production mapping, and reporting.

Important product data includes:

- SKU
- Description
- Category
- Reporting category
- Base unit
- Conversion factor
- Pack size
- Pack count
- Last case cost
- Last purchased date
- Active status

Reporting categories currently include:

```text
FOOD
BEER
WINE
DRAUGHT
IMPORT DRAUGHT
LIQUOR
PAPER
TAKE OUT
CLEANING
DISHWASHING
GUEST SUPPLIES
OTHER
```

Department grouping:

## Food

```text
FOOD
```

## Alcohol

```text
BEER
WINE
DRAUGHT
IMPORT DRAUGHT
LIQUOR
```

## Supplies

```text
PAPER
TAKE OUT
CLEANING
DISHWASHING
GUEST SUPPLIES
OTHER
```

Office supplies are intended to be tracked as an ordering category but are generally not included in physical inventory counts.

Products can also have supplier SKU aliases so different supplier identifiers can map to one product.

---

# 8. Purchasing and Invoice Workflow

The purchasing module supports:

- GFS invoice import
- Manual invoice entry
- Invoice preview and editing
- Add manual line
- Edit selected line
- Remove selected line
- Unknown SKU handling
- SKU alias mapping
- Duplicate invoice detection
- Duplicate invoice overwrite
- Invoice history
- Invoice deletion
- Category breakdown

Operational workflow:

```text
Import Invoice
→ Review Imported Lines
→ Add or Edit Missing Lines
→ Reconcile to Paper Invoice
→ Save Invoice
→ Update Product Costs and Purchase History
```

Important behavior:

- Duplicate SKU lines on one invoice must be supported.
- Duplicate invoices are detected by invoice number.
- Imported totals should reconcile to the supplier invoice.
- Food and supplies use the generic manual invoice workflow.
- Alcohol uses a dedicated manual invoice workflow.
- Alcohol invoices may require deposits, HST, freight, or other non-inventory charges to be excluded from inventory merchandise cost.
- Non-inventory alcohol charges are stored as invoice adjustments and should not flow into inventory valuation.
- The alcohol manual invoice product picker includes alcohol reporting categories plus `FOOD` items, because some non-alcohol beverages are purchased from alcohol suppliers but allocated to food cost.
- The alcohol manual invoice screen supports line-level `HST Included` and `Bottle Deposit Included` logic for stripping non-inventory amounts out of paper line totals.
- The alcohol manual invoice screen also accepts exact paper-invoice HST and exact paper bottle deposit totals for accounting / remittance purposes.
- Any remaining difference between calculated merchandise + exact paper adjustments and the paper invoice total is reconciled into merchandise categories, not into HST.
- Positive differences are subtracted from the highest merchandise category; negative differences are added to the lowest merchandise category.
- When a manual alcohol purchase is entered using split / each cost, fallback valuation cost should derive from the actual each cost and the product conversion factor rather than zeroing the product fallback cost.
- Product costs from purchasing feed inventory valuation and reports.

---

# 9. Inventory Count Templates

Inventory count templates define which products appear on count sheets and how they are organized.

A template line contains information such as:

- Product
- Section name
- Sort order
- Count unit
- Conversion factor to base
- Display name
- Active status

Templates control:

- Count entry layout
- Printed count sheet layout
- Product order
- Section grouping
- Order guide layout

Templates are department-specific:

- Food
- Alcohol
- Supplies

The template editor supports:

- Add product
- Remove product
- Edit product
- Move up
- Move down
- Section organization
- Duplicate template
- Delete protection when a template is already in use

---

# 10. Inventory Count Workflow

Normal workflow:

```text
Select Count Template
→ Start Inventory Count
→ Print Count Sheet
→ Perform Physical Count
→ Enter Quantities
→ Review Count
→ Mark Complete
→ Use in Valuation and Reporting
```

Inventory counts store:

- Template used
- Count date
- Period start date
- Period end date
- Notes
- Completed status
- Individual count lines
- Converted quantities in product base units

Important design rule:

When a count is started, its count lines are created from the template at that time.

Changing the template afterward does not automatically add new items to the already-started count.

Current workaround for a missing template item:

1. Delete the incomplete count.
2. Add the missing product to the template.
3. Start the count again.
4. Re-enter the quantities.

Planned enhancement:

```text
Synchronize with Template
```

This future action should:

- Add products now present in the template but missing from the count.
- Preserve all existing quantities.
- Never delete existing count lines.
- Never overwrite entered quantities.
- Work only as an additive synchronization.

Completed counts must remain historical snapshots and should not be modified by later template changes.

---

# 11. Inventory Valuation

Inventory valuation converts counted quantities into monetary value.

Counted quantities may be entered in different units and converted to the product base unit.

Valuation uses product cost history and purchase data.

Current fallback behavior:

1. Use period purchase totals where period purchase quantity exists.
2. Otherwise use the product's last known purchased-unit fallback cost.
3. For split-cost manual alcohol purchases, derive that fallback from each cost and the product conversion factor instead of overwriting it with zero.
4. Exact paper-invoice HST and deposit should remain accounting values, not valuation fallback values.

Inventory valuation supports category and department reporting.

---

# 12. Weekly Cost Reporting

Core formula:

```text
Usage = Opening Inventory + Purchases - Closing Inventory
Cost % = Usage / Sales
```

The report uses:

- Opening inventory count
- Closing inventory count
- Purchases between the selected period dates
- Sales for the matching period

Important date rule:

Purchases must be limited to the closing count's:

```text
period_end_date
```

Do not include purchases received after the reporting period, even when the physical closing count was entered later.

Reporting behavior:

- Food usage is compared to food sales.
- Alcohol categories are compared to their matching alcohol sales category.
- Supplies categories are generally compared to total revenue.
- Multi-week periods are supported.
- Opening and closing dates are flexible.

---

# 13. Order Guide

The order guide is generated from the current inventory cycle and template structure.

It includes:

- Product description
- Opening quantity
- Purchases
- Closing quantity
- Order 1
- Order 2

Printing rules:

- Hide SKU from the printable version.
- Preserve section names and sort order.
- Include zero-count items where required.
- Use a compact black-and-white layout.
- Avoid clipping the right side of the page.
- Include products in the `OTHER` section.
- Keep product columns narrow enough to fit all ordering columns.
- Inventory Count Sheet printing should create the print page layout after the user selects the printer and should scale printable pages to the selected printer's printable area.
- Alcohol count sheet printing must use API-backed alcohol product profile loading when the desktop is in Cloud API mode.

The restaurant normally places two weekly orders, so both order columns are required.

---

# 14. Sales Data

Sales periods store category totals for a selected period.

Schema version 14 also stores matching net-sales fields for each sales category.

Known sales fields include:

- Food sales
- Beer sales
- Wine sales
- Draught sales
- Import draught sales
- Liquor sales

Sales data is imported from POS Excel reports and used by cost reporting.

POS Sales import currently uses fixed Apache POI zero-based column indexes in `PosSalesImportService`:

- Gross sales column index `1`
- Net sales column index `3`

Do not infer net sales by scanning for the next currency value in the row.

Sales periods are selected by start and end date rather than assuming every reporting period is exactly one week.

---

# 15. Alcohol Inventory

Alcohol inventory uses different count methods depending on product type.

## Weighted Products

Used for:

- Liquor
- Wine
- Kegs

Manager enters:

- Full units
- Partial weight

The application calculates the decimal quantity using stored product profile values.

Conceptual formula:

```text
Decimal Quantity =
Full Units +
((Measured Weight - Tare Weight) / Full Content Weight)
```

Only the calculated decimal quantity needs to feed inventory valuation.

## Each-Count Products

Used for:

- Bottled beer
- Coolers
- Seltzers

Manager enters quantity directly.

## Alcohol Product Profiles

Alcohol product setup stores:

- Count method
- Measurement unit
- Container type where applicable
- Tare weight
- Full content weight
- Active status

The manager should not need to enter tare or full-content values during each inventory count. Those values belong in the product profile.

## Printing

Weighted products print columns for:

- Full
- Weight

Each-count products print:

- Quantity

Alcohol printouts should be grouped by:

- Beer
- Wine
- Liquor
- Draught
- Import Draught

Pagination should be section-aware and match the entry screen.

## Current Status

The alcohol workflow foundation exists, including:

- Alcohol product filtering in Products / Manual Invoice / Counts / Order Guide entry points
- Alcohol product profiles
- Alcohol count templates and count entry support
- Alcohol-specific manual invoice entry
- Alcohol manual invoice entry includes `FOOD` reporting-category products for non-alcohol beverages purchased from alcohol suppliers; this v3.0.4 fix is implemented and working as intended
- Alcohol manual invoice adjustments for non-inventory charges
- Exact paper HST and bottle deposit entry for accounting accuracy
- Automatic reconciliation of remaining invoice differences into merchandise categories
- Alcohol inclusion in valuation and weekly cost reporting
- Alcohol Department navigation now includes alcohol-specific Sales Mappings and Variance Report entry points so bar variance work does not overload the Production menu
- Alcohol Sales Mappings stores POS SKU/PLU to alcohol inventory product mappings with quantity used per sale and usage unit
- Alcohol Sales Mapping add/edit reuses active records from the existing Production POS Menu Items catalog, filling POS SKU/PLU and POS Item Name automatically while leaving manual fields editable as a fallback
- Alcohol Variance Report currently has the department entry point and setup shell; variance calculation/output is the next build step

Remaining priorities focus on future alcohol reporting, order guide, and printing polish when a specific live-data issue or workflow request appears:

1. Populate alcohol-only sales-to-inventory mappings from the POS Menu Items picker for variance reporting.
2. Build alcohol variance calculation/output before starting any food variance workflow.
3. Refine alcohol cost reporting behaviour only when live validation identifies a concrete issue.
4. Continue alcohol order guide workflow review as needed.
5. Finalize alcohol-specific printing and user workflow polish where needed.

---

# 16. Production Module

The production module generates weekly kitchen prep requirements from POS usage.

Primary workflow:

```text
Import POS Usage Report
→ Match POS Items to Production Profiles
→ Generate Weekly Production
→ Review Generated PAR Values
→ Apply Overrides
→ Save
→ Preview and Print Daily Prep Sheets
```

The module includes:

- Production stations
- Production items
- Production profiles
- POS menu items
- Product mappings
- Production weeks
- Weekly day tabs
- Generated PAR values
- Override PAR values
- Final PAR values
- Prep sheet assignment
- Freezer Pull

## Production Stations

Stations organize kitchen prep work and can be assigned to printable sheets such as:

```text
Main Line
Pizza Salad
Freezer Pull
```

## Production Items

Production items include:

- Name
- Unit
- Station
- Shelf life
- Yield factor
- Permanent override PAR
- Active status

Yield factor belongs to the production item because Weekly Production rows are production items. Use `1.0` for normal items. Use a higher value when prepared output is larger than input quantity, such as dry pasta becoming a larger cooked weight.

## Production Profiles

A production profile represents the production requirements generated by a sold POS item.

Example concept:

```text
Chicken Parmesan Sale
→ 1 Chicken Parm Portion
→ 1 Pasta Portion
→ 1 Sauce Portion
```

Weekly Production converts imported sales into generated quantities using:

```text
sales quantity * quantity per sale / production item yield factor
```

Then the selected par multiplier is applied. Existing production items use a yield factor of `1.0`, so only items that need yield correction have to be edited.

## POS Menu Items

POS menu items map POS SKUs or PLUs to production profiles.

Multiple POS SKUs may map into the same production requirement.

Alcohol Sales Mappings also reuse this POS Menu Items catalog as the selectable POS item source. This avoids maintaining a second POS item list while keeping alcohol inventory variance mappings separate from production profiles.

## Product Mappings

Production item product mappings connect production items to inventory products.

This is intended for future:

- Theoretical usage
- Actual-versus-theoretical variance
- Recipe costing
- Yield analysis

## Weekly Production

Weekly Production currently supports:

- Week selection
- POS Usage Report import
- Monday through Sunday tabs
- Include all active production items
- Generated PAR
- Override PAR editing
- Save Overrides
- Prep Sheet selector
- Preview
- Print

Cloud-mode usage report import/generation runs in a background task and production profile lines are batch-loaded to avoid repeated cloud round trips during report generation.

In API mode, Production setup, POS Menu Items, Weekly Production, product mappings, production report import/generation, and Freezer Pull are backed by the deployed API stack.

Generated production normally uses previous sales plus a buffer and rounds upward as required.

Permanent override PAR values can replace calculated values where a fixed production level is preferred.

## Prep Sheet Printing

Each prep sheet should:

- Fit on one page where practical.
- Use black-and-white printing.
- Use readable fonts.
- Preserve station headers.
- Avoid unnecessarily splitting Main Line across pages.

Columns include:

```text
ITEM
UNIT
LIFE
DAY
COUNT
TO DO
INITIAL
```

Do not reintroduce the previous hard-coded 32-row pagination rule unless explicitly requested.

## Freezer Pull

Freezer Pull:

- Loads active production items assigned to the Freezer Pull station.
- Does not require a POS usage report.
- Monday through Sunday quantities are edited directly in the table.
- Daily quantities persist between sessions.
- Weekly totals recalculate from the seven daily values.
- Prints independently in landscape letter format with compact single-page scaling.
- Includes items assigned to the Freezer Pull workflow.
- Prints independently from normal weekly production sheets.
- Uses the profile-specific pull unit where applicable.
- Current Freezer Pull behavior is accepted as working as intended. Do not modify this module unless a future task explicitly asks for a Freezer Pull change.

---

# 17. Labour Management

Labour Management is a top-level operational module replacing the accepted manager Excel workflow.

Implemented Labour Management work includes:

- Labour Management dashboard navigation.
- Administrator-protected Labour Setup.
- Labour Hours with an inventory-count-style saved-week list and a pop-out spreadsheet-style Monday-Sunday grid for separate Shift 1 / Shift 2 employee-hour entry.
- Labour Hours department headers are styled as distinct bands in the pop-out entry window.
- Configurable labour positions grouped as `FOH` or `BOH`.
- Configurable labour employees with position, hourly wage, active status, tip-pool eligibility, and uniform-deduction applicability.
- Configurable default uniform deduction amount stored in settings.
- API-backed Labour Setup, Labour Hours, Daily Labour Cost, Tip Pool, and Tip Pool Breakdown reads/writes for Cloud API mode.
- Live AWS API deployment includes `/labour/weeks`, `/labour/weekly`, and `/labour/daily` routes, and the live RDS schema has been advanced through schema version 21 for multi-location operation.
- Schema foundations for future daily operational sales and daily employee labour entries by actual calendar date.
- Wage, employee name, position name, and labour group snapshots on saved daily labour entries so historical weeks do not recalculate from later setup changes.

Daily Labour Cost, Tip Pool, and Tip Pool Breakdown are implemented and accepted as working as intended.

Daily Labour Cost UI notes:

- Date selection loads one actual calendar day.
- Manual `net_sales` and `tip_out_pool` values are stored in `labour_daily_sales`.
- Employee Shift 1 / Shift 2 hours are stored in the same `labour_daily_entries` rows used by Labour Hours.
- FOH, BOH, and total variable labour percentages use editable Daily Labour Cost net sales, not imported weekly POS sales periods.
- Daily Labour Cost is a weekly start/end range screen with Monday-Sunday rows, editable net sales, BOH labour dollars/percentages, FOH labour dollars/percentages, total labour percentage, and a total row.
- Target percentages come from Labour Setup positions and are summed once per configured position in each labour group.

Future Labour Management work should preserve the spreadsheet-like manager workflow: employees grouped by labour position, Monday through Sunday across the screen, separate Shift 1 and Shift 2 entry, daily totals, labour dollars/percentages, FOH/BOH/total variable labour groupings, tip-out allocation by eligible hours, and uniform deductions from configuration.

Labour Hours UI notes:

- The main Labour Hours screen lists saved weeks and opens a separate pop-out editor, following the Inventory Counts workflow pattern.
- The pop-out editor keeps the spreadsheet mental model while limiting columns to employee name, Monday-Sunday Shift 1 / Shift 2 hour entry, and employee total hours.
- Employee number, hourly rate, total pay, restaurant header, position total rows, and bottom labour total rows are intentionally hidden from the Labour Hours entry grid.

Tip Pool UI notes:

- Tip Pool uses editable daily tip-out pool amounts stored in `labour_daily_sales.tip_out_pool`.
- Employee rows include only Labour Setup employees marked tip-pool eligible.
- Daily tips are allocated by each eligible employee's hours divided by the total eligible hours for that day.
- The tip-out amount entry boxes sit under each day's Tip column so users do not confuse them with hour entry.

Tip Pool Breakdown UI notes:

- Tip Pool Breakdown uses a simple start/end date range.
- Gross tips are calculated from saved Tip Pool amounts and tip-eligible Labour Hours.
- Uniform deduction is subtracted per worked day for employees marked uniform-deduction applicable, using the default deduction configured in Labour Setup. Employees with no hours in the selected range are not charged.
- Net payout amounts are rounded to the nearest nickel for Canadian cash payout handling.
- Tip Pool Breakdown prints a compact black-and-white report with employee rows and totals.

- Week selection normalizes to Monday as the canonical week start.
- The visible entry grid uses compact `S1` and `S2` headers to reduce horizontal scrolling while preserving separate shift entry.
- Tab and Enter should move through editable shift cells in left-to-right, top-to-bottom order.
- Blank shift cells are treated as zero internally.
- Full-week saves use one API request in Cloud API mode rather than saving each cell while typing.

---

# 18. Printing Standards

Printed output is a core application feature.

All printable views should prioritize:

- Black-and-white compatibility
- Clear section headers
- Readable font sizes
- Compact spacing
- Stable page sizing
- No clipped columns
- No missing final sections
- One logical sheet per page where practical
- Minimal decorative styling

Before adding complex transforms, first adjust:

1. Orientation
2. Margins
3. Column widths
4. Font size
5. Row height
6. Padding
7. Page breaks

Print preview should match the actual printed content as closely as possible.

---

# 19. Backup and Restore

The System module includes database backup and restore.

Backup and restore operate against the local SQLite snapshot file.

Important rules:

- Confirm the active database path before troubleshooting.
- Do not treat the project-root database as the packaged database.
- In API mode, creating a backup first downloads a fresh cloud snapshot into the local SQLite file, then copies that file to the selected backup location.
- Restore actions should be protected and clearly confirmed.
- Restore is local snapshot restore only; replacing production cloud data from a local file should remain outside the normal UI.
- Avoid restoring a database over a newer schema without migration support.

---

# 20. Admin Password and Settings

The application uses a local administrator password for protected system functions.

Current behavior:

- The administrator password is stored locally in `%LOCALAPPDATA%\FoodInventory\database.properties`.
- Passwords are stored as salted PBKDF2 hashes, not plain text.
- Legacy plain-text local or SQLite passwords are migrated to hashed local storage after a successful login.
- `scripts/Reset-AdminPassword.ps1` can reset the local administrator password when the System screen cannot be accessed.
- This password protects local app administration. It is not the AWS API key, database password, or a per-user cloud identity.

Passwords must not be stored in plain text.

---

# 21. GitHub Update System

The updater is implemented through:

```text
GitHubUpdateService
```

GitHub repository:

```text
GreenMateria/restaurant-operations-manager
```

The updater:

- Checks the latest published GitHub Release.
- Compares installed and latest versions.
- Runs in the background.
- Shows release notes.
- Shows current version.
- Shows new version.
- Shows download size.
- Shows whether a published checksum is available for the installer.
- Downloads the installer inside the application.
- Displays download progress.
- Verifies the downloaded installer against the published SHA-256 checksum before launch when a matching checksum asset is available.
- Stores the installer under the local Updates folder.
- Prompts for download and install actions.
- Launches the Windows installer.
- Closes the application after installer launch.
- Deletes older downloaded `.exe` installers from `%LOCALAPPDATA%\FoodInventory\Updates` before saving a new installer.

The update check uses a 12-hour cooldown.

The last successful check timestamp is stored with Java Preferences, not SQLite.

This is intentional because update state must remain:

- Per machine
- Per Windows user
- Independent of shared or restored databases

A manual update check should call:

```java
updateService.checkForUpdate(true);
```

This bypasses the cooldown.

Manual update checking is available from:

```text
System → Check for Updates
```

Updater dialogs must explicitly size their content and buttons so stylesheet behavior cannot produce blank white dialogs.

The update-ready installer prompt must be shown only after the download progress dialog has fully closed. Do not open a second modal dialog from inside the download dialog close action; store the completed installer path, let `showAndWait()` return, then show the ready prompt.

Important bootstrap caveat:

- Updater dialog fixes only affect updates started from the version that contains the fix.
- When installed v2.1.5 updates to v2.1.6, the final `Update Ready` prompt is still rendered by v2.1.5 code and may appear blank.
- In that specific v2.1.5-to-v2.1.6 case, pressing Enter activates the default install action.

---

# 22. Versioning and Releases

The Maven project version is the application version source.

Release workflow is automated through:

```text
Release.ps1
```

Typical release process:

1. Set `FOOD_INVENTORY_RELEASE_API_KEY` in the local release shell or make sure `%TEMP%\esm-api-key.txt` contains the current API key.
2. Enter version.
3. Enter release notes.
4. Update project version.
5. Commit and push source changes before generating release credentials.
6. Generate ignored `database-release.properties` for the installer build.
7. Run Maven build.
8. Build Windows installer with `jpackage`.
9. Remove the generated release config from the source tree.
10. Create Git tag.
11. Push tag and publish installer to GitHub Releases.
12. Copy installer to the local Releases folder.
13. Generate and upload the installer `.sha256` checksum asset.

Installer format:

```text
StoreOps Manager-<version>.exe
```

Important:

- GitHub update checks only see published releases.
- Draft releases are not treated as the latest public release.
- Version strings must compare cleanly.
- The installed version should match the Maven/release version.
- `Release.ps1` must always pass the same stable `--win-upgrade-uuid` value to `jpackage`.
- Do not change the Windows upgrade UUID after release; changing it can make Windows treat a future installer as a different product instead of an upgrade.
- The current Windows upgrade UUID is `8F7E5D76-9E8B-4C25-8B8E-55A94D4E0B0A`.
- Program data is stored separately under `%LOCALAPPDATA%\FoodInventory` and should not be removed by normal app upgrades.

---

# 23. Current Project State

The application currently has a stable foundation for:

- Product management
- Purchasing
- Invoice importing and reconciliation
- Inventory templates
- Food inventory counting
- Inventory valuation
- Cost reporting
- Sales periods
- Order guides
- Production setup
- Weekly production generation
- Prep sheet printing
- Freezer Pull
- Backup and restore
- Release automation
- Automatic updating
- Installer checksum generation, GitHub Release checksum upload, and in-app checksum verification before installer launch
- Cloud API operating mode with local SQLite backup snapshot support
- Password-protected cloud upload/download tools
- Deployed API Gateway/Lambda stack for API mode
- API-backed Food Department workflows
- API-backed Alcohol Department workflows, including alcohol profile maintenance
- API-backed Supplies Department workflows
- API-backed Production workflows, including POS Menu Items, Weekly Production, production report import/generation, Product Mappings, and Freezer Pull
- API-backed Reporting/Sales workflows, including Invoice History, Sales Entry/import persistence, Inventory Valuation, and Weekly Cost Report generation
- API-backed Labour Setup for labour positions, employees, and default uniform deduction configuration
- API-backed Labour Hours for Monday-Sunday Shift 1 / Shift 2 employee hours
- API-backed Daily Labour Cost, Tip Pool, and Tip Pool Breakdown for weekly net sales, daily tip-out pools, labour percentages, tip allocation, uniform deductions, nickel-rounded net payouts, and payout-report printing
- Fixed Inventory Count Sheet printing for selected printer layout/scaling and API-mode alcohol profile loading
- API-backed GFS product guide import persistence and product purchase history lookup
- API-backed administrative upload/download sync for cloud replacement and local recovery workflows
- Persistent Order Guide case-size overrides
- Complete Labour Management module: top-level navigation, administrator-protected Labour Setup, Labour Hours, Daily Labour Cost, Tip Pool, Tip Pool Breakdown, schema/API/model foundation, and shared labour daily records
- Multi-location rollout implementation includes location credentials, `locations`, `location_sessions`, API `POST /auth/login`, development-machine credential scripts, release-enabled desktop Store Login, desktop store-session request headers, Labour `location_id` scoping, store-owned operational `location_id` columns, location-scoped API reads/writes for the main business workflows, PostgreSQL location-aware uniqueness for duplicate-sensitive store data, and admin setup-copy tooling for products/templates/production setup between stores
- Background cloud-mode save/import handling for inventory count saves/completions, sales report imports/saves, and Weekly Production usage imports
- Normal API workflows for Food, Alcohol, Supplies, Production, Reporting/Sales, product import, and product purchase history are confirmed working on the latest stable version

All implemented features through version 4.0.0 are considered working as intended unless a future issue is reported with a specific workflow, error, or data case. Labour Management and multi-location Store Login/location scoping are accepted as complete in the v4.0.0 development build; remaining work is focused on post-release fixes and future enhancements.

Primary unfinished areas:

- Food production-to-inventory variance later, after alcohol variance is working
- Recipe costing
- Yield tracking
- Advanced reporting
- Inventory count template synchronization

---

# 24. Development Priorities

## Immediate

1. Continue monitoring daily Cloud API use on the configured work PCs.
2. Build alcohol variance calculation/output inside the Alcohol Department after operational workflows are stable.
3. Continue advanced reporting and workflow polish.

## Medium Term

1. Finish alcohol sales-to-inventory mappings.
2. Build alcohol theoretical usage calculations.
3. Build alcohol variance reporting.
4. Resume food production item to inventory product mappings after alcohol variance is working.
5. Add recipe and yield management.

## Long Term

1. Recipe costing.
2. Theoretical food cost.
3. Actual-versus-theoretical variance.
4. Historical trend reporting.
5. Multi-location support if ever required.
6. Broader role and permission controls.

---

# 25. Important Design Principles

## Preserve Historical Data

Completed counts, invoices, and production weeks should remain historical records.

Do not allow later template or product changes to silently rewrite completed history.

## Prefer Additive Changes

When synchronizing data:

- Add missing records.
- Preserve existing user-entered data.
- Avoid automatic deletion.
- Avoid overwriting manual overrides.

## Match Real Restaurant Workflow

The application should support the way managers actually work:

- Printed count sheets
- Printed prep sheets
- Manual reconciliation
- Flexible date ranges
- Missing-item recovery
- Two weekly orders
- Multiple managers
- Real-world exceptions

## Protect User Data

Never:

- Reset the database casually.
- Delete counts without confirmation.
- Remove invoice history unexpectedly.
- Overwrite entered values without warning.
- Assume a development database is the active runtime database.

## Keep Changes Targeted

Prefer focused changes to working modules.

Broad rewrites should only be used when there is a clear reason and the existing behavior is understood.

---

# 26. Build and Verification

After Java changes:

```bash
mvn clean test
```

Also manually verify relevant workflows:

- Application launches.
- Database migration succeeds.
- View opens.
- Data loads.
- Save works.
- Import works.
- Preview works.
- Print works.
- Update dialog works.
- Installer launches when updater changes are involved.

For packaging:

- Close running application processes.
- Ensure no installer file is locked.
- Confirm the icon is included.
- Confirm the bundled runtime launches on a clean machine.
- Confirm GitHub release assets contain the installer.
- Confirm installer upgrades replace the existing Windows install entry and do not create duplicate installed apps.
- Confirm app data under `%LOCALAPPDATA%\FoodInventory` is preserved across upgrades.

---

# 27. New Session Instructions

When starting a new AI or Codex session, provide this instruction:

```text
Read PROJECT_REFERENCE.md, PROJECT_STATUS.md, DATABASE_SCHEMA.md,
CODING_STANDARDS.md, INFRASTRUCTURE.md, ROADMAP.md, and RELEASE_HISTORY.md.

Treat PROJECT_REFERENCE.md as the master functional overview.
Inspect the exact Java files involved before changing code.
Preserve the existing JavaFX, Cloud API, AWS RDS PostgreSQL backend,
local SQLite snapshot, Maven, migration, printing, packaging, and
GitHub update architecture.
Make targeted changes and run mvn clean test after Java edits.
```

For a focused task, also include:

- The exact feature being changed
- Current observed behavior
- Desired behavior
- Relevant files
- Any compiler or runtime error
- Screenshots or printed PDFs where layout is involved

---

# 28. Document Set

This project documentation set should include:

```text
PROJECT_REFERENCE.md
PROJECT_STATUS.md
DATABASE_SCHEMA.md
CODING_STANDARDS.md
INFRASTRUCTURE.md
ROADMAP.md
RELEASE_HISTORY.md
```

Purpose of each file:

## PROJECT_REFERENCE.md

Master functional and architectural overview.

## PROJECT_STATUS.md

Current version, development state, completed work, active work, and known issues.

## DATABASE_SCHEMA.md

Current database tables, fields, migrations, and reporting relationships.

## CODING_STANDARDS.md

Implementation conventions and project-specific development rules.

## INFRASTRUCTURE.md

Current AWS infrastructure baseline, what is managed by IaC, what is still manual, budget guardrails, and recovery-oriented deployment notes.

## ROADMAP.md

Prioritized future work.

## RELEASE_HISTORY.md

Version-by-version changelog.

Keep all files synchronized when major modules, migrations, workflows, or releases change.
