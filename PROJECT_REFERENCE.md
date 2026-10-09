# StoreOps Manager — Project Reference

_Last Updated: Friday, October 9, 2026_

This document is the primary reference for the **StoreOps Manager** project.

Read this first when starting a new development session, using Codex in IntelliJ, or making changes that affect multiple modules.


## Current snapshot and contents

This is the single current technical reference. It combines the former status, schema, coding standards, infrastructure, API, cloud-transition, and roadmap documentation. Update this file when behavior, schema, deployment, or priorities change. Read the implementation before making code changes; archived notes are historical, not current instructions.

- **Desktop source version:** 4.1.5 in `pom.xml` at consolidation. The older v4.2.0 references below describe the planned release scope, not proof that v4.2.0 has been published.
- **API source version:** use `api/pom.xml`; desktop and API versions may differ.
- **Schema:** 23. Normal desktop use is Cloud API with store-scoped sessions; SQLite is a snapshot/recovery and development path.
- **Latest accepted change (October 8, 2026):** finalized GFS LineItemList import, manual invoice number/date/shipping/HST/grand-total reconciliation, actual billed weighted costs, and product-based quantity conversion. The manager confirmed correct operation; 27 desktop tests passed. Actual delivered weight is absent from the export, so variable-weight base quantities remain approximate.
- **Other current work:** zero-sales mapped wine category correction, admin launcher, and adopted AWS infrastructure with Secrets Manager and seven-day RDS backup retention. See the functional sections and release history for details.
- **Deployment evidence:** infrastructure state was verified October 4, 2026; this documentation consolidation did not query or change live AWS resources.
- **Latest Labour fix (October 9, 2026):** Save Net Sales and Save Tip Pool now update only their respective amount, preserving other sales fields, finalization, and saved hours. All 28 desktop tests and 11 API tests passed, including a stale-screen regression. The operator deployed through Admin Tools; AWS confirmed `esm-operations-api` at `UPDATE_COMPLETE` (11:30 a.m. Toronto), Lambda `Active` with update status `Successful`, and `/health` returned `status=ok`. The new authenticated save routes were not exercised against live store data. Updated desktop clients are still required; desktop publication/installation has not been verified.

| Information | Location in this reference |
| --- | --- |
| Architecture, runtime paths, navigation | [Sections 1–6](#1-project-overview) |
| Products, invoices, counts, valuation, reports | [Sections 7–15](#7-product-management) |
| Production, shared POS catalog, Labour | [Sections 16–17](#16-production-module) |
| Printing, backup, protected passwords, updater | [Sections 18–21](#18-printing-standards) |
| Releases, current scope, future priorities | [Sections 22–24](#22-versioning-and-releases) |
| Development and verification | [Sections 25–27](#25-important-design-principles) |
| Migration and database table reference | [Section 28](#28-database-reference) |
| API routes, local execution, deployment | [Section 29](#29-api-reference) |
| AWS infrastructure, credentials, recovery | [Section 30](#30-infrastructure-and-recovery) |
| Administration and deferred enhancements | [Sections 31–32](#31-administration) |
| Documentation maintenance and new sessions | [Section 33](#33-documentation-and-new-sessions) |

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
- Current release target is v4.2.0. `pom.xml` may remain at the prior version until `Release.ps1` performs the release-version update during packaging.
- Current database schema version in code is 23.
- API layer stack `esm-operations-api` is deployed in AWS and the desktop app uses API mode for normal workflows.
- On October 4, 2026, existing RDS, networking, subnet/monitoring resources were adopted in place into CloudFormation; API credentials moved to a retained Secrets Manager secret. RDS now has seven-day backup retention and deletion protection. Infrastructure Plan/Apply and status are available in Admin Tools; see the Infrastructure and Recovery section below. Store data and desktop release behavior were preserved.
- The deployed API stack was updated on Friday, October 2, 2026 after confirming AWS RDS schema version 23 and `labour_employee_pay_rates` were already present.
- Store Login is enabled by release packaging with `location.login.required=true`.
- Desktop API clients include the store session token with normal business requests, and the deployed API scopes store-owned reads/writes to the resolved location.
- Current API-mode migrated areas are Food Department workflows, Alcohol Department workflows, Supplies Department workflows, Production workflows, Reporting/Sales workflows, Labour Setup, Labour Hours, Daily Labour Cost, Tip Pool, Tip Pool Breakdown, GFS product guide import persistence, product purchase history, and administrative cloud snapshot download.
- Current API-mode gaps are future rollup/reporting enhancements and local backup/export polish.
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

The dashboard and section menus use a polished card-based navigation layout. The main application window launches maximized, and the home dashboard remains scrollable for smaller screens. Dense work screens such as invoices, inventory tables, weekly labour, production grids, and reporting tables remain task-focused rather than card-based.

The dashboard is organized around these operational areas:

- Inventory & Purchasing
- Operations Planning
- Labour
- Administration

Main cards include:

- Food Department
- Alcohol Department
- Supplies Department
- Production
- Reporting
- Labour Management
- System
- About

Administration also includes **POS Catalog / PLUs**, the shared POS item import and maintenance screen for Alcohol Sales Mappings and Production. Catalog XLSX imports read item names from column A and PLUs from column B. Production retains **POS Production Mappings** for profile assignments and usage-report import.

The sidebar includes Home, Back, and Switch Store when Store Login is required. Switch Store confirms the action, clears the saved location session, and returns to Store Login without closing the app.

The Labour Management module is complete and accepted as working as intended in the current v4.2.0 release target. It includes an administrator-protected Labour Setup screen, Labour Hours, Daily Labour Cost, Tip Pool, and Tip Pool Breakdown. Labour Hours uses an inventory-count-style list of saved weeks and opens a separate spreadsheet-style pop-out window for manual Shift 1 / Shift 2 hour entry. Daily Labour Cost uses a weekly start/end date range with Monday-Sunday rows, editable daily net sales, BOH labour dollars, FOH labour dollars, labour percentages, and a total row. Tip Pool uses editable daily tip-out pool amounts, tip-pool eligible employee hours from Labour Hours, calculated employee tip allocation by day, and weekly totals. Tip Pool Breakdown allocates saved tip pool amounts over a selected date range, subtracts configured uniform deductions per worked day for applicable employees only, rounds net payouts to the nearest nickel, and supports compact black-and-white printing.

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

Food invoice import now uses the finalized GFS `LineItemList.csv` export, reading received quantities and authoritative line totals by header name. Zero-quantity/zero-value rows are skipped. Weighted rows use the billed amount per received unit for fallback cost. Existing product setup supplies pack sizes and quantity conversions; this export does not supply actual delivered weight. Invoice number/date, shipping, HST, and paper grand total are entered at save, which requires merchandise plus adjustments to balance. Delivery-order H/P exports are rejected to avoid estimated order amounts being treated as billed amounts.

The preview shows Imported Merchandise, Current Merchandise, and Edits to Merchandise. The edit comparison is informational; only the save dialog's paper-grand-total reconciliation determines whether the invoice balances. Shipping is saved as the existing Freight adjustment for compatibility. CSV item tax is not automatically applied: the manager enters the paper invoice's exact HST.

Manager testing on October 8, 2026 confirmed the finalized import works better and balances correctly. The supplied invoice produced 64 received lines and $4,843.04 merchandise; $25.50 shipping plus $46.14 HST reconciled to $4,914.68. Weighted SKU 7243163 imported at $91.56, and undelivered SKU 7216397 was skipped.

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

Purchased quantities use the product setup conversion: `base quantity = cases × conversion factor + splits`. Period unit cost derives from billed merchandise cost divided by converted purchased units. Finalized weighted invoices preserve the actual billed cost, but this CSV lacks actual delivered weight; configured weight per case can therefore produce approximate quantities and cost per lb/kg. The manager accepts this approximation for the current workflow. No delivered-weight capture or schema change was added.

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
- Alcohol Sales Mapping add/edit reuses active records from the shared Administration POS Catalog / PLUs, filling POS SKU/PLU and POS Item Name automatically while leaving manual fields editable as a fallback
- Alcohol Variance Report calculates actual versus sold usage and quantity/dollar variances from completed opening/closing counts, period purchases, active Alcohol Sales Mappings, product profiles, valuation costs, and a separately selected POS usage `.xlsx` file. A prior Production import is not required.
- On October 3, 2026, corrected wine sales being skipped between the production import's KDS start and X41 Note.ESM end headings. Alcohol now reads active mapped PLUs throughout the sheet without an active shared POS catalog filter; Production keeps its existing exclusion. Verified six domestic wine PLUs with 25 sales, all 22 tests passed, and the manager confirmed correct operation. No API deployment or schema migration is needed.

Remaining priorities focus on future alcohol reporting, order guide, and printing polish when a specific live-data issue or workflow request appears:

1. Populate alcohol-only sales-to-inventory mappings from the POS Menu Items picker for variance reporting.
2. Monitor the completed alcohol variance report with live data and refine it when a concrete issue is reported.
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

Alcohol Sales Mappings also reuse this shared catalog as the selectable POS item source. Import and maintain it through Administration → POS Catalog / PLUs; assign production profiles through Production → POS Production Mappings. This avoids maintaining a second POS item list while keeping alcohol inventory variance mappings separate from production profiles.

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
- Labour Setup protected by its own configurable store-scoped password in Cloud API mode, separate from the System administrator password.
- Labour Hours with an inventory-count-style saved-week list and a pop-out spreadsheet-style Monday-Sunday grid for separate Shift 1 / Shift 2 employee-hour entry.
- Labour Hours department headers are styled as distinct bands in the pop-out entry window.
- Configurable labour positions grouped as `FOH` or `BOH`.
- Configurable labour employees with position, hourly wage, active status, tip-pool eligibility, and uniform-deduction applicability.
- Configurable default uniform deduction amount stored in settings.
- API-backed Labour Setup, Labour Hours, Daily Labour Cost, Tip Pool, and Tip Pool Breakdown reads/writes for Cloud API mode.
- Live AWS API deployment includes `/labour/weeks`, `/labour/weekly`, and `/labour/daily` routes, and the live RDS schema has been advanced through schema version 23 for multi-location operation and effective-dated labour pay rates.
- Daily operational sales and employee labour entries stored by actual calendar date.
- Wage, employee name, position name, and labour group snapshots on saved daily labour entries so historical weeks do not recalculate from later setup changes.
- Labour Setup stores pay-rate effective dates in `labour_employee_pay_rates`; unsaved Labour Hours and Daily Labour rows use the rate effective on the work date, while saved rows keep their stored wage snapshot.

Daily Labour Cost, Tip Pool, and Tip Pool Breakdown are implemented and accepted as working as intended.

Daily Labour Cost UI notes:

- Saving net sales updates only `labour_daily_sales.net_sales`; saving Tip Pool updates only `tip_out_pool`. Both preserve other sales fields, finalization, and labour hours, even when another manager changes them after the screen loads. The updated desktop requires the API's `PUT /labour/net-sales` and `PUT /labour/tip-pool` routes, each accepting `{ "workDate": "YYYY-MM-DD", "amount": 0 }`. The operator deployed the API through Admin Tools on October 9, 2026; stack/Lambda status and API health were verified afterward. Updated desktop clients are still required; older clients retain full-day saves. No schema migration is required. These saves do not resolve two users concurrently editing the same amount; the later save wins.

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

- Daily gross allocation is `saved daily tip_out_pool × employee daily hours / total eligible daily hours`, using Shift 1 plus Shift 2 and equal weight per eligible hour. Daily shares are rounded to four decimal places internally and summed over the inclusive date range. Uniform deductions apply once per worked day for applicable eligible employees; the final net payout is rounded once per employee to the nearest nickel. Independent rounding is not balanced back to the pool, but cannot explain a $50–$70 shortfall for a normal-sized team. When every day has positive eligible hours, saved pool totals should equal net payouts plus uniforms apart from small rounding differences. Compare these saved totals with actual collected money before concluding the formula lost funds. No specific reported discrepancy has been proven to result from the stale-save bug.

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

The application uses protected passwords for System and Labour Setup functions.

Current behavior:

- In Cloud API mode, the System administrator password and Labour Setup password are store-scoped and stored as salted PBKDF2 hashes on the signed-in `locations` row.
- Local SQLite/development fallback still stores protected passwords in `%LOCALAPPDATA%\FoodInventory\database.properties`.
- Passwords are stored as salted PBKDF2 hashes, not plain text.
- Legacy plain-text local or SQLite passwords are migrated to hashed local storage after a successful login.
- `scripts/Reset-AdminPassword.ps1` can reset the local SQLite/development fallback administrator password when the System screen cannot be accessed.
- This password protects local app administration. It is not the AWS API key, database password, or a per-user cloud identity.
- Labour Setup uses a separate password from the System administrator password.
- When no Labour Setup password exists for the signed-in store, the first Labour Setup login accepts the temporary password `LabourSetup!` and then immediately prompts for a new store-scoped Labour Setup password before opening the screen.
- The Labour Setup password can be changed later from the password-protected System screen.
- `scripts/Apply-LocationProtectedPasswordsMigration.ps1` adds the store-scoped protected-password columns to AWS RDS.
- `scripts/Reset-LabourSetupPassword.ps1` removes only the local fallback Labour Setup password keys, returning a local SQLite/development PC to the temporary-password first-use flow.

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
- Department invoice workflows and purchasing history
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
- Neutral StoreOps Manager branding and app icon
- Maximized app launch and polished card-based navigation
- About panel with version, database mode, signed-in store, and application scope
- Switch Store support from the sidebar when Store Login is required
- Installer checksum generation, GitHub Release checksum upload, and in-app checksum verification before installer launch
- Cloud API operating mode with local SQLite backup snapshot support
- Password-protected cloud snapshot download; cloud replacement remains an explicit administrator recovery operation outside normal desktop use
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
- Normal API workflows for Food, Alcohol, Supplies, Production, Reporting/Sales, product import, and product purchase history are confirmed working for the v4.2.0 release target

Labour Management, multi-location Store Login/location scoping, the StoreOps Manager navigation shell, and manager-tested invoice/variance workflows are accepted as working as intended. The v4.2.0 wording elsewhere describes planned release scope; the source POM and release history establish built versions. Remaining work focuses on publishing validated desktop changes and future enhancements.

Primary unfinished areas:

- Food production-to-inventory variance later, after multi-location and infrastructure priorities are settled
- Recipe costing
- Yield tracking
- Advanced reporting
- Inventory count template synchronization

---

# 24. Development Priorities

## Immediate

1. Continue monitoring daily Cloud API use on the configured work PCs.
2. Release the validated finalized Food invoice import and zero-sales wine-category fix; verify installer upgrade behavior before publishing.
3. Continue advanced reporting and workflow polish.

## Medium Term

1. Review and maintain alcohol sales-to-inventory mappings as POS items change.
2. Refine completed alcohol variance reporting when live use identifies a specific need.
3. Resume food production item to inventory product mappings after multi-location and infrastructure priorities are settled.
4. Add recipe and yield management.

## Long Term

1. Recipe costing.
2. Theoretical food cost.
3. Actual-versus-theoretical variance.
4. Historical trend reporting.
5. Cross-location reporting and safer store administration as requirements emerge.
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

---

# 27. Implementation Standards

## General Development Rules

- Preserve the current JavaFX + Cloud API + AWS RDS PostgreSQL backend + local SQLite snapshot + Maven architecture.
- Treat Cloud API mode as the normal client path, with AWS RDS PostgreSQL behind the API as the backend shared database.
- Do not add new direct desktop PostgreSQL workflows. PostgreSQL credentials should stay off client PCs.
- Keep SQLite only as the local cloud-snapshot/backup target and development fallback path.
- Prefer targeted changes over large rewrites.
- Do not rename packages, models, DAOs, or major screens unless explicitly requested.
- Do not delete user data or reset the database unless explicitly asked.
- After Java changes, run:

```bash
mvn clean test
```

---

## Package Structure

Main package:

```text
ca.foodinventory
```

Common package layout:

```text
ca.foodinventory.ui
ca.foodinventory.dao
ca.foodinventory.model
ca.foodinventory.service
ca.foodinventory.database
```

Use the existing structure:

- UI screens -> `ui`
- Database access -> `dao`
- Plain data objects -> `model`
- Business/report/import logic -> `service`
- Schema/migrations -> `database`

---

## JavaFX UI Patterns

Most screens use:

- `BorderPane`
- `VBox`
- `HBox`
- `GridPane`
- `TableView`
- `Dialog`
- `FileChooser`

Common conventions:

- Add root style class where appropriate:

```java
getStyleClass().add("root-dark");
```

- Page titles use:

```java
title.getStyleClass().add("page-title");
```

- Primary action buttons use:

```java
button.getStyleClass().add("primary-button");
```

- Prefer hand-built JavaFX layouts over FXML unless the project is explicitly migrated.
- For Labour Management entry screens, preserve the familiar spreadsheet-like workflow where managers already work that way. Use compact grids, predictable keyboard traversal, and horizontal scrolling when needed rather than redesigning labour entry into generic dashboards or per-cell dialogs.
- Keep Labour Management calculations in service classes, not JavaFX listener blocks, so Daily Labour Cost, Tip Pool, and Tip Pool Breakdown can reuse weekly hours, labour-dollar, tip-allocation, and uniform-deduction logic.

---

## TableView Rules

Use JavaBean-style getters in model classes so `PropertyValueFactory` works correctly.

Example:

```java
TableColumn<Product, String> descriptionCol = new TableColumn<>("Description");
descriptionCol.setCellValueFactory(new PropertyValueFactory<>("description"));
```

If adding a new table column:

1. Confirm the model has a matching getter.
2. Confirm the DAO populates the field.
3. Confirm null values render safely.

---

## Dialog Rules

For add/edit workflows:

- Use a dedicated dialog class when the form is reusable or complex.
- Use inline dialogs for very small one-off prompts.
- Validate required fields before saving.
- Show clear warning alerts for missing inputs.
- Show error alerts for failed save/import operations.

Alert pattern:

```java
showAlert(Alert.AlertType.WARNING, "Title", "Message");
```

---

## DAO Rules

DAO classes should:

- Own SQL for one model area.
- Use prepared statements.
- Avoid mixing UI logic into DAO classes.
- Throw runtime exceptions with useful messages when database operations fail.
- Keep connection management consistent with existing code.

Do not make DAO methods depend on JavaFX classes.

---

## Service Rules

Service classes should contain business logic, import parsing, report generation, or calculations.

Examples:

- CSV/XLSX import logic
- Inventory valuation
- Weekly cost reporting
- Production report generation
- Backup logic

Avoid putting calculations directly into JavaFX view classes if they may be reused.

---

## Migration Rules

When database structure changes:

1. Create a new migration class.
2. Increment the schema version in `DatabaseMigrationRunner`.
3. Keep migrations backward-compatible.
4. Avoid destructive schema changes.
5. Use repair logic if older databases may have partially upgraded schemas.
6. Verify app startup against an existing runtime database.

Never assume the project-root database is the active database.

In Cloud API mode, System administrator and Labour Setup passwords are store-scoped protected passwords stored as salted PBKDF2 hashes on the signed-in `locations` row. Local SQLite/development fallback stores protected passwords in `%LOCALAPPDATA%\FoodInventory\database.properties`. Do not store or reintroduce plain-text protected passwords.

Runtime DB path is under:

```text
%LOCALAPPDATA%/FoodInventory/food_inventory.db
```

---

## Import Rules

### GFS Invoice CSV

Use the finalized GFS `LineItemList.csv`, matched by column headers. Delivery-order H/P exports are rejected with a clear message.

- `Item Code` = SKU (preserve leading zeros).
- `Item Description` = description.
- `Current Quantity` = received cases or splits, selected by `Split Item Indicator` (Y/N).
- `Line Total` = authoritative merchandise extended cost, excluding item tax.
- `Unit Price` = purchased unit cost for normal items.
- `Unit of Measure` Y marks weighted items; derive billed case/each cost from line total / received quantity rather than storing the per-pound price as case cost.
- Skip zero-quantity, zero-value rows. Reject invalid numbers and unsupported indicators instead of silently importing zero.
- Pack size and base-unit conversions come from product setup. Actual delivered weight is not supplied in this export.
- Invoice number/date, shipping, HST, and paper grand total are entered manually at save. Shipping is stored as the existing Freight adjustment for API/database compatibility.
- Save requires merchandise plus adjustments to equal the paper grand total. The imported total now means original merchandise subtotal, not supplier grand total.
- Preview wording is Imported Merchandise, Current Merchandise, and Edits to Merchandise; the edit comparison is informational and must not imply an unbalanced paper invoice.
- Exact billed cost is preserved for weighted items, while base-unit quantity remains `cases × product conversion factor + splits`. Do not imply that the export provides measured delivered weight.
- Regression coverage verifies billed weighted cost, zero-delivery exclusion, split quantities, SKU leading zeros, invalid numeric rejection, and delivery-order format rejection. The supplied finalized invoice was also verified read-only and accepted in manager testing on October 8, 2026.

Important:

- Support duplicate SKUs/lines on the same invoice.
- Preserve duplicate invoice overwrite behavior.
- When a manual invoice line uses split / each cost without a case cost, preserve valuation fallback by deriving the product's last known purchased-unit cost from each cost and `conversion_factor`.
- Never overwrite a valid `last_case_cost` with zero just because the purchase was entered on the split / each side.
- For alcohol-specific manual invoices, treat line-level inclusion flags as merchandise-cost calculation helpers only.
- Save exact paper HST and exact paper bottle deposit totals as the accounting adjustment values.
- If a final invoice balancing difference remains, reconcile it into merchandise categories rather than modifying the saved HST amount.

---

### POS Sales / Usage XLSX

Use Apache POI.

For POS Sales Report import in `PosSalesImportService`:

- Gross sales = fixed zero-based column index `1`
- Net sales = fixed zero-based column index `3`
- Do not infer net sales as the next currency value found in the row.

For Production Usage Report:

- Column B = SKU/PLU
- Column D = Monday qty
- Column F = Tuesday qty
- Column H = Wednesday qty
- Column J = Thursday qty
- Column L = Friday qty
- Column N = Saturday qty
- Column P = Sunday qty
- Column R = Weekly qty

Skip KDS-only rows between markers:

```text
kds dnu.<store suffix>
X41 Note.ESM
```

Cloud-mode save/import workflows that can touch many rows should run off the JavaFX application thread and batch database writes where practical. This is especially important for inventory count saves/completions, Sales Period save/import, and Weekly Production usage report import/generation.

Alcohol Variance uses the same XLSX quantity columns but selects PLUs from active Alcohol Sales Mappings. Do not apply Production's KDS section exclusion or active shared POS catalog filter to alcohol usage: valid wine rows can occur between the KDS start and X41 Note.ESM end headings. Keep Production's existing exclusion behavior when changing the shared parser.

---

## Weekly Production Rules

`WeeklyProductionView.java` is currently the baseline for Weekly Production.

Preserve:

- Week selection.
- Usage Report import.
- Day tabs Monday through Sunday.
- `Include all active production items`.
- Override Par editing.
- Save Overrides.
- Prep Sheet selector.
- Prep Sheet filtering.
- Preview Prep Sheet.
- Print Prep Sheet.

### Printing Rules

Prep sheets should print as:

- One prep sheet per page.
- Compact layout.
- Station headers included.
- Columns:
  - `ITEM`
  - `UNIT`
  - `LIFE`
  - day name
  - `COUNT`
  - `TO DO`
  - `INITIAL`

Important:

- Do not reintroduce the old 32-row pagination rule.
- Do not split Main Line prep into multiple pages unless explicitly requested.
- Future print tuning should first adjust:
  - row height
  - font size
  - padding
  - column width
  - page orientation
- Avoid complicated JavaFX transform chains unless required.

---

## Freezer Pull Rules

Freezer Pull:

- Is an independent manual workflow.
- Filters to Production Items assigned to `Freezer Pull`.
- Prints only freezer-pull lines.
- Unit comes from the Production Profile line where applicable.
- Current Freezer Pull behavior is accepted as working as intended. Leave this module unchanged unless a task explicitly targets Freezer Pull.

Do not merge freezer pull into Weekly Production printouts unless explicitly requested.

---

## Production Module Rules

Production setup workflow:

1. Production Stations
2. Production Items
3. Production Profiles
4. POS Menu Items
5. Product Mappings

Production Profiles define the production quantities for sold POS items.
Production Item yield factors default to `1.0` and should only be changed for items that need prep-yield conversion.

POS Menu Items connect POS SKUs/PLUs to Production Profiles.

Product Mappings connect Production Items to Inventory Products for future variance reporting.

---

## Naming Style

Use descriptive class and method names.

Examples:

```java
ProductionUsageReportImportService
ProductionReportService
ProductionWeekDao
WeeklyProductionView
```

Prefer clarity over cleverness.

---

## Error Handling

Use clear user-facing alerts for:

- Missing required input.
- Failed import.
- Failed save.
- Failed print.
- Empty selection.

Use exception messages that help diagnose file/import/database issues.

---

## Build / Verification Checklist

After changes:

```bash
mvn clean test
```

Manual verification when relevant:

- App launches.
- Screen opens.
- Data loads.
- Save works.
- Import works.
- Print/preview works.
- No runtime database migration errors.

---

---

# 28. Database Reference

These are functional table/field notes, not an exhaustive SQL schema dump. Migrations and the PostgreSQL initializer define the exact schema. PostgreSQL API mode uses location-aware uniqueness; SQLite retains its single-location fallback constraints.

## Migration Files

Current known migration files:

- `Migration2`
- `Migration3`
- `Migration4`
- `Migration5`
- `Migration6`
- `Migration7`
- `Migration8`
- `Migration9`
- `Migration10`
- `Migration11`
- `Migration12`
- `Migration13`
- `Migration14`
- `Migration15`
- `Migration16`
- `Migration17`
- `Migration18`
- `Migration19`
- `Migration20`
- `Migration21`
- `Migration22`
- `Migration23`

PostgreSQL support:

- `PostgresSchemaInitializer`
  - Creates or repairs a PostgreSQL-compatible version of the current schema when the connected user has schema privileges.
  - Validates required schema for lower-access application users.
  - Lower-access application users may use existing tables after a schema-capable user applies new PostgreSQL schema changes.
- `SQLiteToPostgresUploadTool`
  - Copies data from the runtime SQLite database into PostgreSQL through `DatabaseSyncService`.
  - Requires explicit upload confirmation before replacing PostgreSQL table data.
  - Initial upload to Aiven `defaultdb` copied 6,576 rows on Sunday, August 2, 2026.
  - Production cloud data was later copied from Aiven PostgreSQL to AWS RDS PostgreSQL on Sunday, August 30, 2026.
- `DatabaseSyncService`
  - Uploads local SQLite data to cloud PostgreSQL.
  - Downloads cloud PostgreSQL data to local SQLite, backing up the local database first.
  - These actions are administrator migration/recovery tools. Local-to-cloud upload has been removed from normal desktop use; normal daily cloud use happens through Cloud API mode and backup/cloud snapshot download flows.
- `scripts/Apply-LabourSchemaMigration.ps1`
  - Applies the Labour Management schema foundation and Migration 17 snapshot columns to AWS RDS through a schema-capable admin user.
  - Prompts for the RDS admin password securely and does not store it.
  - Refreshes `operations_app` grants for Labour tables/sequences after schema creation.
- `scripts/Apply-LocationAuthSchemaMigration.ps1`
  - Applies the location-auth schema foundation to AWS RDS through a schema-capable admin user.
  - Prompts for the RDS admin password and initial location password securely.
  - Stores only salted PBKDF2 password hash data in `locations`.
- `scripts/Apply-StoreLocationSchemaMigration.ps1`
  - Applies store-owned `location_id` columns for the multi-location data model.
  - Prompts for the RDS admin password securely and does not store it.
- `scripts/Apply-LocationUniqueConstraintsMigration.ps1`
  - Applies the Migration 21 PostgreSQL uniqueness changes to AWS RDS through a schema-capable admin user.
  - Prompts for the RDS admin password securely and does not store it.
- `scripts/Copy-LocationSetup.ps1`
  - Copies selected setup/master data from one location to another after both locations exist.
  - Supports products, aliases, inventory templates, alcohol setup, production setup, POS menu items, and production product mappings.
  - Does not copy invoices, counts, sales periods, labour history, generated production weeks, sessions, or passwords.
- `scripts/Apply-LabourPayRateSchemaMigration.ps1`
  - Applies the Migration 23 labour pay-rate history table to AWS RDS through a schema-capable admin user.
  - Prompts for the RDS admin password securely and does not store it.
  - Refreshes `operations_app` grants for the pay-rate table and sequence after schema creation.

Migration responsibilities:

- `Migration6`
  - Added production module tables.
- `Migration7`
  - Added `production_items.shelf_life`.
- `Migration8`
  - Added `production_stations.prep_sheet`.
- `Migration9`
  - Added `production_items.permanent_override_par`.
- `Migration10`
  - Added invoice subtotal/allocation fields to `invoices`:
    `imported_total`, `merchandise_subtotal`, `freight`, and `hst`.
- `Migration11`
  - Added `invoice_adjustments`.
  - Preserved any previously saved Freight/HST values as adjustment rows.
- `Migration12`
  - Added `production_items.yield_factor`.
  - Existing production items default to a yield factor of `1.0`.
- `Migration13`
  - Added `inventory_count_template_lines.order_guide_case_size`.
  - Order Guide case-size edits now persist per count template line.
- `Migration14`
  - Added net-sales fields to `sales_periods`:
    `food_net_sales`, `beer_net_sales`, `wine_net_sales`, `draught_net_sales`,
    `import_draught_net_sales`, and `liquor_net_sales`.
  - Existing net-sales values are seeded from the matching gross sales fields when missing.
- `Migration15`
  - Added `alcohol_sales_mappings` for alcohol-only POS item to inventory product variance mappings.
- `Migration16`
  - Added Labour Management Phase 1 tables:
    `labour_positions`, `labour_employees`, `labour_daily_sales`, and `labour_daily_entries`.
  - Added setting key `labour.default_uniform_deduction`.
  - Added indexes for labour position/group lookup, employee/position lookup, and daily labour entry date lookup.
- `Migration17`
  - Added `labour_daily_entries` snapshot columns:
    `employee_name_snapshot`, `position_name_snapshot`, and `labour_group_snapshot`.
  - These snapshots preserve historical Labour Hours context when employee wages or positions later change.
- `Migration18`
  - Added `locations` for one username/password credential per store/location.
  - Added `location_sessions` for hashed API session tokens.
  - Seeded the current store as location `1` with code `STORE` and username `store`.
- `Migration19`
  - Added `location_id` to Labour Management tables:
    `labour_positions`, `labour_employees`, `labour_daily_sales`, and `labour_daily_entries`.
  - Existing Labour records default to location `1`.
  - Labour API reads and writes are scoped to the resolved location session.
- `Migration20`
  - Added `location_id` to store-owned operational tables, including products, aliases, invoices, inventory templates/counts, sales periods, alcohol setup, and production setup/weeks.
  - Existing records default to location `1`.
  - Main store-owned API routes now use the resolved location session to filter and write these tables by `location_id`.
- `Migration21`
  - PostgreSQL multi-location uniqueness migration.
  - Replaces global uniqueness for product SKUs, product alias SKUs, sales periods, alcohol sales mapping POS SKUs, production station/item/profile names, POS menu SKUs, and production weeks with location-aware composite uniqueness.
  - SQLite fallback keeps its original single-location uniqueness model.
- `Migration22`
  - Adds store-scoped protected-password columns to `locations` for System administrator and Labour Setup passwords.
  - Cloud API mode stores these passwords as salted PBKDF2 hashes on the signed-in location so protected access follows the store across workstations.
- `Migration23`
  - Adds `labour_employee_pay_rates` for effective-dated employee hourly wage history.
  - Seeds existing employee wages with an effective date of `1900-01-01`.
  - Labour Hours and Daily Labour use the rate effective on each work date when creating unsaved entries.
  - Already-saved `labour_daily_entries.hourly_wage` values remain the historical wage snapshot for reports.

---

## Core Inventory Tables

### products

Stores inventory products.

Important fields:

- `id`
- `sku`
- `description`
- `category`
- `reporting_category`
- `unit`
- `conversion_factor`
- `pack_size`
- `pack_count`
- `last_case_cost`
- `last_purchased_date`
- `active`

Notes:

- `sku` is unique per location in PostgreSQL API mode.
- `reporting_category` drives department/reporting grouping.
- `last_case_cost` is used as a fallback for valuation when no period purchase cost exists.

---

### product_sku_aliases

Maps supplier-specific SKUs to products.

Important fields:

- `id`
- `product_id`
- `supplier`
- `sku`
- `description`
- `pack_size`
- `active`

Used by invoice imports to connect supplier CSV lines to existing products.

---

### invoices

Stores invoice headers.

Important fields:

- `id`
- `invoice_number`
- `supplier`
- `invoice_date`
- `imported_total`
- `merchandise_subtotal`
- `freight`
- `hst`
- `invoice_total`

Notes:

- Invoice duplicate handling relies on `invoice_number`.
- `merchandise_subtotal` is the inventory-cost portion of the invoice.
- For finalized GFS LineItemList imports, `imported_total` stores the original CSV merchandise subtotal, not a supplier header/grand total. Invoice number/date and final invoice total are supplied manually during reconciliation.
- `freight`, `hst`, and other non-inventory charges are excluded from valuation logic.

---

### invoice_lines

Stores invoice line items.

Important fields:

- `id`
- `invoice_id`
- `product_id`
- `quantity`
- `base_quantity`
- `pack_size`
- `case_cost`
- `extended_cost`

Notes:

- `base_quantity` is used for usage and valuation calculations.
- GFS import calculates base quantity from cases and splits.
- Finalized GFS import uses `Current Quantity` and `Split Item Indicator`; `base_quantity = case quantity × products.conversion_factor + split quantity`. Pack/conversion settings come from product setup, not the finalized CSV.
- `extended_cost` comes directly from the finalized CSV's billed Line Total, excluding item tax. Zero-quantity/zero-value rows are skipped.
- Weighted rows derive case/each cost from billed line total divided by received quantity, rather than treating the supplier's per-pound price as case cost. Actual delivered weight is absent, so converted quantity and base-unit cost remain approximate when case weight varies.
- `case_cost` remains the stored purchased-unit cost field.
- When manual invoices use split / each pricing without a case cost, product fallback valuation cost is derived from the each cost and the product conversion factor rather than overwriting `last_case_cost` with zero.

---

### invoice_adjustments

Stores non-inventory invoice charges and balancing rows.

Important fields:

- `id`
- `invoice_id`
- `description`
- `amount`
- `display_order`

Notes:

- Used for HST, Freight, Bottle Deposit, Keg Deposit, and similar non-inventory charges.
- These rows are saved for invoice balancing and display, but are not part of inventory valuation.
- Food import requires manual paper HST and shipping at reconciliation. Shipping is persisted as Freight for compatibility; CSV Item Tax is not automatically saved as HST. Save requires merchandise plus adjustments to equal the manually entered paper grand total.
- For alcohol invoices, exact paper HST and exact paper bottle deposit should be saved here as the accounting values.
- Any remaining reconciliation difference should be absorbed into merchandise line allocation rather than changing the saved HST amount.

---

## Inventory Count Tables

### inventory_count_templates

Stores count sheet templates.

Important fields:

- `id`
- `name`
- `department`
- `active`

---

### inventory_count_template_lines

Stores products and layout settings for count templates.

Important fields:

- `id`
- `template_id`
- `product_id`
- `section_name`
- `sort_order`
- `count_unit`
- `conversion_factor_to_base`
- `display_name`
- `order_guide_case_size`
- `active`

Notes:

- `section_name` and `sort_order` control printed count sheet/order guide layout.
- `conversion_factor_to_base` converts counted units to product base units.
- `order_guide_case_size` overrides the product pack size on generated order guides without changing the product record.

---

### inventory_counts

Stores completed or in-progress inventory counts.

Important fields:

- `id`
- `template_id`
- `count_date`
- `period_start_date`
- `period_end_date`
- `notes`
- `completed`

---

### inventory_count_lines

Stores individual counted quantities.

Important fields:

- `id`
- `count_id`
- `product_id`
- `quantity`
- `count_unit`
- `converted_quantity`

Notes:

- `converted_quantity` is the calculated quantity in product base units.

---

## Sales Tables

### sales_periods

Stores sales totals by period.

Important fields:

- `id`
- `period_start_date`
- `period_end_date`
- `food_sales`
- `beer_sales`
- `wine_sales`
- `draught_sales`
- `import_draught_sales`
- `liquor_sales`

Notes:

- Imported from POS sales `.xlsx`.
- Weekly/category cost reporting uses these values.

---

## Alcohol Variance Tables

### alcohol_sales_mappings

Maps POS alcohol items from the item-level POS usage report to alcohol inventory products.

Important fields:

- `id`
- `pos_sku`
- `pos_item_name`
- `reporting_category`
- `product_id`
- `quantity_per_sale`
- `unit`
- `active`

Notes:

- This table is intentionally separate from production item mappings.
- It supports alcohol variance reporting without adding bar workflows to the Production menu.
- `pos_sku` should match the SKU/PLU from the POS usage report used by Weekly Production.
- The Alcohol Sales Mapping dialog reuses active records from `pos_menu_items` to fill `pos_sku` and `pos_item_name`, but `alcohol_sales_mappings` remains the authoritative alcohol variance mapping table.
- `quantity_per_sale` and `unit` describe how much inventory product is consumed by one sale, such as `20 OZ` for a draught pint or `1 EACH` for a bottle.

---

## Location Authentication Tables

Migrations 18 through 21 provide the multi-location Store Login, row ownership, and location-aware uniqueness foundation used by v4.0.0 and later releases.

### locations

Stores one login credential per location.

Important fields:

- `id`
- `code`
- `name`
- `username`
- `password_hash`
- `password_salt`
- `password_iterations`
- `admin_password_hash`
- `admin_password_salt`
- `admin_password_iterations`
- `labour_setup_password_hash`
- `labour_setup_password_salt`
- `labour_setup_password_iterations`
- `active`
- `created_at`

Notes:

- Passwords are stored as salted PBKDF2 hashes, not plain text.
- The initial current-store row is location `1`, code `STORE`, username `store`.
- This is location identity, not employee/user identity.
- In Cloud API mode, System administrator and Labour Setup passwords are also stored on this table as salted PBKDF2 hashes scoped to the location.
- When `labour_setup_password_hash` is blank, the API accepts the temporary Labour Setup password `LabourSetup!` once and the desktop immediately requires the store to create a replacement password.

### location_sessions

Stores API login sessions for location credentials.

Important fields:

- `id`
- `location_id`
- `token_hash`
- `created_at`
- `expires_at`
- `revoked`

Notes:

- The API returns the raw session token to the desktop after successful login.
- Only a SHA-256 hash of the session token is stored in the database.
- Labour and the main store-owned operational API routes filter reads and writes by `location_id` from the resolved store session.

---

## Settings Tables

### settings

Stores shared application settings.

Known setting use cases:

- Labour default uniform deduction amount: `labour.default_uniform_deduction`.

Protected password note:

- In Cloud API mode, System administrator and Labour Setup passwords live on the signed-in `locations` row, not in local machine settings.
- Local SQLite/development fallback stores protected passwords in `%LOCALAPPDATA%\FoodInventory\database.properties` as salted PBKDF2 hashes.
- Legacy `admin_password` / `password_initialized` database settings may exist for old databases, but new desktop code should not use the shared `settings` table as the administrator password authority.

---

## Labour Tables

Labour tables were introduced by Migration 16.

### labour_positions

Stores configurable labour positions/departments for Labour Management.

Important fields:

- `id`
- `name`
- `labour_group`
- `sort_order`
- `target_labour_percentage`
- `active`

Notes:

- `labour_group` supports the reporting concepts `FOH` and `BOH`.
- Do not hard-code calculations around position names such as Server, Bartender, Cook, or Dish.
- Positions are deactivated with `active = 0` rather than deleted.

### labour_employees

Stores Labour Setup employee configuration.

Important fields:

- `id`
- `name`
- `position_id`
- `hourly_wage`
- `tip_pool_eligible`
- `uniform_deduction_applicable`
- `active`

Notes:

- Employee names and wages are manually configured by managers/admins; they are not imported or hard-coded.
- Employees are deactivated with `active = 0` rather than deleted.
- Saved labour/tip records use stored daily entry snapshots for historical accuracy when wages or positions later change.

### labour_employee_pay_rates

Stores effective-dated hourly wage history for Labour Management employees.

Important fields:

- `id`
- `location_id`
- `employee_id`
- `hourly_wage`
- `effective_date`
- `created_at`

Notes:

- One row is stored per employee, location, and effective date.
- Existing employee wages are seeded with an effective date of `1900-01-01` during Migration 23.
- Labour Hours and Daily Labour use the latest pay-rate row whose effective date is on or before the work date when creating unsaved daily entries.
- Saved `labour_daily_entries.hourly_wage` remains the historical wage snapshot for that work date.

### labour_daily_sales

Stores daily operational sales values for Labour Management.

Important fields:

- `id`
- `sales_date`
- `net_sales`
- `tip_out_pool`
- `finalized`

Notes:

- These records are separate from official weekly imported `sales_periods`.
- Daily Labour Cost uses `net_sales` for weekly cost percentages.
- Tip Pool uses `tip_out_pool` as the daily amount to allocate across tip-pool eligible employee hours.

### labour_daily_entries

Stores daily employee labour entries by actual calendar date.

Important fields:

- `id`
- `work_date`
- `employee_id`
- `position_id`
- `hourly_wage`
- `shift_1_hours`
- `shift_2_hours`
- `employee_name_snapshot`
- `position_name_snapshot`
- `labour_group_snapshot`
- `finalized`

Notes:

- `shift_1_hours` and `shift_2_hours` are stored separately for the spreadsheet-style Labour Hours workflow.
- `hourly_wage`, `position_id`, `employee_name_snapshot`, `position_name_snapshot`, and `labour_group_snapshot` are stored on each entry so historical labour reports do not silently change after employee setup changes.
- Daily hours are calculated as `shift_1_hours + shift_2_hours`.
- Daily labour dollars are calculated as daily hours multiplied by the stored `hourly_wage` snapshot.
- A unique rule on `work_date, employee_id` prevents duplicate current entries for the same employee/date.

### Labour API Routes

Cloud API mode uses the existing authenticated desktop API pattern.

Setup routes:

- `GET /labour/positions`
- `GET /labour/positions/active`
- `POST /labour/positions`
- `PUT /labour/positions/{id}`
- `POST /labour/positions/{id}/deactivate`
- `GET /labour/employees`
- `POST /labour/employees`
- `PUT /labour/employees/{id}`
- `POST /labour/employees/{id}/deactivate`
- `GET /labour/settings`
- `PUT /labour/settings`

Labour Hours routes:

- `GET /labour/weeks`
- `GET /labour/weekly/{weekStartDate}`
- `PUT /labour/weekly`

Daily Labour Cost / Tip Pool routes:

- `GET /labour/daily/{workDate}`
- `PUT /labour/daily`

Notes:

- `weekStartDate` is the Monday canonical week start in `YYYY-MM-DD` format.
- `GET /labour/weeks` returns distinct saved Labour Hours week starts for the list-style Labour Hours screen.
- `PUT /labour/weekly` saves a full week payload in bulk rather than issuing one request per edited cell.
- Labour Hours data is stored by actual calendar date, not by relative "this week" columns.
- Daily Labour Cost and Tip Pool use `workDate` / `salesDate` in `YYYY-MM-DD` format and save daily sales plus employee daily entries in one request.
- `tip_pool_eligible` and `uniform_deduction_applicable` are returned in labour row payloads so Tip Pool and Tip Pool Breakdown can calculate from the same API-loaded labour data.
- The live AWS RDS Labour schema was applied on Sunday, September 6, 2026. Labour API route updates for Daily Labour Cost, Tip Pool, Tip Pool Breakdown, and Labour Hours saved-week listing were deployed on Monday, September 7, 2026. Later multi-location, protected-password, and pay-rate history migrations advanced the live RDS schema through version 23.

---

## Production Tables

Production tables were introduced by Migration 6.

### production_stations

Stores kitchen/prep stations.

Important fields:

- `id`
- `name`
- `active`
- `prep_sheet`

Notes:

- `prep_sheet` controls which printable prep sheet a station belongs to.
- Default assignments:
  - `Pizza` and `Salad` -> `Pizza Salad`
  - Other stations -> `Main Line`

---

### production_items

Stores prep/production items.

Important fields:

- `id`
- `name`
- `unit`
- `station_id`
- `active`
- `shelf_life`
- `yield_factor`
- `permanent_override_par`

Notes:

- `shelf_life` is printed in the LIFE column on prep sheets.
- `yield_factor` adjusts generated quantities for prep yield. A value of `1.0` preserves existing behavior.
- `permanent_override_par`, when set, becomes the default override/final par for generated Weekly Production lines.
- Freezer Pull items use pull-style units.

---

### production_profiles

Stores production profile headers.

Important fields:

- `id`
- `name`
- `active`

Notes:

- A production profile is the recipe/build list for a sold POS item.

---

### production_profile_lines

Stores production item quantities per production profile.

Important fields:

- `id`
- `profile_id`
- `production_item_id`
- `quantity_per_sale`
- `unit`
- `active`

Notes:

- These lines determine how POS item sales convert into production quantities.
- Weekly Production uses `sales quantity * quantity_per_sale / production item yield factor` before applying the par multiplier.
- Freezer Pull line units can differ from normal prep units.

---

### pos_menu_items

Stores POS menu items and their assigned production profile.

Important fields:

- `id`
- `pos_sku`
- `name`
- `production_profile_id`
- `active`

Notes:

- `pos_sku` maps to the PLU/SKU in POS Usage Report files.
- Existing POS items should preserve profile assignment during import/update.

---

### production_item_product_mappings

Maps production items to inventory products.

Important fields:

- `id`
- `production_item_id`
- `product_id`
- conversion/quantity fields if present in current implementation
- `active`

Purpose:

- Future production variance reporting.
- Connect theoretical production usage to inventory product usage.

---

### production_weeks

Stores generated weekly production headers.

Important fields:

- `id`
- `week_start_date`
- `week_end_date`
- `par_multiplier`
- created/updated fields if present

Notes:

- Re-importing the same week replaces the existing generated week.

---

### production_week_days

Stores each day inside a generated production week.

Important fields:

- `id`
- `production_week_id`
- `day_name`
- `prep_date`

Notes:

- Weekly Production displays each day as a separate tab.

---

### production_week_lines

Stores generated and overridden production lines for each day.

Important fields:

- `id`
- `production_week_day_id`
- `production_item_id`
- `production_item_name`
- `station_name`
- `prep_sheet`
- `unit`
- `shelf_life`
- `previous_sales_quantity`
- `generated_par`
- `override_par`
- `final_par`

Notes:

- `previous_sales_quantity` stores the generated quantity from prior POS usage.
- `generated_par` is calculated from prior sales and multiplier.
- `override_par` is user-editable.
- `final_par` is override par when present, otherwise generated par.
- Weekly Production print uses `final_par`.

---

---

# 29. API Reference

The standalone `api/` Maven project implements the HTTP/Lambda backend without pulling JavaFX into the deployment package. Normal business requests require both `x-api-key` and a valid `x-location-token` when location authentication is enabled. `/health` is public; `/auth/login` exchanges store credentials for a session token. Local examples calling business routes must also supply a valid location token when that enforcement is enabled.

Food/Alcohol/Supplies, Production, Reporting/Sales, Labour, product support, and protected-password workflows use the deployed API. File parsing stays on the desktop. Admin sync download/upload are explicit recovery tools; upload replaces cloud data and is not an ordinary desktop action. No API/schema changes are required for finalized Food invoice parsing.

Additional established routes include `POST /products/import`, `GET /products/{id}/purchase-history`, Reporting/Sales endpoints, and `GET /admin/sync/download` / `POST /admin/sync/upload`. The inventory below reflects the former API guide; consult `ApiRoutes` for the complete executable route set.
## Route inventory

```text
GET /health
POST /auth/login
GET /protected-passwords/status
POST /protected-passwords/verify
PUT /protected-passwords/{scope}
GET /products
POST /products
PUT /products/{id}
POST /products/{id}/deactivate
POST /products/resolve-skus
POST /products/aliases
GET /pos-menu-items
POST /pos-menu-items
POST /invoices/exists
POST /invoices/delete
POST /food-invoices
POST /alcohol-invoices
POST /supplies-invoices
GET /departments/{department}/inventory-count-templates
POST /departments/{department}/inventory-count-templates
POST /inventory-count-templates/{id}/deactivate
POST /inventory-count-templates/{id}/duplicate
GET /inventory-count-templates/{id}/lines
POST /inventory-count-templates/{id}/lines
PUT /inventory-count-templates/{id}/line-sort-orders
PUT /inventory-count-template-lines/{id}
POST /inventory-count-template-lines/{id}/deactivate
PUT /inventory-count-template-lines/{id}/order-guide-case-size
GET /departments/{department}/inventory-counts
POST /departments/{department}/inventory-counts
GET /departments/{department}/completed-inventory-counts
GET /inventory-counts/{id}/lines
PUT /inventory-counts/{id}/lines
POST /inventory-counts/{id}/complete
DELETE /inventory-counts/{id}
GET /order-guide/{openingCountId}/{closingCountId}
GET /alcohol-product-profiles
GET /alcohol-sales-mappings
POST /alcohol-sales-mappings
PUT /alcohol-sales-mappings/{id}
POST /alcohol-sales-mappings/{id}/deactivate
GET /production/stations
GET /production/stations/active
POST /production/stations
PUT /production/stations/{id}
POST /production/stations/{id}/deactivate
GET /production/items
GET /production/items/active
POST /production/items
PUT /production/items/{id}
POST /production/items/{id}/deactivate
PUT /production/items/{id}/permanent-override-par
GET /production/profiles
GET /production/profiles/active
POST /production/profiles
PUT /production/profiles/{id}
POST /production/profiles/{id}/deactivate
GET /production/profiles/{id}/lines
PUT /production/profiles/{id}/lines
GET /production/pos-menu-items
GET /production/pos-menu-items/active
POST /production/pos-menu-items
PUT /production/pos-menu-items/{id}
POST /production/pos-menu-items/{id}/deactivate
POST /production/pos-menu-items/import
POST /production/pos-menu-items/delete-by-skus
GET /production/product-mappings
POST /production/product-mappings
PUT /production/product-mappings/{id}
POST /production/product-mappings/{id}/deactivate
GET /production/freezer-pull/lines
PUT /production/freezer-pull/par
GET /production/weeks
POST /production/weeks/generate
GET /production/weeks/{id}/days
GET /production/weeks/{id}/lines
PUT /production/weeks/{id}/refresh
PUT /production/week-lines/overrides
GET /labour/positions
GET /labour/positions/active
POST /labour/positions
PUT /labour/positions/{id}
POST /labour/positions/{id}/deactivate
GET /labour/employees
POST /labour/employees
PUT /labour/employees/{id}
POST /labour/employees/{id}/deactivate
GET /labour/settings
PUT /labour/settings
GET /labour/weekly/{weekStartDate}
PUT /labour/weekly
GET /labour/weeks
GET /labour/daily/{workDate}
PUT /labour/daily
```

`/health` does not require database settings.

All other endpoints require backend-side PostgreSQL settings:

```powershell
$env:FOOD_INVENTORY_API_DB_URL = "jdbc:postgresql://<host>:5432/postgres?sslmode=require"
$env:FOOD_INVENTORY_API_DB_USER = "<api-db-user>"
$env:FOOD_INVENTORY_API_DB_PASSWORD = "<api-db-password>"
```

For a local proof only, the API can also load the existing Java properties file:

```powershell
$env:FOOD_INVENTORY_API_CONFIG_FILE = "$env:LOCALAPPDATA\FoodInventory\database.properties"
```

Optional port setting:

```powershell
$env:FOOD_INVENTORY_API_PORT = "8080"
```

Optional local diagnostics:

```powershell
$env:FOOD_INVENTORY_API_ERROR_DETAILS = "true"
```

Protected endpoints require:

```powershell
$env:FOOD_INVENTORY_API_KEY = "<shared-api-key>"
```

## Build

```powershell
mvn -f api\pom.xml test
```

Lambda deployment artifact:

```powershell
mvn -f api\pom.xml package
```

The Lambda fat jar is:

```text
api\target\foodinventory-api-lambda.jar
```

## Run Locally

Compile first:

```powershell
mvn -f api\pom.xml package
```

Run:

```powershell
java -jar api\target\foodinventory-api.jar
```

Then test:

```powershell
Invoke-RestMethod http://localhost:8080/health
Invoke-RestMethod http://localhost:8080/products -Headers @{"x-api-key" = $env:FOOD_INVENTORY_API_KEY}
```

Do not commit database credentials. Local environment variables are acceptable for development. AWS-side deployment should use Lambda environment variables, SSM Parameter Store `SecureString`, or AWS Secrets Manager.

As of October 4, 2026, production uses Secrets Manager dynamic references in Lambda environment configuration. The API template takes `RuntimeSecretArn`; database credentials and the API key are not ordinary deployment parameters. Do not commit `samconfig.toml`, command transcripts containing secret values, or local key files.

## Deploy To AWS Lambda

The production API uses:

```text
API Gateway HTTP API
-> Lambda
-> direct JDBC to AWS RDS PostgreSQL
```

Use [api/template.yaml](api/template.yaml) with AWS SAM. The template is the source of truth for the API Gateway/Lambda stack.

```powershell
cd api
mvn package
sam deploy --guided
```

For repeat local deploys, copy [api/samconfig.example.toml](api/samconfig.example.toml) to `samconfig.toml` and fill values locally. Do not commit `samconfig.toml`.

Template parameters:

```text
RuntimeSecretArn
ApiStageName
LocationAuthRequired
LambdaSubnetIds
LambdaSecurityGroupIds
ReservedConcurrency
LambdaLogRetentionDays
```

Routine API deployment uses the saved SAM configuration referencing the managed secret. Initial secret seeding preserved the deployed credentials; no API key or database password was rotated. CloudFormation resolves secret values at resource update time, not per request. Coordinate future credential rotation with an environment configuration update and a desktop release if the shared API key changes.

Infrastructure currently managed by SAM:

```text
API Gateway HTTP API
Lambda function
Lambda VPC attachment
Lambda runtime configuration
API stage name
Lambda invoke permission
API URL output
```

Additional infrastructure adopted under `infra/`:

```text
AWS RDS instance
VPC/subnets/security groups
Database subnet group and enhanced monitoring role
Secrets Manager API credentials
Operational alarms, backup retention and deletion protections
```

See [Infrastructure and recovery](#30-infrastructure-and-recovery). These application/operator workflows remain separate:

```text
Database schema migrations
GitHub release secrets
Per-device authentication
```

Recommended initial `ReservedConcurrency` on small/free-tier accounts:

```text
0
```

Use `0` to leave function-level reserved concurrency unset. On small AWS accounts, setting function-level reserved concurrency can fail if it would reduce unreserved account concurrency below AWS minimums. The account-level Lambda concurrency limit still prevents unlimited scale for this deployment.

RDS security group rule:

```text
Inbound PostgreSQL 5432
Source: Lambda security group
```

Lambda security group rule:

```text
Outbound PostgreSQL 5432
Destination: RDS security group
```

Avoid adding a NAT Gateway without an explicit outbound-internet requirement. If Lambda only needs to reach RDS and uses environment variables for database settings, NAT is not needed for normal endpoint execution.

---

# 30. Infrastructure and Recovery

Verified in AWS account `863819358995`, region `ca-central-1`, on October 4, 2026.

## What is managed

| Stack or definition | Scope |
| --- | --- |
| `esm-operations-api` / `api/template.yaml` | API Gateway, Lambda, IAM execution role, permissions, runtime settings, and log retention |
| `esm-operations-network` / `infra/network.template.json` | Existing VPC, three subnets, database access group, internet gateway, main route table, and internet route |
| `esm-operations-infrastructure` / `infra/infrastructure.template.json` | Existing RDS instance, subnet group, Lambda security group, and enhanced monitoring role |
| `esm-operations-secrets` / `infra/secrets.template.json` | Existing application credentials and API key, retained in Secrets Manager |
| `esm-operations-monitoring` / `infra/monitoring.template.json` | Database CPU/storage and unhandled Lambda error alarms |
| `infra/budget.json` | Existing USD 30 monthly budget; limit updated in place without replacing its notifications |

`infra/environments.json` records account, region, stacks, and intentional external dependencies. The live and TEST stores remain separate locations within one deployment/database. No separate dev/test AWS environment was created.

The migration imported existing resources; it preserved physical resource IDs, database endpoint, API URL, credential values, public developer access rules, and store records. It then increased RDS retention from one to seven days and enabled deletion protection. No schema changes or store provisioning were run as part of infrastructure adoption.

## Everyday use

1. Open **Admin Tools.cmd**.
2. For ordinary API code changes, use **Deploy API (usual update)**. No infrastructure Plan/Apply is required. The saved SAM configuration references `RuntimeSecretArn`, not raw credentials.
3. For AWS configuration changes, first edit/review the relevant template.
4. Choose **Plan infrastructure changes** in the maintenance picker and run it. Review additions/modifications in the task window.
5. Close the task window. Choose **Apply reviewed infrastructure changes**, run it, and type APPLY after review.
6. Use **AWS infrastructure status** to check stacks and alarms.

Plans expire after 24 hours and are refused if templates change after planning. Each plan is retained under ignored `.admin/` files. The tool refuses resource removals and replacements. Positive-to-positive RDS backup retention changes have a narrow reviewed exception; an independent CloudFormation stack policy forbids replacing/deleting the Database resource.

The database uses `DeletionPolicy: Retain` and `UpdateReplacePolicy: Retain`, RDS deletion protection, and stack-policy protection. Managed infrastructure stacks have termination protection. These prevent accidental destructive deployment; intentional retirement needs a separate explicit procedure.

The migration helpers under `infra/Import-*`, `Seed-ApiSecret.ps1`, and `Enable-ManagedApiSecrets.ps1` are one-time adoption tools, not everyday deployment steps. Do not rerun them against the adopted resources. Use the launcher.

## Credentials and monitoring

The retained secret `storeops/production/api-credentials` contains the existing restricted database username/password, JDBC URL, and API key. Initial seeding used a `NoEcho` parameter and a temporary file restricted to the Windows operator; the temporary file was removed afterward. No credentials were generated or rotated. CloudFormation resolves dynamic references when updating Lambda configuration; the Java service still receives the values as environment variables. This is not per-request secret retrieval or automatic password rotation.

Future credential rotation must update the database/secret consistently, explicitly refresh Lambda environment configuration, and update desktop packaging if the API key changes. A code-only deployment is not a substitute for deliberately refreshing secret-bearing environment configuration. Do not change the seed parameter during routine infrastructure deployment; it uses its previous value.

The previous SAM configuration was preserved as a Windows-user-encrypted `.admin/samconfig-*.encrypted` backup. Do not restore the old credential-parameter format against the new API template. Passwords for RDS administrator and store logins are still prompted in store-management tasks. AWS operator credentials remain managed by AWS CLI, outside the application templates.

Three CloudWatch alarms were added. Their optional `AlarmTopicArn` is empty, so they do not send notifications. CPU/storage alarms cover RDS; Lambda Errors counts unhandled invocation errors, not every handled HTTP error response. Secrets Manager and alarms are billable resources; the existing USD 30 budget remains unchanged.

## Recovery and scope limits

RDS automated backups retain seven days and report a latest restorable time. No restore drill or new recovery instance was created during this migration. Templates preserve infrastructure configuration; backups preserve actual database contents.

For recovery:

1. Check RDS automated backups and the available recovery window in AWS.
2. Restore into a **new, separate instance**. Keep the current instance and its protection settings.
3. Verify schema, data, location isolation, and affected workflows on the restored instance.
4. Prepare a reviewed cutover for database endpoint/secret changes and Lambda environment refresh.
5. Adopt the restored instance with revised identifiers after successful validation; do not replace the live Database resource through the routine deployment tool.

The templates are an adopted baseline for this existing account, not an unattended new-account installer. AWS-managed KMS keys, default parameter/option groups, operator credentials, SAM's managed packaging stack, and implicit default-VPC relationships remain AWS/account dependencies. CloudFormation cannot import the existing VPC gateway attachment or implicit main-route associations; these are recorded dependencies. The named default security group and physical IDs must be reviewed for any fresh environment rather than copied blindly.

Database schema migrations, restricted database role grants, and store records remain versioned application/admin operations. They are not deployed as CloudFormation resources, and ordinary infrastructure deployment does not create/reset stores. Custom domains, new authentication models, cross-region recovery, and a separate test environment are future features, not existing resources left unmigrated.

## Validation

- All infrastructure templates were validated by CloudFormation; the API template passed SAM lint validation.
- All five application stacks passed final drift detection with `IN_SYNC`; a fresh infrastructure plan found no remaining changes.
- API package tests passed; the ordinary SAM deployment path was exercised using managed-secret configuration.
- Offline admin launcher and infrastructure safety checks passed.
- Post-change checks verified RDS availability, seven-day retention/deletion protection, API health, store-token enforcement, schema version 23, and both live/TEST store entries.

Use `scripts/Test-Infrastructure.ps1` and `scripts/Test-AdminLauncher.ps1` for offline checks. `Release.ps1` remains unchanged.


## Account and Runtime Baseline

The following baseline is documented evidence from prior checks, not a fresh live-state verification. Current templates and the environment manifest govern configuration changes. Historical spend and resource status values are dated observations.

### Current AWS Account

Current deployment account discovered with AWS CLI:

```text
Account: 863819358995
Deploy IAM user: arn:aws:iam::863819358995:user/esm-operations-deploy
Default region: ca-central-1
```

Do not commit AWS access keys, database passwords, API keys, or copied command output that includes secret values.

### Current Monthly Budget

AWS Budgets currently has a monthly cost budget:

```text
Budget name: My Monthly Cost Budget
Limit: 30.00 USD monthly
Status: HEALTHY
Actual spend when checked: 3.269 USD
Checked on: Sunday, September 6, 2026
```

Keep this budget in place before adding new AWS resources. The highest-risk cost items for this project are larger RDS instances, Multi-AZ RDS, NAT Gateway, RDS Proxy, long CloudWatch log retention, and duplicated per-store RDS instances.

### Managed By IaC Today

The API stack is managed by AWS SAM / CloudFormation:

```text
Template: api/template.yaml
Example deploy config: api/samconfig.example.toml
Stack name: esm-operations-api
Stack region: ca-central-1
Stack status: UPDATE_COMPLETE
API URL: https://rn0j30p2vf.execute-api.ca-central-1.amazonaws.com/prod
```

SAM-managed resources:

```text
AWS::ApiGatewayV2::Api       FoodInventoryHttpApi        rn0j30p2vf
AWS::ApiGatewayV2::Stage     FoodInventoryHttpApiStage   prod
AWS::Lambda::Function        FoodInventoryApiFunction    esm-operations-api-FoodInventoryApiFunction-HoikXKwCH2C0
AWS::Lambda::Permission      FoodInventoryApiFunctionApiProxyPermission
AWS::IAM::Role               FoodInventoryApiFunctionRole
```

The SAM template currently takes these deployment parameters:

```text
ApiStageName
RuntimeSecretArn
LambdaSubnetIds
LambdaSecurityGroupIds
ReservedConcurrency
LocationAuthRequired
```

Database credentials and the API key are now stored in Secrets Manager. The template resolves them through `RuntimeSecretArn`; normal SAM deployment configuration contains no raw database password or API key. The one-time seed used a `NoEcho` parameter and a temporary file restricted to the Windows operator.

### Current API Runtime

The deployed Lambda configuration:

```text
Function name: esm-operations-api-FoodInventoryApiFunction-HoikXKwCH2C0
Runtime: java25
Handler: ca.foodinventory.api.LambdaApiHandler::handleRequest
Memory: 512 MB
Timeout: 30 seconds
State: Active
```

Configured environment variable names:

```text
FOOD_INVENTORY_API_DB_URL
FOOD_INVENTORY_API_DB_USER
FOOD_INVENTORY_API_DB_PASSWORD
FOOD_INVENTORY_API_KEY
FOOD_INVENTORY_API_ERROR_DETAILS
FOOD_INVENTORY_LOCATION_AUTH_REQUIRED
```

Do not print full Lambda environment variables in shared notes because they include secrets.

Current Lambda log group:

```text
/aws/lambda/esm-operations-api-FoodInventoryApiFunction-HoikXKwCH2C0
Retention: 30 days
Stack resource: FoodInventoryApiFunctionLogGroup
```

The SAM template declares this log group with configurable retention:

```text
Parameter: LambdaLogRetentionDays
Default: 30
Resource: FoodInventoryApiFunctionLogGroup
```

Import status:

```text
Imported into stack: Sunday, September 6, 2026
Stack status after import/update: UPDATE_COMPLETE
Verified retention after import/update: 30 days
Drift status after import/update: IN_SYNC
API location auth after v4.0.0 deployment: required
Labour API routes deployed: Sunday, September 6, 2026
Labour API route updates for `/labour/weeks`, Daily Labour Cost, Tip Pool, and Tip Pool Breakdown support fields deployed: Monday, September 7, 2026
Protected-password API routes deployed: Thursday, September 24, 2026
```

### Current Database

Current AWS RDS instance:

```text
DB instance identifier: esm-operations-db
Engine: postgres
Engine version: 18.3
Instance class: db.t4g.micro
Status: available
Multi-AZ: false
Storage: 20 GB gp2
Endpoint: esm-operations-db.cdcok68as3cr.ca-central-1.rds.amazonaws.com
DB subnet group: default-vpc-0b41e3692165238ed
VPC security group: sg-056216eb5a750ccfa
```

This existing RDS instance was imported into `esm-operations-infrastructure` on October 4, 2026. The import did not recreate it. Current backup retention is seven days and deletion protection is enabled.

The live RDS application schema is currently at version 23. Labour Management, multi-location, location-aware uniqueness, protected-password, and labour pay-rate schema setup paths remain controlled database migrations, separate from infrastructure deployment. The restricted `operations_app` runtime user cannot create or alter schema-owned structures in the `public` schema. Use the secure prompt-based migration helpers with the schema-capable RDS admin user; do not store the admin password in repo files or Lambda environment variables.

For the current budget, keep the database small and Single-AZ unless the budget is intentionally changed.

### Current Network

Current VPC:

```text
VPC: vpc-0b41e3692165238ed
```

Lambda subnets used by the SAM stack:

```text
subnet-02d952055a4433d04  ca-central-1a  172.31.16.0/20
subnet-05226760382273fff  ca-central-1b  172.31.0.0/20
```

Current Lambda security group:

```text
Security group: sg-09e7c97add719b857
Name: esm-operations-api-lambda-sg
Ingress: none
Egress: tcp/5432 to RDS security group sg-056216eb5a750ccfa
```

Current RDS security group:

```text
Security group: sg-056216eb5a750ccfa
Name: default
Ingress:
  - tcp/5432 from Lambda security group sg-09e7c97add719b857
  - tcp/5432 from 72.38.209.198/32
  - all traffic from itself
Egress:
  - all traffic to 0.0.0.0/0
```

Recommended future hardening:

- Create a dedicated RDS security group instead of using the default security group.
- Keep Lambda-to-RDS PostgreSQL access explicit.
- Remove direct public/client PostgreSQL ingress when direct database fallback is no longer needed.
- Avoid NAT Gateway unless a specific Lambda outbound internet requirement appears.

### Release Automation

Desktop releases are scripted by:

```text
Release.ps1
```

This script handles:

- Maven project version update.
- Git commit, push, and tag.
- Release API config generation from local secret input.
- Maven package.
- `jpackage` Windows installer creation.
- GitHub Release publishing.

This is automation, not cloud IaC. Keep it separate from AWS infrastructure deployment.

### Existing Operational Scripts

The Windows entry point for API deployment and store administration is now **Admin Tools.cmd**. It opens a graphical launcher with named actions and store selection by name, saved non-secret settings, and retained activity history. See [Admin Tools instructions](docs/ADMIN_TOOLS.md). The existing scripts remain the implementation, and `Release.ps1` remains unchanged.

Current script:

```text
scripts/Create-AwsRdsAppUser.ps1
```

Purpose:

- Create or repair the restricted AWS RDS application database user.
- Grant normal table/sequence access.

Database role grants remain an explicit administrative script operation, separate from the adopted CloudFormation infrastructure.

---

# 31. Administration

Open **Admin Tools.cmd** for store maintenance, API deployment, infrastructure plans/status, and the in-app Guide. The operator walkthrough is [docs/ADMIN_TOOLS.md](docs/ADMIN_TOOLS.md); the launcher reads [docs/ADMIN_QUICK_GUIDE.txt](docs/ADMIN_QUICK_GUIDE.txt). These are user-facing operating instructions, not competing technical references.

Store creation, login credential reset, naming, setup-copy, schema migration, and restricted-role grants remain explicit administrative operations. Store-login credentials are separate from store-scoped System and Labour Setup protected passwords. Setup copy excludes operational history, passwords, and sessions. The launcher requires an empty destination; direct scripts retain their documented replacement behavior unless their safety flags are supplied.

Use the in-app System workflow for cloud protected-password changes. Legacy local reset scripts affect SQLite/development fallback only. Do not reintroduce plaintext passwords or desktop PostgreSQL credentials.

# 32. Deferred Enhancements and Accepted Limits

- Inventory count template synchronization: additive missing lines only, preserve quantities and completed history.
- Supplier combo-case invoice splits: map one supplier SKU to child inventory products with quantity and cost allocation; do not double-count the original line. Proposed fields are supplier, supplier SKU, child product, quantity per imported unit, cost percentage/ratio, and active flag.
- Food theoretical usage/variance, recipe costing, yield analysis, historical trends, and cross-location reporting remain future work.
- A browser frontend is a future option over the existing Java API; no immediate JavaFX rewrite or repo restructure is planned.
- Live screen refresh/edit-conflict handling, local backup status/export polish, updater retry polish, stronger per-user authentication, separate AWS environments, and a recovery drill require explicit future scope.
- Full billed weighted purchase cost is accurate; quantities/cost per lb or kg are approximate because finalized exports lack actual weight. This is an accepted limit, not an unresolved importer bug.
- Alcohol variance, Labour Management, multi-location Store Login, shared POS catalog/KDS handling, and Freezer Pull are implemented. Modify accepted workflows only for specific requested changes or observed issues.

# 33. Documentation and New Sessions

Start a development session by reading **PROJECT_REFERENCE.md** and the latest **RELEASE_HISTORY.md** entry. Inspect the exact implementation files before editing. Preserve JavaFX, Cloud API, RDS, SQLite snapshots, migrations, printing, packaging, and update behavior. Make targeted changes and run the checks appropriate to the changed component.

Current documents have distinct purposes:

| Document | Purpose |
| --- | --- |
| [PROJECT_REFERENCE.md](PROJECT_REFERENCE.md) | Single current technical reference, implementation rules, schema, API, infrastructure, state, and priorities |
| [HOW_TO_USE.md](HOW_TO_USE.md) | Manager training and day-to-day workflows |
| [RELEASE_HISTORY.md](RELEASE_HISTORY.md) | Canonical change history; unreleased work stays marked unreleased |
| [docs/ADMIN_TOOLS.md](docs/ADMIN_TOOLS.md) | Operator instructions for the launcher |
| [docs/ADMIN_QUICK_GUIDE.txt](docs/ADMIN_QUICK_GUIDE.txt) | Text displayed by the launcher's Guide button |
| [Releases/ReleaseHistory.md](Releases/ReleaseHistory.md) | Existing release-script append log; retained because Release.ps1 writes it |
| [api/README.md](api/README.md) | Entry link to this reference for API developers |
| [docs/compensation-pitch.md](docs/compensation-pitch.md) | Business presentation, maintained for that audience |
| [docs/archive/2026-10-08/README.md](docs/archive/2026-10-08/README.md) | Superseded documentation snapshots and earlier design decisions |

Keep the technical reference, affected user/operator guide, and change history synchronized when behavior changes. Do not revive separate technical status/schema/roadmap files. The source POMs identify built versions; release notes describe planned changes but do not establish deployment or publication. Archive contents are historical and must not override this reference or the implementation.
