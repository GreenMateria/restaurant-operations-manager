# PROJECT STATUS

## Current Version

**Current Project Version:** v3.1.1

Current database schema version: **17**

Application compiles successfully.

The latest documented release is v3.1.1, dated Saturday, September 5, 2026.

All implemented features through v3.1.1 are considered working as intended unless a future issue is reported with a specific workflow, error, or data case.

# Completed This Session

## Current Working State

Completed:

-   Labour Management Phase 1 foundation added on Sunday, September 6, 2026.
-   Labour Management is now a top-level dashboard module with submenu entries for Weekly Labour, Daily Labour, Tip Pool, Tip Pool Breakdown, and Labour Setup.
-   Labour Management Phase 2 Weekly Labour Entry added on Sunday, September 6, 2026.
-   Weekly Labour is implemented as a compact spreadsheet-style JavaFX grid with Monday-Sunday columns, separate Shift 1 / Shift 2 entry fields, position grouping, employee totals, position totals, and FOH/BOH/total variable labour rollups.
-   Weekly Labour grid spacing was tightened after live testing: narrower shift/rate/total columns, shorter `S1`/`S2` headers, reduced row/header height, and denser cell padding reduce horizontal scrolling while preserving the Excel-style workflow.
-   Weekly Labour supports bulk save/load in Cloud API mode through `/labour/weekly` routes and local/direct fallback through `LabourDailyEntryDao`.
-   Weekly Labour stores wage, employee name, position name, and labour group snapshots on daily entries so saved historical weeks do not recalculate from later wage/position setup changes.
-   The live AWS API stack was redeployed on Sunday, September 6, 2026 so the deployed `/labour/weekly` routes are available to API-mode desktops.
-   The live AWS RDS database was advanced to schema version 17 for Labour Management after the API route deployment exposed missing Labour tables in the production schema.
-   `scripts/Apply-LabourSchemaMigration.ps1` was added as a secure prompt-based helper for applying the Labour schema through the RDS admin user without storing the admin password.
-   Labour Setup is implemented as an administrator-protected JavaFX setup screen for labour positions, employees, and default uniform deduction settings.
-   Labour Setup supports API mode through `/labour` routes and local/direct database fallback through new Labour DAOs.
-   Migration 16 added `labour_positions`, `labour_employees`, `labour_daily_sales`, and `labour_daily_entries`, plus the `labour.default_uniform_deduction` setting.
-   Migration 17 added daily labour snapshot columns for historical Weekly Labour reporting.
-   Daily Labour, Tip Pool, and Tip Pool Breakdown are placeholders only and remain future Labour Management phases.
-   Inventory Count Sheet printing was fixed after the v3.1.1 stable release: print layout is now created after printer selection, pages are scaled to the selected printer's printable area, failures show a clear error alert, and alcohol count sheet printing uses the API-backed alcohol profile client in API mode.
-   August 10, 2026 cloud-mode performance pass: inventory count Save Quantities / Complete Count now batch-updates count lines in one transaction and runs the save work in a background JavaFX task instead of blocking the UI thread
-   August 10, 2026 cloud-mode performance pass: Sales report import, Sales Period save, and Weekly Production usage report import/generation now run in background tasks so long Excel/database work does not freeze the app window
-   August 10, 2026 cloud-mode performance pass: Weekly Production report generation now batch-loads active production profile lines instead of querying profile lines one profile at a time
-   POS Sales import now reads fixed report columns for sales amounts: gross sales from column index `1` and net sales from column index `3` in `PosSalesImportService`
-   PostgreSQL JDBC dependency and controlled cloud database mode using hosted PostgreSQL
-   PostgreSQL mode configuration via `FOOD_INVENTORY_DB_*` environment variables, matching `foodinventory.db.*` JVM system properties, or `%LOCALAPPDATA%\FoodInventory\database.properties`
-   SQLite remains the default startup mode, while Cloud PostgreSQL can be selected from the password-protected System module and takes effect after restart
-   Standalone Java connection test succeeded against Aiven PostgreSQL `defaultdb`
-   PostgreSQL schema initializer, schema validation path, and guarded SQLite-to-PostgreSQL upload/download sync service have been added
-   Runtime SQLite database uploaded to Aiven PostgreSQL `defaultdb` with 6,576 rows copied and read-back counts verified
-   A lower-access Aiven application database user is configured for normal app access; the admin user is not required for packaged runtime access
-   Products workflow DAO smoke-tested against Aiven PostgreSQL for list, import-style upsert, detail update, SKU alias upsert, deactivate, and purchase history lookup
-   Product list loading now batches alcohol profile lookup to avoid one cloud database query per product row
-   Invoice DAO smoke-tested against Aiven PostgreSQL for invoice list, duplicate check, line loading, adjustment loading, category breakdown, save, and delete
-   Inventory count template/count DAO smoke-tested against Aiven PostgreSQL for template add, line add, duplicate template, count creation, count line creation, quantity update, completion, and cleanup
-   Inventory valuation and weekly cost report smoke-tested against Aiven PostgreSQL using uploaded completed counts and sales periods
-   Sales period save/upsert/read/totals smoke-tested against Aiven PostgreSQL with temporary data cleanup
-   Order Guide generation smoke-tested against Aiven PostgreSQL using uploaded counts `15` and `18`
-   Production setup/week DAO smoke-tested against Aiven PostgreSQL for stations, items, profiles, profile lines, POS items, product mappings, generated weeks, day lines, overrides, Freezer Pull setting persistence, and cleanup
-   Broad PostgreSQL read smoke check passed for order guide, production setup data, production weeks, sales category mappings, and system settings
-   Default SQLite runtime path re-verified after PostgreSQL compatibility work: build passes, existing data loads, product/invoice/count/sales temporary write smoke test passes, and cleanup succeeded
-   PostgreSQL UI performance pass completed for reported slow screens: Weekly Production now batch-loads week lines, Freezer Pull batch-loads saved pars and loads in the background, Inventory Valuation uses grouped purchase totals and loads in the background
-   PostgreSQL mode now reuses a small internal connection pool so cloud screens do not pay a fresh TLS/database connection cost for every DAO call
-   Password-protected System module now includes simple cloud sync controls for testing the cloud connection, uploading this PC's local SQLite data to cloud, and downloading cloud data to this PC's SQLite database
-   Password-protected System module now includes restart-required database mode switching between Local SQLite and Cloud PostgreSQL using `%LOCALAPPDATA%\FoodInventory\database.properties`
-   Fresh installs now seed `%LOCALAPPDATA%\FoodInventory\database.properties` from safe bundled defaults, with SQLite as the default startup mode; cloud credentials must be supplied locally outside source control
-   All current work PCs have connected to the Aiven PostgreSQL cloud database without error
-   The cloud database is now treated as the accurate master data source for normal daily use
-   On Sunday, August 30, 2026, the Aiven PostgreSQL database was dumped and restored into AWS RDS PostgreSQL.
-   AWS RDS PostgreSQL connection testing succeeded from the development environment.
-   The development app and installed app successfully loaded data from AWS RDS PostgreSQL.
-   A restricted AWS RDS `operations_app` database user was created and verified for normal application access.
-   Configured work PCs should now use AWS RDS PostgreSQL through `%LOCALAPPDATA%\FoodInventory\database.properties`.
-   Work PCs should run in Cloud API mode so normal app saves go through the API to the shared cloud database
-   SQLite mode remains available for fallback, local testing, and fresh installs before cloud configuration
-   Migration 14 added net-sales fields to `sales_periods` and aligned SQLite/PostgreSQL schema versioning at 14
-   Migration 15 added alcohol-only POS sales mappings for future alcohol variance reporting
-   Order Guide `Case` column is editable and saves per count template line, so manager-specific case labels persist when the guide is regenerated
-   Backup and restore now refuse non-local database mode instead of copying the SQLite file while cloud mode is active
-   Automatic GitHub release checking at application startup
-   Version display in application UI and window title
-   Background update checks with 12-hour success cache
-   In-app installer download with progress display
-   Update-ready prompt with installer launch
-   Runtime database backup and restore
-   Invoice subtotal and adjustment support
-   Alcohol-specific manual invoice entry
-   Alcohol manual invoice entry now includes `FOOD` reporting-category products for non-alcohol beverages purchased from alcohol suppliers; this v3.0.4 fix has been implemented and is working as intended
-   Alcohol manual invoice HST Included and Bottle Deposit Included checkbox labels are styled for visibility on the dark screen
-   Alcohol manual invoice adjustments for non-inventory charges
-   Exact paper HST and bottle deposit entry for alcohol invoices
-   Alcohol invoice reconciliation that preserves paper HST and absorbs remaining difference into merchandise categories
-   Split-cost alcohol invoice fallback now preserves valuation cost by deriving case-equivalent last cost from each cost when needed
-   Production items now support a yield factor for automated prep-yield quantity conversion
-   Freezer Pull loads active station items with editable daily quantities
-   Freezer Pull daily quantities persist between sessions
-   Freezer Pull printing uses landscape letter single-page scaling
-   Freezer Pull is accepted as working as intended and should be left unchanged unless explicitly requested
-   Update-ready installer prompt now opens after the download dialog fully closes, preventing the blank white dialog state
-   Release installer now uses a stable Windows upgrade UUID so future installers are treated as upgrades of the same app instead of separate products
-   Main navigation now uses Food Department, Alcohol Department, and Supplies Department labels instead of Inventory labels
-   Alcohol Department now has alcohol-specific Sales Mappings and Variance Report entry points, keeping variance work out of the Production menu
-   Alcohol Sales Mappings is now unlocked and supports add/edit/deactivate of POS SKU-to-alcohol inventory product mappings
-   Alcohol Sales Mapping add/edit now reuses the existing Production POS Menu Items catalog through a searchable POS item picker, then fills POS SKU/PLU and POS Item Name automatically
-   Alcohol Sales Mapping schema preparation now checks whether `alcohol_sales_mappings` already exists before attempting PostgreSQL create/index SQL, so lower-access cloud app users can open the screen after the table has been applied by a schema-capable user
-   Menu open errors now show the root cause and expandable stack trace details instead of clipping long exception text
-   Shared searchable combo boxes now cap live popup matches to reduce JavaFX VirtualFlow warnings and improve large-list picker responsiveness
-   v3.0.6 fixed shared combo box support after the AWS RDS database release.
-   Shared UI styles now define section titles, status bar, danger buttons, and primary-button hover styling for more consistent screens
-   API layer proof started on Tuesday, September 1, 2026.
-   Added standalone `api/` Maven project for the API proof.
-   Added local API endpoints `GET /health` and `GET /products`.
-   Added AWS Lambda/API Gateway deployment support through `api/template.yaml`.
-   Deployed AWS proof stack `esm-operations-api` in `ca-central-1`.
-   Live proof API URL is `https://rn0j30p2vf.execute-api.ca-central-1.amazonaws.com/prod`.
-   Verified deployed `GET /health` returns version `3.1.1` after the API version update.
-   Verified deployed `GET /products` reaches AWS RDS and returns `359` active products when called with the configured `x-api-key`.
-   Verified deployed `GET /products` returns `401 Unauthorized` without the API key.
-   Added desktop API mode configuration support for `mode=api`, `api.url`, and `api.key`.
-   Added System screen API connection status, API health test, and next-startup API mode selection.
-   Wired the desktop Products screen to load active products from the deployed API when running in API mode.
-   Food, Alcohol, and Supplies product add/edit/deactivate are implemented for API mode; GFS product guide CSV parsing remains client-side and sends normalized products through the API import endpoint.
-   Added deployed API endpoint `GET /pos-menu-items`.
-   Verified deployed `GET /pos-menu-items` returns `401 Unauthorized` without the API key.
-   Verified deployed `GET /pos-menu-items` reaches AWS RDS and returns `642` POS menu items when called with the configured `x-api-key`.
-   Wired the desktop POS Menu Items screen to load POS menu items from the deployed API when running in API mode.
-   POS Menu Item add/edit/import/delete and usage-report import are implemented for API mode through the Production API backing.
-   Added deployed Alcohol Sales Mappings API endpoints for list, add, edit, and deactivate.
-   Verified deployed `GET /alcohol-sales-mappings` returns `401 Unauthorized` without the API key.
-   Verified deployed `GET /alcohol-sales-mappings` reaches AWS RDS and returns the current mapping data when called with the configured `x-api-key`.
-   Verified deployed `POST /alcohol-sales-mappings` returns `401 Unauthorized` without the API key.
-   Wired the desktop Alcohol Sales Mappings screen to load, save, and deactivate mappings through the deployed API when running in API mode.
-   Alcohol Sales Mapping dialog POS item and product pickers now use API clients in API mode.
-   API migration priority has shifted away from variance reporting because it is not in active use yet.
-   API migration strategy is department-first. Food Department, Alcohol Department, Supplies Department, Production, and Reporting/Sales are now API-backed.
-   Food Department API migration is implemented and deployed for products, import/manual invoice saves, count templates, inventory counts, and order guide generation/case-size save.
-   The new Food API routes were deployed to `esm-operations-api` in `ca-central-1` and read-only smoke checks passed for Food templates, counts, count lines, completed counts, order guide generation, and unauthorized access rejection.
-   Alcohol Department API migration is implemented and deployed for product/profile maintenance, manual invoice saves, count templates, inventory counts, order guide generation/case-size save, and alcohol product profile reads needed for weighted inventory counts.
-   Alcohol API read-only smoke checks passed for templates, counts, completed counts, count lines, order guide generation, alcohol product profiles, and unauthorized access rejection.
-   Alcohol product maintenance smoke check passed: `/products` returns 85 Alcohol products and all 85 include active alcohol profile data.
-   Production API backing has been implemented in the development build for stations, production items, production profiles/profile lines, POS menu item maintenance/import/KDS cleanup, product mappings, Weekly Production generation/loading/refresh/override saves, and Freezer Pull manual quantities.
-   Weekly Production Refresh Week now removes saved lines for production items that are no longer active/current prep-list items.
-   Production API backing was deployed to `esm-operations-api` in `ca-central-1` on Friday, September 4, 2026.
-   Production API read-only smoke checks passed for health, missing-key `401`, stations, active production items, profiles, profile lines, active POS menu items, product mappings, production weeks, week days, week lines, and Freezer Pull lines.
-   Live desktop validation confirmed Production CSV/report import and Weekly Production generation complete successfully in API mode.
-   Added the missing desktop Log4j runtime provider required by Apache POI so production report imports no longer print `Log4j API could not find a logging provider`.
-   Supplies Department API migration is implemented and deployed for products, manual invoice saves, count templates, inventory counts, order guide generation, and order-guide case-size save.
-   Added the explicit `/supplies-invoices` API route and deployed the updated SAM stack to `esm-operations-api` in `ca-central-1` on Saturday, September 5, 2026.
-   Supplies API read-only smoke checks passed for health, missing-key `401` on `/supplies-invoices`, Supplies templates, counts, completed counts, and order guide generation from completed count `31` to `35`.
-   Reporting/Sales API migration is implemented and deployed for Invoice History, invoice line/breakdown loading, invoice delete, sales period list/save, POS sales Excel import save, Inventory Valuation, and Weekly Cost Report generation.
-   Product support gaps are implemented and deployed: GFS product guide CSV files are parsed client-side, normalized product records are upserted through `POST /products/import`, and product purchase history loads through `GET /products/{id}/purchase-history`.
-   The SAM template now uses one `ANY /{proxy+}` API Gateway trigger for the Lambda router to avoid Lambda resource-policy size limits as the API route surface grows.
-   Reporting/Sales and product support live smoke checks passed on Saturday, September 5, 2026: missing-key `401` for `/reporting/invoices` and `/products/import`, 50 invoices, 11 sales periods, invoice `106` lines/breakdown, Food valuation for count `34`, Weekly Cost Report from count `32` to `34`, 362 active products, and product `267` purchase history. API health was later updated and verified at version `3.1.1`.
-   Administrative upload/download sync migration is implemented and deployed through `GET /admin/sync/download` and `POST /admin/sync/upload`; API mode no longer needs desktop PostgreSQL credentials for those cloud replacement tools.
-   Admin sync live smoke checks passed on Saturday, September 5, 2026: missing-key `401` for download/upload and authenticated read-only download returned 24 tables, 11,417 rows, and a 1.85 MB cloud snapshot. Authenticated upload was not command-line smoke tested because it replaces production cloud data.
-   Release packaging can now generate an ignored `src/main/resources/database-release.properties` file from `FOOD_INVENTORY_RELEASE_API_KEY`, embedding API mode defaults in the installer without committing the key to Git.
-   On startup, the desktop app applies a bundled release API config once per release version, updating `%LOCALAPPDATA%\FoodInventory\database.properties` to API mode automatically for users who install the update.
-   API infrastructure is managed by the SAM template in `api/template.yaml`; `api/samconfig.example.toml` documents safe local deploy parameters while real `samconfig.toml` and secrets remain untracked.
-   v3.1.1 was released on Saturday, September 5, 2026.
-   v3.1.1 completed the desktop API migration so normal users no longer need to configure direct client database connections.
-   v3.1.1 added a database status indicator for clearer runtime mode/connection visibility.
-   The deployed API version was updated on Sunday, September 6, 2026 so `GET /health` returns version `3.1.1`.
-   The existing API Lambda CloudWatch log group was imported into the SAM/CloudFormation stack on Sunday, September 6, 2026.
-   API Lambda log retention is now managed by IaC through `LambdaLogRetentionDays` and verified at 30 days.

Current behaviour:

-   Fresh installs still start with the local SQLite runtime database until configured.
-   Current work PCs should use Cloud API mode for Food Department, Alcohol Department, Supplies Department, Production, Reporting/Sales, Labour Setup, Weekly Labour, product support, and administrative upload/download sync workflows.
-   Direct Cloud PostgreSQL mode remains available as an administrator fallback.
-   The desktop app supports API mode for Food Products, Food Import Invoice, Food Manual Invoice, Food Count Templates, Food Inventory Counts, Food Order Guide, Alcohol Products/Profile Maintenance, Alcohol Manual Invoice, Alcohol Count Templates, Alcohol Inventory Counts, Alcohol Order Guide, Alcohol Sales Mappings, Supplies Products, Supplies Manual Invoice, Supplies Count Templates, Supplies Inventory Counts, Supplies Order Guide, Production workflows, Labour Setup, Weekly Labour, Invoice History, Sales Entry/import, Inventory Valuation, Weekly Cost Report, GFS product guide import, and product purchase history.
-   POS Menu Items support API-mode load, add, edit, deactivate, setup import, usage-report import, and KDS cleanup in the development build.
-   Inventory count saves/completions, sales report imports/saves, and Weekly Production usage imports are expected to remain responsive in cloud mode because long writes/imports are now moved off the JavaFX thread.
-   Inventory Count Sheet printing is expected to dispatch correctly after printer selection in Food, Alcohol, and Supplies count screens.
-   PostgreSQL mode has passed broad DAO/service smoke tests and manual JavaFX UI testing, including the previously slow Weekly Production, Freezer Pull, and Inventory Valuation screens.
-   When running in Cloud PostgreSQL mode, normal reads and saves use the shared AWS RDS database directly.
-   Upload This PC to Cloud and Download Cloud to This PC are administrator migration/recovery tools, not routine daily sync actions; when API settings are configured, these tools use the API instead of direct PostgreSQL.
-   Alcohol Sales Mappings uses the already imported POS Menu Items list for POS SKU selection; managers should keep the Production POS Menu Items catalog current before mapping alcohol variance items.
-   Installed versions automatically check GitHub Releases on startup.
-   If a newer version exists, the user is prompted to download it.
-   The installer is downloaded inside the application when a Windows installer asset is available.
-   Release-built installers can automatically configure client PCs for API mode using the generated release config resource; source-controlled defaults still keep API credentials blank.
-   Download failures can fall back to opening the GitHub release page.
-   Invoice adjustments are stored separately from inventory merchandise cost.
-   Alcohol invoice history breakdown now shows saved adjustments such as HST and Bottle Deposit.
-   Inventory valuation still uses purchase history first, then the product fallback cost.
-   Freezer Pull is an independent manual workflow and does not require POS usage report import.
-   After an update download completes, the user sees a readable install-now / install-later prompt.
-   Installers built from the updated release script use the same `--win-upgrade-uuid` on every release.
-   The v2.1.5-to-v2.1.6 update-ready prompt may still appear blank because that prompt is rendered by the already-installed v2.1.5 code; pressing Enter activates the default install action.

# Future Enhancement

-   Verify installer integrity before launch.
-   Add a manual Check for Updates action.
-   Validate the first installer upgrade built with the stable Windows upgrade UUID on a previously installed machine.
-   Continue alcohol workflow polish only as new live-data issues are identified.

# Next Development Priorities

1.  Continue daily operations from Cloud API mode on all configured work PCs.
2.  Validate the v3.1.1 installer upgrade on the work PCs and confirm existing local data/config are preserved.
3.  Validate migrated Food, Alcohol, Supplies, Production, Reporting/Sales, product import, and product purchase-history API workflows from the IntelliJ desktop app using normal live workflows.
4.  Build Daily Labour with manual daily net sales, labour percentages, target variance reporting, and FOH/BOH/total variable labour rollups.
5.  Manually smoke test Weekly Labour in API mode against live manager workflow data.
6.  Add installer verification / checksum handling.
7.  Add a manual Check for Updates action.
8.  Confirm future installer upgrades replace the existing installed app entry and do not create duplicate installs.
9.  Continue advanced reporting and workflow polish.
10. Return to variance reporting only after the required mappings and live workflow setup are ready.
