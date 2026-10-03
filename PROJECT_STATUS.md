# PROJECT STATUS

## Current Version

**Current Stable Release Target:** v4.2.0

Current database schema version: **23**

Application compiles successfully.

The latest stable release being prepared is v4.2.0.

All implemented features through v4.2.0 are considered working as intended unless a future issue is reported with a specific workflow, error, or data case.

# Completed This Session

## Current Working State

Completed:

-   On Saturday, October 3, 2026, fixed Alcohol Variance importing zero wine sales because the shared production importer skipped wine rows between the KDS start and X41 Note.ESM end headings. Alcohol import now selects active mapped PLUs throughout the file without filtering through the active shared POS catalog; Production keeps its existing exclusion. Verified six domestic wine PLUs with 25 sales in the supplied report, passed all 22 tests with `mvn clean test`, and received manager confirmation that the report works properly. The fix needs a desktop update only, with no API deployment or schema migration.

-   Updated the KDS section end heading to X41 Note.ESM for POS catalog import, KDS cleanup, and usage-report parsing. Cleanup refuses deletion when a KDS start heading has no matching end heading.
-   Shared POS item import and maintenance now lives under Administration → POS Catalog / PLUs. Production → POS Production Mappings retains profile assignments and usage-report import. Catalog XLSX parsing continues to use column A for item names and column B for PLUs.
-   Multi-location rollout foundation started on Thursday, September 10, 2026.
-   Migration 18 adds `locations` and `location_sessions` for one username/password per location and session-token based API login.
-   Migration 19 adds `location_id` to Labour Management tables and the Labour API now scopes positions, employees, daily sales, daily entries, and saved weeks by the logged-in location.
-   Migration 20 adds `location_id` to store-owned operational tables for products, aliases, invoices, inventory templates/counts, sales periods, alcohol setup, and production setup/weeks so existing records remain attached to current location `1` while future locations can be separated.
-   Migration 21 updates the PostgreSQL multi-location uniqueness model so products, aliases, sales periods, alcohol sales mappings, production setup names, POS menu SKUs, and production weeks are unique per location instead of globally.
-   The API now includes `POST /auth/login`, returning a session token and resolved location details when location credentials are valid.
-   The desktop app now has a Store Login dialog that can be enabled in API mode with `location.login.required=true`.
-   Successful Store Login saves the returned session token and location display details in local app configuration; it does not store the location password.
-   Desktop API clients now attach the Store Login session token to normal business API requests when a store session is present.
-   API reads and writes are now scoped to the resolved store location for Labour, Products, Invoices, Inventory templates/counts/order guides, Reporting/Sales periods/valuations/weekly cost reports, Alcohol Profiles/Sales Mappings, and Production/POS Menu/Weekly Production workflows.
-   The live API stack was redeployed with location auth required, and TEST-store validation confirmed that a new store does not see home-store products while the home store still sees its own data.
-   `scripts/Apply-LocationAuthSchemaMigration.ps1` was added as a secure prompt-based helper for applying the location-auth schema to AWS RDS and setting the initial current-store password without storing plain text credentials.
-   `scripts/Set-LocationCredentials.ps1` was added as a development-machine-only helper for creating or resetting location credentials without shipping scripts in the store installer.
-   `scripts/Apply-StoreLocationSchemaMigration.ps1`, `scripts/Apply-LocationUniqueConstraintsMigration.ps1`, and `scripts/Copy-LocationSetup.ps1` were added for RDS multi-location schema upgrades and controlled setup/master-data copying between stores.
-   `scripts/Manage-Locations.ps1` now lists stores, creates/resets store credentials, renames stores, applies location schema migrations, applies location-aware uniqueness, and copies setup data between stores.
-   Release packaging now bundles `location.login.required=true` by default so updated client PCs require Store Login without manual per-machine configuration.
-   v4.2.0 rebrands the desktop application as StoreOps Manager with a neutral app logo, installer naming, update-dialog text, and release metadata.
-   v4.2.0 adds a polished card-based navigation UI for the home dashboard and main section menus while preserving dense task-focused table/spreadsheet screens.
-   v4.2.0 launches the main application window maximized for manager workstations.
-   v4.2.0 adds an About panel with version, database mode, signed-in store, and application scope.
-   v4.2.0 adds a Switch Store action in the sidebar so a user can clear the current store session and return to Store Login without restarting the application.
-   System and Labour Setup protected passwords are now store-scoped in Cloud API mode and stored as salted PBKDF2 hashes on the signed-in location record, so managers are not tied to one workstation.
-   Labour Setup uses a temporary first-use password that immediately prompts for a new store-scoped Labour Setup password when the signed-in store does not have one yet.
-   `scripts/Apply-LocationProtectedPasswordsMigration.ps1` was added to apply the schema 22 protected-password columns to AWS RDS.
-   Migration 23 adds `labour_employee_pay_rates` so employee hourly wage changes can be entered with an effective date without rewriting previous labour records.
-   Labour Hours and Daily Labour now seed unsaved daily entries from the employee pay rate effective on each work date, while already-saved daily entries continue to use their stored wage snapshot.
-   `scripts/Apply-LabourPayRateSchemaMigration.ps1` was added to apply the schema 23 pay-rate history table to AWS RDS.
-   On Friday, October 2, 2026, AWS RDS was verified at schema version 23 with `labour_employee_pay_rates` present, `esm-operations-api` was redeployed with the labour pay-rate API updates, `/health` returned `status=ok`, and `/protected-passwords/status` correctly rejected API-key-only requests without a store location token.
-   `scripts/Reset-LabourSetupPassword.ps1` remains a local SQLite/development fallback helper for returning a PC to the first-use Labour Setup password flow outside API mode.
-   On Thursday, September 24, 2026, `esm-operations-api` was redeployed with protected-password routes, AWS RDS was advanced to schema version 22, `/health` returned `status=ok`, and `/protected-passwords/status` correctly rejected API-key-only requests without a store location token.
-   Labour Management Phase 1 foundation added on Sunday, September 6, 2026.
-   Labour Management is now a top-level dashboard module with submenu entries for Labour Hours, Daily Labour Cost, Tip Pool, Tip Pool Breakdown, and Labour Setup.
-   Labour Management Phase 2 Labour Hours foundation added on Sunday, September 6, 2026 and reshaped on Monday, September 7, 2026.
-   Labour Hours is implemented with an inventory-count-style list of saved weeks. Opening a week launches a separate spreadsheet-style pop-out window for daily Shift 1 / Shift 2 hour entry by position group.
-   Labour Hours entry uses Monday-Sunday columns with separate Shift 1 / Shift 2 fields and employee total hours. Employee number, hourly rate, total pay, restaurant header, position total rows, and bottom labour total rows were removed from the entry screen.
-   Labour Hours supports saved-week listing through `/labour/weeks`, and bulk save/load in Cloud API mode through `/labour/weekly` routes and local DAO fallback.
-   Labour Hours stores wage, employee name, position name, labour group, tip-pool eligibility, and uniform-deduction context on daily entries/rows so saved historical labour workflows do not recalculate from later setup changes.
-   The live AWS API stack was redeployed on Sunday, September 6, 2026 so the deployed `/labour/weekly` routes are available to API-mode desktops.
-   The live AWS RDS database was first advanced for Labour Management after the API route deployment exposed missing Labour tables in the production schema; later multi-location, protected-password, and pay-rate history migrations advanced the live schema to version 23.
-   `scripts/Apply-LabourSchemaMigration.ps1` was added as a secure prompt-based helper for applying the Labour schema through the RDS admin user without storing the admin password.
-   Labour Setup is implemented as an administrator-protected JavaFX setup screen for labour positions, employees, and default uniform deduction settings.
-   Labour Setup supports API mode through `/labour` routes, with local DAO support retained for development fallback.
-   Labour Management Phase 3 Daily Labour Cost was added on Monday, September 7, 2026.
-   Daily Labour Cost is implemented as a weekly cost screen with start/end date selection, editable daily net sales, Monday-Sunday rows, BOH labour dollars, FOH labour dollars, labour percentages, and a total row.
-   Daily Labour Cost reads hours from the same `labour_daily_entries` records used by Labour Hours so both views stay consistent.
-   Daily Labour Cost stores manual operational net sales in `labour_daily_sales`, separate from imported official weekly `sales_periods`.
-   Daily Labour Cost supports Cloud API mode through `/labour/daily/{workDate}` and `PUT /labour/daily` routes, with local DAO fallback retained for development.
-   Migration 16 added `labour_positions`, `labour_employees`, `labour_daily_sales`, and `labour_daily_entries`, plus the `labour.default_uniform_deduction` setting.
-   Migration 17 added daily labour snapshot columns for historical Labour Hours reporting.
-   Tip Pool is implemented as a weekly spreadsheet-style allocation screen using editable daily tip-out pool amounts, tip-pool eligible employee hours from Labour Hours, calculated daily employee tip amounts, and weekly totals. The tip pool amount entry fields sit under each day's Tip column to avoid confusion with Hours.
-   Tip Pool Breakdown is implemented as a date-range payout report that allocates saved tip pool amounts by tip-eligible hours and subtracts the configured uniform deduction per worked day for applicable employees only.
-   Labour Management was manager-tested and accepted as working as intended on Tuesday, September 8, 2026.
-   Labour Hours department headers now use a clearer styled band in the pop-out entry window, and the Labour Hours pop-out scene loads the shared application stylesheet.
-   Tip Pool Breakdown net payout amounts now round to the nearest nickel for Canadian cash payout handling.
-   Tip Pool Breakdown now supports printing a compact black-and-white payout report with employee rows and totals.
-   Normal API workflows for Food, Alcohol, Supplies, Production, Reporting/Sales, product import, and product purchase history are confirmed working for the v4.2.0 release target.
-   Protected-password workflows for System and Labour Setup are API-backed and store-scoped in Cloud API mode for the v4.2.0 release target.
-   On Monday, September 21, 2026, the API and desktop imported-invoice path was fixed so API-mode GFS invoice imports validate unknown SKUs against cloud products/aliases, save new supplier SKU aliases through the API, create missing products through the API, and return clear validation details instead of a generic invoice-save `HTTP 500`.
-   The `esm-operations-api` stack was redeployed in `ca-central-1` on Monday, September 21, 2026 with the imported-invoice SKU/alias fix; deployed `/health` returned `status=ok`.
-   Inventory Count Sheet printing was fixed after the v3.1.1 stable release: print layout is now created after printer selection, pages are scaled to the selected printer's printable area, failures show a clear error alert, and alcohol count sheet printing uses the API-backed alcohol profile client in API mode.
-   Direct desktop Cloud PostgreSQL mode was retired on Monday, September 7, 2026; release desktops now use the shared Cloud API as the normal operating path.
-   The System screen no longer exposes direct PostgreSQL mode switching, direct cloud connection testing, or upload-this-PC-to-cloud controls.
-   SQLite remains as a local cloud-snapshot/backup target for API downloads and emergency local restore only, not as a normal daily operating mode.
-   System administrator password storage was previously moved to local salted PBKDF2 hashes in `%LOCALAPPDATA%\FoodInventory\database.properties`; this now remains the SQLite/development fallback while Cloud API mode uses store-scoped protected passwords on `locations`.
-   Legacy plain-text local/SQLite administrator passwords are migrated to a hash after a successful local fallback login.
-   `scripts/Reset-AdminPassword.ps1` was added as a local SQLite/development password recovery helper that prompts for a new admin password and writes only a salted hash.
-   August 10, 2026 cloud-mode performance pass: inventory count Save Quantities / Complete Count now batch-updates count lines in one transaction and runs the save work in a background JavaFX task instead of blocking the UI thread
-   August 10, 2026 cloud-mode performance pass: Sales report import, Sales Period save, and Weekly Production usage report import/generation now run in background tasks so long Excel/database work does not freeze the app window
-   August 10, 2026 cloud-mode performance pass: Weekly Production report generation now batch-loads active production profile lines instead of querying profile lines one profile at a time
-   POS Sales import now reads fixed report columns for sales amounts: gross sales from column index `1` and net sales from column index `3` in `PosSalesImportService`
-   PostgreSQL JDBC dependency and controlled cloud database mode using hosted PostgreSQL were part of the earlier hybrid rollout
-   PostgreSQL mode configuration via `FOOD_INVENTORY_DB_*` environment variables, matching `foodinventory.db.*` JVM system properties, or `%LOCALAPPDATA%\FoodInventory\database.properties` was part of the earlier hybrid rollout
-   Direct Cloud PostgreSQL desktop mode has since been retired in favor of Cloud API mode
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
-   The earlier password-protected System module included direct cloud sync controls and restart-required mode switching during the hybrid PostgreSQL rollout.
-   Direct desktop PostgreSQL mode switching and local-to-cloud upload controls have since been removed from normal desktop use.
-   Fresh installs now seed `%LOCALAPPDATA%\FoodInventory\database.properties` from safe bundled defaults, with API mode as the default startup mode and credentials supplied through ignored release/local config.
-   All current work PCs have connected to the Aiven PostgreSQL cloud database without error
-   The cloud database is now treated as the accurate master data source for normal daily use
-   On Sunday, August 30, 2026, the Aiven PostgreSQL database was dumped and restored into AWS RDS PostgreSQL.
-   AWS RDS PostgreSQL connection testing succeeded from the development environment.
-   The development app and installed app successfully loaded data from AWS RDS PostgreSQL.
-   A restricted AWS RDS `operations_app` database user was created and verified for normal application access.
-   Configured work PCs should now use Cloud API mode through `%LOCALAPPDATA%\FoodInventory\database.properties`.
-   Work PCs should run in Cloud API mode so normal app saves go through the API to the shared cloud database.
-   SQLite mode remains available as a local cloud-snapshot/backup target, emergency local restore file, and development fallback.
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
-   Alcohol Variance Report now generates actual-vs-sold usage rows from opening/closing alcohol counts, period purchases, imported POS usage, and active Sales Mappings; bottle and keg products convert mapped ounce/mL portions into inventory-equivalent usage before variance is calculated
-   Alcohol Variance Report includes red/green variance quantity and variance-dollar columns and is accepted as complete for the v4.2.0 release target.
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
-   Verified deployed `GET /health` returns version `3.1.2` after the API version update.
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
-   Alcohol Variance Report is complete in the desktop app and uses existing API-backed counts, order guide usage, valuation cost, product profiles, sales mappings, and client-side POS usage import.
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
-   Reporting/Sales and product support live smoke checks passed on Saturday, September 5, 2026: missing-key `401` for `/reporting/invoices` and `/products/import`, 50 invoices, 11 sales periods, invoice `106` lines/breakdown, Food valuation count `34`, Weekly Cost Report from count `32` to `34`, 362 active products, and product `267` purchase history. API health was later updated and verified at version `3.1.2`.
-   Administrative upload/download sync migration is implemented and deployed through `GET /admin/sync/download` and `POST /admin/sync/upload`; API mode no longer needs desktop PostgreSQL credentials for those cloud replacement tools.
-   Admin sync live smoke checks passed on Saturday, September 5, 2026: missing-key `401` for download/upload and authenticated read-only download returned 24 tables, 11,417 rows, and a 1.85 MB cloud snapshot. Authenticated upload was not command-line smoke tested because it replaces production cloud data.
-   Release packaging can now generate an ignored `src/main/resources/database-release.properties` file from `FOOD_INVENTORY_RELEASE_API_KEY`, embedding API mode defaults in the installer without committing the key to Git.
-   On startup, the desktop app applies a bundled release API config once per release version, updating `%LOCALAPPDATA%\FoodInventory\database.properties` to API mode automatically for users who install the update.
-   API infrastructure is managed by the SAM template in `api/template.yaml`; `api/samconfig.example.toml` documents safe local deploy parameters while real `samconfig.toml` and secrets remain untracked.
-   v3.1.1 was released on Saturday, September 5, 2026.
-   v3.1.1 completed the desktop API migration so normal users no longer need to configure direct client database connections.
-   v3.1.1 added a database status indicator for clearer runtime mode/connection visibility.
-   The deployed API version was updated on Monday, September 7, 2026 so `GET /health` returns version `3.1.2`.
-   The existing API Lambda CloudWatch log group was imported into the SAM/CloudFormation stack on Sunday, September 6, 2026.
-   API Lambda log retention is now managed by IaC through `LambdaLogRetentionDays` and verified at 30 days.

Current behaviour:

-   Multi-location login is implemented for Cloud API mode. Store Login is enabled in release builds, and the main store-owned API workflows require and use the resolved location session.
-   PostgreSQL multi-location uniqueness has been moved to location-aware composite constraints for the main store-owned duplicate-sensitive records.
-   Store Login is enabled in release packaging by default through `location.login.required=true`; source-controlled defaults still keep it disabled for local fallback unless configured.
-   Fresh installs and release desktops are intended to start in Cloud API mode when API settings are present.
-   Current work PCs should use Cloud API mode for Food Department, Alcohol Department, Supplies Department, Production, Reporting/Sales, Labour Setup, Labour Hours, Daily Labour Cost, Tip Pool, Tip Pool Breakdown, product support, and administrative cloud snapshot workflows.
-   Direct Cloud PostgreSQL mode is no longer a normal desktop operating mode.
-   SQLite is retained only as a local backup snapshot/recovery file, populated from the Cloud API download workflow.
-   The desktop app supports API mode for Food Products, Food Import Invoice, Food Manual Invoice, Food Count Templates, Food Inventory Counts, Food Order Guide, Alcohol Products/Profile Maintenance, Alcohol Manual Invoice, Alcohol Count Templates, Alcohol Inventory Counts, Alcohol Order Guide, Alcohol Sales Mappings, Alcohol Variance Report, Supplies Products, Supplies Manual Invoice, Supplies Count Templates, Supplies Inventory Counts, Supplies Order Guide, Production workflows, Labour Setup, Labour Hours, Daily Labour Cost, Tip Pool, Tip Pool Breakdown, Invoice History, Sales Entry/import, Inventory Valuation, Weekly Cost Report, GFS product guide import, and product purchase history.
-   In API mode, Food Import Invoice now uses cloud SKU/alias resolution before save, writes supplier SKU alias mappings through the API, and creates missing products in the signed-in store scope instead of touching the local SQLite snapshot.
-   POS Menu Items support API-mode load, add, edit, deactivate, setup import, usage-report import, and KDS cleanup in the development build.
-   Inventory count saves/completions, sales report imports/saves, and Weekly Production usage imports are expected to remain responsive in cloud mode because long writes/imports are now moved off the JavaFX thread.
-   Inventory Count Sheet printing is expected to dispatch correctly after printer selection in Food, Alcohol, and Supplies count screens.
-   Earlier PostgreSQL mode passed broad DAO/service smoke tests and manual JavaFX UI testing, including the previously slow Weekly Production, Freezer Pull, and Inventory Valuation screens.
-   Normal reads and saves now use the shared AWS RDS database through the Cloud API.
-   Cloud Snapshot download is an administrator backup/recovery tool, not a routine daily sync action.
-   Alcohol Sales Mappings uses the shared POS catalog for POS SKU selection; managers should keep Administration → POS Catalog / PLUs current before mapping alcohol variance items.
-   Installed versions automatically check GitHub Releases on startup.
-   If a newer version exists, the user is prompted to download it.
-   The installer is downloaded inside the application when a Windows installer asset is available.
-   Release-built installers can automatically configure client PCs for API mode using the generated release config resource; source-controlled defaults still keep API credentials blank.
-   Download failures can fall back to opening the GitHub release page.
-   Invoice adjustments are stored separately from inventory merchandise cost.
-   Alcohol invoice history breakdown now shows saved adjustments such as HST and Bottle Deposit.
-   Inventory valuation still uses purchase history first, then the product fallback cost.
-   Freezer Pull is an independent manual workflow and does not require POS usage report import.
-   Alcohol Variance Report is complete and accepted as working as intended in the v4.2.0 development build.
-   After an update download completes, the user sees a readable install-now / install-later prompt.
-   Installers built from the updated release script use the same `--win-upgrade-uuid` on every release.
-   Release packaging now generates and uploads a `.sha256` checksum asset beside the Windows installer.
-   In-app update downloads verify the installer against the published SHA-256 checksum before offering to launch it when a checksum asset is available.
-   The System screen now includes a manual Check for Updates action that bypasses the startup cooldown.
-   The release script now guards the stable Windows upgrade UUID so future installers continue upgrading the existing installed app entry.
-   App-facing old restaurant branding has been removed from the current code, assets, and release downloads. POS/import normalization keeps the required legacy report suffix handling.
-   GitHub Release assets with old branded installer names were removed from published releases.
-   The v2.1.5-to-v2.1.6 update-ready prompt may still appear blank because that prompt is rendered by the already-installed v2.1.5 code; pressing Enter activates the default install action.

# Future Enhancement

-   Extend multi-location administration with safer in-app/through-API setup-copy tooling and future reporting rollups as requirements emerge.
-   Convert the remaining manually managed cloud infrastructure into Infrastructure as Code once the multi-location target shape is clear.
-   Defer Recipe Costing and Food Variance until after multi-location and infrastructure priorities are settled.

# Next Development Priorities

1.  Prepare and validate the v4.2.0 release package.
2.  Monitor multi-location rollout on the first updated workstations.
3.  Convert cloud infrastructure to IaC based on the multi-location design.
