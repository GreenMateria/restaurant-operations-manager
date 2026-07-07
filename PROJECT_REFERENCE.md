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
  - `mvn clean test` passed after the production Usage Report changes.

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

## Database And Migrations

- `DatabaseManager.initializeDatabase()` creates the base schema and runs `DatabaseMigrationRunner`.
- Current schema version in `DatabaseMigrationRunner` is `6`.
- Migration files:
  - `Migration2`
  - `Migration3`
  - `Migration4`
  - `Migration5`
  - `Migration6`
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

## Production Module Status

- Production setup screens exist and compile:
  - Production Stations
  - Production Items
  - Production Profiles
  - POS Menu Items
  - Product Mappings
- Production item saving was manually retested and is working.
- `POS Menu Items` can assign each POS SKU/PLU to a production profile.
- `Product Mappings` can map production items to inventory products for future usage/variance reporting.
- Weekly Production UI is still a placeholder in `MainView`.
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
- Current import result is a summary only:
  - configured POS rows imported
  - weekly quantity sold
- It does not yet save imported rows or generate production report lines.

## Production Files

- `src/main/java/ca/foodinventory/model/PosMenuItem.java`
- `src/main/java/ca/foodinventory/dao/PosMenuItemDao.java`
- `src/main/java/ca/foodinventory/ui/PosMenuItemsView.java`
- `src/main/java/ca/foodinventory/ui/PosMenuItemDialog.java`
- `src/main/java/ca/foodinventory/service/ProductionUsageReportImportService.java`
- `src/main/java/ca/foodinventory/model/ImportedUsageReportLine.java`
- `src/main/java/ca/foodinventory/model/ImportedUsageReportSummary.java`
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

Build the generated production report workflow:

1. Import Usage Report.
2. For each imported POS SKU, find the assigned production profile.
3. For each profile line, multiply quantity sold by quantity per sale.
4. Roll totals up by:
   - production item
   - station
   - day
   - week total
5. Display and eventually print the production report.

## Notes For Future Codex Sessions

- Prefer reading this file first, then inspect the exact files relevant to the requested change.
- Use `rg` for search.
- Use `mvn clean test` after Java changes.
- Do not assume root-level database data is the live app data; `DatabaseManager.getDatabasePath()` points to the runtime DB.
- Existing untracked release/installer files may be present. Do not delete or reset them unless explicitly asked.
