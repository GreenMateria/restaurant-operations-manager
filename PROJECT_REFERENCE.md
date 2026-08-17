# PROJECT_REFERENCE.md

_Last Updated: Monday, August 10, 2026_

This document is the primary reference for the **ESM Operations Manager** project.

Read this first when starting a new development session, using Codex in IntelliJ, or making changes that affect multiple modules.

---

# 1. Project Overview

**Application name:** ESM Operations Manager  
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
- PostgreSQL on Aiven for optional cloud database mode
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
- SQLite is the default startup mode.
- Cloud PostgreSQL mode is selected through `%LOCALAPPDATA%\FoodInventory\database.properties` and can be changed from the password-protected System module.
- Fresh installs seed `database.properties` from safe bundled defaults when the local config file does not exist; cloud credentials must be supplied locally outside source control.
- For the current work PCs, Aiven PostgreSQL is the normal operating mode and the cloud database is the accurate master data source.
- Upload/download cloud sync controls are administrator migration/recovery tools, not routine daily sync actions.
- Never delete, replace, or reset the runtime database unless explicitly requested.
- Database schema changes must be made through migrations.

---

# 4. Application Architecture

The application follows this practical layered structure:

```text
UI View
   ↓
Service / Business Logic
   ↓
DAO
   ↓
SQLite Database or Aiven PostgreSQL
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
2. Initializes the selected startup database mode.
3. Runs pending SQLite migrations or validates/initializes PostgreSQL schema.
4. Loads the main dashboard.
5. Displays the installed application version.
6. Starts a background GitHub update check.
7. Shows an update dialog when a newer release is available.

The startup update check does not block the main UI.

---

# 6. Main Modules

The dashboard is organized around these operational areas:

- Food Inventory
- Alcohol Inventory
- Supplies Inventory
- Purchasing
- Reports
- Sales
- Production
- System

All module views should provide a clear route back to the dashboard.

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
- Alcohol manual invoice adjustments for non-inventory charges
- Exact paper HST and bottle deposit entry for accounting accuracy
- Automatic reconciliation of remaining invoice differences into merchandise categories
- Alcohol inclusion in valuation and weekly cost reporting

Remaining priorities focus on validation and workflow polish:

1. Continue validating alcohol valuation against live purchasing data.
2. Continue refining alcohol cost reporting behaviour.
3. Continue alcohol order guide workflow review.
4. Finalize alcohol-specific printing and user workflow polish where needed.

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

# 17. Printing Standards

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

# 18. Backup and Restore

The System module includes database backup and restore.

Backup and restore operate on the active runtime database.

Important rules:

- Confirm the active database path before troubleshooting.
- Do not treat the project-root database as the packaged database.
- Restore actions should be protected and clearly confirmed.
- Avoid restoring a database over a newer schema without migration support.

---

# 19. Admin Password and Settings

The application uses a settings table for protected system functions.

Known settings include:

- Admin password hash
- Password initialized flag

Passwords must not be stored in plain text.

---

# 20. GitHub Update System

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
- Downloads the installer inside the application.
- Displays download progress.
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

Planned UI enhancement:

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

# 21. Versioning and Releases

The Maven project version is the application version source.

Release workflow is automated through:

```text
Release.ps1
```

Typical release process:

1. Enter version.
2. Enter release notes.
3. Update project version.
4. Run Maven build.
5. Build Windows installer with `jpackage`.
6. Update release history.
7. Commit changes.
8. Create Git tag.
9. Push code and tag.
10. Publish installer to GitHub Releases.
11. Copy installer to the local Releases folder.

Installer format:

```text
ESM Operations Manager-<version>.exe
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

# 22. Current Project State

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
- Controlled SQLite/PostgreSQL mode switching
- Password-protected cloud upload/download tools
- Persistent Order Guide case-size overrides
- Background cloud-mode save/import handling for inventory count saves/completions, sales report imports/saves, and Weekly Production usage imports

All implemented features through version 3.0.2 are considered working as intended unless a future issue is reported with a specific workflow, error, or data case.

Primary unfinished areas:

- Alcohol workflow validation and polish against live data
- Production-to-inventory variance
- Recipe costing
- Yield tracking
- Advanced reporting
- Inventory count template synchronization

---

# 23. Development Priorities

## Immediate

1. Package and publish the next installer with the v3.0.2 cloud performance and POS sales import fixes.
2. Validate the v3.0.2 installer upgrade on the work PCs.
3. Continue monitoring daily Cloud PostgreSQL use on the configured work PCs.
4. Add a manual Check for Updates action.
5. Continue production variance groundwork.

## Medium Term

1. Finish production item to inventory product mappings.
2. Build theoretical usage calculations.
3. Build production variance reporting.
4. Add recipe and yield management.
5. Improve advanced report filtering.

## Long Term

1. Recipe costing.
2. Theoretical food cost.
3. Actual-versus-theoretical variance.
4. Historical trend reporting.
5. Multi-location support if ever required.
6. Broader role and permission controls.

---

# 24. Important Design Principles

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

# 25. Build and Verification

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

# 26. New Session Instructions

When starting a new AI or Codex session, provide this instruction:

```text
Read PROJECT_REFERENCE.md, PROJECT_STATUS.md, DATABASE_SCHEMA.md,
CODING_STANDARDS.md, ROADMAP.md, and RELEASE_HISTORY.md.

Treat PROJECT_REFERENCE.md as the master functional overview.
Inspect the exact Java files involved before changing code.
Preserve the existing JavaFX, SQLite, Maven, migration, printing,
packaging, and GitHub update architecture.
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

# 27. Document Set

This project documentation set should include:

```text
PROJECT_REFERENCE.md
PROJECT_STATUS.md
DATABASE_SCHEMA.md
CODING_STANDARDS.md
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

## ROADMAP.md

Prioritized future work.

## RELEASE_HISTORY.md

Version-by-version changelog.

Keep all files synchronized when major modules, migrations, workflows, or releases change.
