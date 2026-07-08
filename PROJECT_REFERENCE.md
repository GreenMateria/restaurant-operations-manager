# Food Inventory Project Reference

Use this file to restore context in a new chat. A good prompt is:

`Read PROJECT_REFERENCE.md and continue working on the Food Inventory app.`

## Project Overview

- JavaFX desktop app for East Side Mario's inventory operations.
- Maven project.
- Main app package: `ca.foodinventory`.
- Entry points:
  - `src/main/java/ca/foodinventory/Launcher.java`
  - `src/main/java/ca/foodinventory/MainApp.java`
- Main navigation shell:
  - `src/main/java/ca/foodinventory/ui/MainView.java`
- Database:
  - SQLite via `org.xerial:sqlite-jdbc`.
  - Runtime database path is under `%LOCALAPPDATA%/FoodInventory/food_inventory.db`.
  - Root-level `food_inventory.db` may exist but is not the runtime path used by `DatabaseManager`.
- Build verification command:
  - `mvn clean test`
- Latest known verification:
  - `mvn clean test` passes after the WeeklyProductionView printable layout rewrite and one-page prep sheet fixes.

## Architecture Pattern

- UI classes live in `src/main/java/ca/foodinventory/ui`.
- DAO classes live in `src/main/java/ca/foodinventory/dao`.
- Data models live in `src/main/java/ca/foodinventory/model`.
- Business/import/reporting services live in `src/main/java/ca/foodinventory/service`.
- Database setup and migrations live in `src/main/java/ca/foodinventory/database`.
- JavaFX screens are mostly hand-built views using `BorderPane`, `VBox`, `GridPane`, `TableView`, dialogs, and `FileChooser`.
- Most screens follow this flow:
  - UI view or dialog collects input.
  - DAO saves/loads from SQLite.
  - `PropertyValueFactory` binds table columns to JavaBean-style getters.

## Main App Areas

- Food Inventory
  - Products
  - Import Invoice
  - Manual Invoice
  - Count Templates
  - Inventory Counts
  - Order Guide
- Alcohol Inventory
  - Manual Invoice
  - Products
  - Count Templates
  - Inventory Counts
  - Order Guide
- Supplies Inventory
  - Manual Invoice
  - Products
  - Count Templates
  - Inventory Counts
  - Order Guide
- Production
  - Production Items
  - Production Stations
  - POS Menu Items
  - Production Profiles
  - Product Mappings
  - Weekly Production placeholder
  - Freezer Pull
  - Variance Reports placeholder
- Reporting
  - Invoice History
  - Inventory
  - Sales
- System
  - Admin/password gated system utilities and database backup.

## Important Existing Services

- `GfsCsvImportService`
  - Reads GFS invoice CSVs.
- `GfsProductImportService`
  - Imports/updates products from GFS order guide CSVs.
- `PosSalesImportService`
  - Imports sales report `.xlsx` files for Reporting > Sales.
  - Uses Apache POI `XSSFWorkbook`.
- `InventoryValuationService`
  - Builds inventory valuation lines.
- `InventoryCostService`
  - Builds weekly/category cost reporting.
- `OrderGuideService`
  - Builds order guide rows.
- `DatabaseBackupService`
  - Handles database backup operations.
- `ProductionUsageReportImportService`
- Production Usage Report `.xlsx` import, described below.
- `PosMenuItemImportService`
  - Imports initial POS Menu Item setup from a sales mix/menu item `.xlsx`.
  - Reads first sheet, column A as menu item name, column B as PLU/SKU.
  - Skips rows where column B is blank.
  - Skips KDS-only rows between marker rows containing `kds dnu.ESM` and `Gifts and Selling Suppli.ESM`.
  - Can extract PLU/SKU values from that KDS section for cleanup.
  - Inserts new POS menu items and updates existing item names by PLU/SKU while preserving production profile assignments.
- `ProductionReportService`
  - Converts imported POS usage rows into generated production totals.
  - Resolves POS SKUs to production profiles, expands active profile lines, and rolls totals up by production item/station/day/week.
- `FreezerPullView`
  - Dedicated Production submenu screen for freezer pull output.
  - Imports the same Usage Report `.xlsx`.
  - Generates production profile quantities and filters to Production Items assigned to the `Freezer Pull` station.
  - Displays and prints freezer pull quantities by Monday through Sunday plus weekly total.
- `ProductionWeekDao`
  - Saves generated production weeks to `production_weeks`, `production_week_days`, and `production_week_lines`.
  - Replaces an existing week if the same week start/end is imported again.
  - Stores each day separately, with its own production week day record and line table data.

## Database And Migrations

- `DatabaseManager.initializeDatabase()` creates the base schema and runs `DatabaseMigrationRunner`.
- Current schema version in `DatabaseMigrationRunner` is `8`.
- Migration files:
  - `Migration2`
  - `Migration3`
  - `Migration4`
  - `Migration5`
  - `Migration6`
  - `Migration7`
  - `Migration8`
- Migration repair logic exists for missing schemas after version upgrades.
- Migration 6 added production tables:
  - `production_stations`
  - `production_items`
  - `production_profiles`
  - `production_profile_lines`
  - `pos_menu_items`
  - `production_item_product_mappings`
  - `production_weeks`
  - `production_week_days`
  - `production_week_lines`
- Migration 7 added:
  - `production_items.shelf_life`
- Migration 8 added:
  - `production_stations.prep_sheet`
  - Existing/default station prep sheet assignment:
    - `Pizza` and `Salad` default to `Pizza Salad`
    - Other existing stations default to `Main Line`

## Production Module Status

- Production setup screens exist and compile:
  - Production Stations
  - Production Items
  - Production Profiles
  - POS Menu Items
  - Product Mappings
- Production item saving was manually retested and is working.
- `POS Menu Items` can assign each POS SKU/PLU to a production profile.
- `POS Menu Items` can import menu item setup from a sales mix/menu item `.xlsx`.
  - Button: `Import Menu Items`.
  - Button: `Delete KDS Items` selects a salesmix `.xlsx`, finds PLU/SKUs between `kds dnu.ESM` and `Gifts and Selling Suppli.ESM`, confirms, and deletes matching POS Menu Items.
  - Column A: menu item name.
  - Column B: PLU/SKU.
  - Blank PLU/SKU rows are skipped.
  - KDS-only rows are skipped between marker rows containing `kds dnu.ESM` and `Gifts and Selling Suppli.ESM`.
  - Existing PLU/SKU rows keep their production profile assignment.
- `Product Mappings` can map production items to inventory products for future usage/variance reporting.
- Import Usage Report in POS Menu Items still opens a display-only generated production report dialog.
- Weekly Production is now a real screen in `MainView`.
- Freezer Pull is a dedicated screen in `MainView`.
  - It imports a Usage Report and generates only production lines assigned to the `Freezer Pull` station.
  - It prints a separate `FREEZER PULL` sheet with `ITEM`, `UNIT`, Monday-Sunday quantities, and `TOTAL`.
  - Print uses explicit letter portrait `PageLayout` and a scaled wrapper node to avoid blank print-to-PDF output.
  - Printable columns/fonts/rows are sized down to fit portrait output.
  - Scaled print output is centered within the printable page area.
  - Print scaling is capped below full size to avoid clipping on PDF/letter output.
  - Freezer Pull lines use the unit from the Production Profile line, so pull units can differ from normal prep item units.
- Weekly Production excludes any generated or manual/zero production item assigned to the `Freezer Pull` station.
- Weekly Production imports a Usage Report, saves a generated production week, and displays separate tabs/tables for Monday through Sunday.
- Weekly Production has an `Include all active production items` checkbox, selected by default.
  - When selected, active Production Items with no imported POS usage are still listed on every daily prep tab.
  - Those manual/zero-sales lines start with previous sales quantity `0`, generated par `0`, final par `0`, and can be manually populated through `Override Par`.
- Weekly Production can preview and print the selected daily production list.
  - Buttons: `Preview Prep Sheet`, `Print Prep Sheet`.
  - A `Prep Sheet` selector filters the selected day table and printout by kitchen area.
  - Each day tab keeps all loaded lines internally, so switching prep sheets does not discard override edits.
  - Production Stations have a `Prep Sheet` field, so stations can be assigned to printable areas such as `Main Line` or `Pizza Salad`.
  - Printed layout is based on the `LINE PREP` worksheet from `src/main/resources/2024 - PREP(NEW).xlsm`.
  - Columns: `ITEM`, `UNIT`, `LIFE`, day name, `COUNT`, `TO DO`, `INITIAL`.
  - The generated `final_par` prints under the day-name column.
  - Production Item shelf life prints in the `LIFE` column.
  - `COUNT`, `TO DO`, and `INITIAL` are blank for kitchen use.
  - Station names print as category bands across the table.
  - Preview uses the same printable layout so the user can inspect it before printing.
  - Print uses explicit letter portrait `PageLayout` and a scaled wrapper node to avoid blank print-to-PDF output.
  - Printable columns/fonts/rows are sized down to fit portrait output.
  - Scaled print output is centered within the printable page area.
  - Print scaling is capped below full size to avoid clipping on PDF/letter output.
- Large production dropdowns are searchable/editable:
  - Product Mappings > Production Item
  - Product Mappings > Inventory Product
  - Production Profiles > Production Item
  - POS Menu Items > Production Profile
- Production Item units include common prep units:
  - `EA`, `PORTION`, `LB`, `OZ`, `KG`, `G`, `L`, `ML`, `QT`, `GAL`, `BATCH`, `TRAY`, `PAN`, `1/9 PAN`, `1/6 PAN`, `1/3 PAN`, `BAG`, `BOTTLE`
  - The unit dropdown remains editable for custom units.
- When a Production Item is assigned to the `Freezer Pull` station, the unit dropdown switches to pull-style units:
  - `2 KG BAG`, `1 KG BAG`, `BAG`, `BOX`, `CASE`, `PORTION BAG`, `TRAY`, `TUB`, `PACK`
- Production Profile lines automatically use the Freezer Pull item unit when a Freezer Pull item is selected.
- Production Items have optional `Shelf Life`, used as the cook labeling reminder on printed prep sheets.
- Production Stations have a `Prep Sheet` assignment used to split Weekly Production printouts by kitchen area.
- Variance Reports UI is still a placeholder in `MainView`.

## Production Setup Workflow

1. Create or verify Production Stations.
2. Create Production Items.
3. Create Production Profiles.
   - A profile is the recipe for a sold POS item.
   - Profile lines define production item plus quantity per sale.
4. Create POS Menu Items.
   - `posSku` stores the POS SKU/PLU.
   - Each POS menu item can be assigned to a production profile.
5. Optionally create Product Mappings.
   - These map production items to inventory products for later usage and variance reporting.

For Freezer Pull:
- Create freezer-pull Production Items and assign them to the `Freezer Pull` station.
- Add those freezer-pull items to the relevant Production Profiles with quantity-per-sale values.
- Use the Production Profile line unit for the pull unit, such as `2 KG BAG`.
- Production > Freezer Pull imports the Usage Report and prints only those freezer-pull lines.

## Production Usage Report Import

- Button name: `Import Usage Report`.
- Location: Production > POS Menu Items.
- File type: `.xlsx`.
- Opens a JavaFX file browser.
- Reads the first sheet.
- Fixed column mapping:
  - SKU/PLU: column B
  - Monday qty sold: column D
  - Tuesday qty sold: column F
  - Wednesday qty sold: column H
  - Thursday qty sold: column J
  - Friday qty sold: column L
  - Saturday qty sold: column N
  - Sunday qty sold: column P
  - Weekly qty sold: column R
- The importer only imports rows where column B matches an active POS SKU configured in POS Menu Items.
- Unconfigured SKUs are ignored completely.
- KDS-only rows are skipped between marker rows containing `kds dnu.ESM` and `Gifts and Selling Suppli.ESM`.
- Current import result:
  - configured POS rows imported
  - weekly quantity sold
- It generates a display-only production report:
  - production item
  - station
  - unit
  - Monday through Sunday production quantities
  - weekly production quantity
- POS Menu Items import does not save imported rows or generated production report lines.

## Weekly Production

- Location: Production > Weekly Production.
- User selects:
  - production week start date
  - par multiplier, default `1.25`
  - whether to include all active production items, default selected
  - Usage Report `.xlsx`
- Import flow:
  1. Reads configured POS rows through `ProductionUsageReportImportService`.
  2. Generates production totals through `ProductionReportService`.
  3. Saves a production week through `ProductionWeekDao`.
  4. Creates seven separate `production_week_days` records, Monday through Sunday.
  5. Saves each day's generated lines separately in `production_week_lines`.
     - If `Include all active production items` is selected, zero-quantity lines are also saved for manual override.
- Each day is displayed in its own tab/table, not as one combined weekly table.
- `previous_sales_quantity` stores the generated production quantity for that day.
- `generated_par` is `ceil(previous_sales_quantity * par_multiplier)`.
- `override_par` is editable in each daily table.
- `final_par` uses override par when present, otherwise generated par.
- `Save Overrides` persists `override_par` and `final_par` for all visible day tabs.



### July 2026 - Weekly Production Print Rewrite

- `WeeklyProductionView` printable layout was substantially rewritten after JavaFX print scaling issues.
- Prep sheets are now designed to print as **one page per prep sheet** instead of using a fixed row-count pagination.
- The previous hard-coded 32-row page split has been removed.
- Printing now relies on automatic fit-to-page scaling rather than manual pagination.
- The printable layout was compacted (reduced padding, row heights and font sizes) to maximize usable space.
- This implementation is now considered the baseline. Avoid reintroducing hard-coded page limits or the previous multi-page logic unless specifically requested.
- If future print adjustments are required, modify the printable layout dimensions before changing print scaling.

## Production Files

- `src/main/java/ca/foodinventory/model/PosMenuItem.java`
- `src/main/java/ca/foodinventory/dao/PosMenuItemDao.java`
- `src/main/java/ca/foodinventory/ui/PosMenuItemsView.java`
- `src/main/java/ca/foodinventory/ui/PosMenuItemDialog.java`
- `src/main/java/ca/foodinventory/service/PosMenuItemImportService.java`
- `src/main/java/ca/foodinventory/model/PosMenuItemImportSummary.java`
- `src/main/java/ca/foodinventory/service/ProductionUsageReportImportService.java`
- `src/main/java/ca/foodinventory/model/ImportedUsageReportLine.java`
- `src/main/java/ca/foodinventory/model/ImportedUsageReportSummary.java`
- `src/main/java/ca/foodinventory/model/ProductionReportLine.java`
- `src/main/java/ca/foodinventory/model/ProductionReportSummary.java`
- `src/main/java/ca/foodinventory/model/ProductionWeek.java`
- `src/main/java/ca/foodinventory/model/ProductionWeekDay.java`
- `src/main/java/ca/foodinventory/model/ProductionWeekLine.java`
- `src/main/java/ca/foodinventory/service/ProductionReportService.java`
- `src/main/java/ca/foodinventory/ui/ProductionReportDialog.java`
- `src/main/java/ca/foodinventory/ui/FreezerPullView.java`
- `src/main/java/ca/foodinventory/dao/ProductionWeekDao.java`
- `src/main/java/ca/foodinventory/ui/WeeklyProductionView.java`
- `src/main/java/ca/foodinventory/ui/SearchableComboBoxSupport.java`
- `src/main/java/ca/foodinventory/model/ProductionStation.java`
- `src/main/java/ca/foodinventory/dao/ProductionStationDao.java`
- `src/main/java/ca/foodinventory/ui/ProductionStationsView.java`
- `src/main/java/ca/foodinventory/model/ProductionItem.java`
- `src/main/java/ca/foodinventory/dao/ProductionItemDao.java`
- `src/main/java/ca/foodinventory/ui/ProductionItemsView.java`
- `src/main/java/ca/foodinventory/ui/ProductionItemDialog.java`
- `src/main/java/ca/foodinventory/model/ProductionProfile.java`
- `src/main/java/ca/foodinventory/model/ProductionProfileLine.java`
- `src/main/java/ca/foodinventory/dao/ProductionProfileDao.java`
- `src/main/java/ca/foodinventory/dao/ProductionProfileLineDao.java`
- `src/main/java/ca/foodinventory/ui/ProductionProfilesView.java`
- `src/main/java/ca/foodinventory/ui/ProductionProfileDialog.java`
- `src/main/java/ca/foodinventory/model/ProductionItemProductMapping.java`
- `src/main/java/ca/foodinventory/dao/ProductionItemProductMappingDao.java`
- `src/main/java/ca/foodinventory/ui/ProductionItemProductMappingsView.java`
- `src/main/java/ca/foodinventory/ui/ProductionItemProductMappingDialog.java`

## Next Production Coding Step

Build Weekly Production output:

1. Fine-tune the one-page prep sheet layout against the restaurant's production workflow.
2. Add print/export for the full production week if needed.
3. Consider finalized/locked week behaviour.
4. Begin implementing Production Variance reporting using Product Mappings.

## Notes For Future Codex Sessions

- Prefer reading this file first, then inspect the exact files relevant to the requested change.
- Use `rg` for search.
- Use `mvn clean test` after Java changes.
- Do not assume root-level database data is the live app data; `DatabaseManager.getDatabasePath()` points to the runtime DB.
- Existing untracked release/installer files may be present. Do not delete or reset them unless explicitly asked.
- WeeklyProductionView print layout has already been rewritten. Treat the current implementation as the baseline and avoid reverting to the older 32-row pagination approach.
- Keep prep sheets to a single printable page whenever practical.

