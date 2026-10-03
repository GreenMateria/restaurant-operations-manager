# Release History

## v4.2.0

Date: 2026-09-13

### Alcohol Variance Import Fix (2026-10-03)

- Fixed wine sold quantities appearing as zero when wine rows fall between the production import's KDS start and X41 Note.ESM end headings.
- Alcohol Variance now reads the selected usage report using active Alcohol Sales Mapping PLUs throughout the sheet, without requiring those PLUs to remain active in the shared POS catalog. Production retains its existing KDS exclusion.
- Verified the supplied sales mix file returns 25 sales across six domestic wine PLUs; all 22 automated tests passed, and the manager confirmed the variance report works correctly.
- This is a desktop import fix; no API deployment or database migration is required.

### Shared POS Catalog

- Updated KDS exclusion and cleanup to end at X41 Note.ESM; cleanup refuses deletion if that end heading is missing after the KDS start.
- Moved shared POS item import and maintenance to Administration → POS Catalog / PLUs for discoverability from Alcohol and Production workflows.
- Kept production profile assignments and usage-report import under Production → POS Production Mappings.
- Clarified catalog XLSX columns: A for item names and B for PLUs.

### Branding And Presentation

- Rebranded the desktop application as StoreOps Manager with neutral app/window/update wording.
- Replaced the bundled restaurant logo and Windows icon with a generic operations icon.
- Updated release packaging metadata so new installers use StoreOps Manager naming and a neutral vendor value.
- Removed old branded installer assets from GitHub Releases while preserving required POS/import report normalization.

### UI Shell

- Added a maximized launch experience for manager workstations.
- Added a polished card-based home dashboard with version, database mode, and signed-in store status.
- Applied the same card-based navigation layout to Food, Alcohol, Supplies, Production, Labour, and Reporting menus.
- Added an About panel with version, database, store, and app-scope details.
- Added Switch Store in the sidebar so users can return to Store Login without restarting the app.
- Fixed standard dialog text visibility when dark app styles are loaded.

### API Invoice Import

- Fixed API-mode imported GFS invoice saving so unknown supplier SKUs are validated against cloud products and aliases before save.
- Added API-backed supplier SKU alias saving and missing-product creation for the imported invoice mapping workflow.
- Changed missing imported-invoice products to return clear validation details instead of a generic invoice-save `HTTP 500`.
- Redeployed `esm-operations-api` in `ca-central-1` on Monday, September 21, 2026.

### Store-Scoped Protected Passwords

- Added store-scoped System administrator and Labour Setup protected passwords for Cloud API mode.
- Stored protected passwords as salted PBKDF2 hashes on the signed-in `locations` row so protected access follows the store across workstations.
- Added first-use Labour Setup handling: when a store has no Labour Setup password yet, the temporary password `LabourSetup!` opens a required create-new-password prompt.
- Added schema migration 22 and `scripts/Apply-LocationProtectedPasswordsMigration.ps1` for applying the protected-password columns to AWS RDS.
- Added `GET /protected-passwords/status`, `POST /protected-passwords/verify`, and `PUT /protected-passwords/{scope}` to the API and deployed them to `esm-operations-api` in `ca-central-1` on Thursday, September 24, 2026.
- Verified the live RDS schema version is 22 and the deployed protected-password routes require a valid store location token.

### Labour Pay Rate History

- Added schema migration 23 with `labour_employee_pay_rates` for effective-dated hourly wage history.
- Added a Rate Effective date to Labour Setup employee wage changes.
- Updated Labour Hours and Daily Labour so unsaved entries use the employee pay rate effective on each work date.
- Preserved historical reporting behavior by keeping already-saved `labour_daily_entries.hourly_wage` snapshots unchanged after later wage changes.
- Added `scripts/Apply-LabourPayRateSchemaMigration.ps1` for applying the schema 23 table to AWS RDS.
- Verified AWS RDS was at schema version 23 with `labour_employee_pay_rates` present, then redeployed `esm-operations-api` in `ca-central-1` on Friday, October 2, 2026.
- Smoke-tested the deployed API after redeploy: `/health` returned `status=ok`, and `/protected-passwords/status` rejected API-key-only requests without a store location token.

## v4.0.0

Date: 2026-09-11

### Multi-Location

- Added Store Login for Cloud API mode.
- Added location credentials and hashed location sessions through `locations` and `location_sessions`.
- Added `POST /auth/login` to the API and desktop login support that stores the returned session token without storing the location password.
- Added `location_id` to Labour Management tables and store-owned operational tables.
- Scoped main API reads and writes by the resolved store location for Labour, Products, Invoices, Inventory templates/counts/order guides, Reporting/Sales periods/valuations/weekly cost reports, Alcohol Profiles/Sales Mappings, and Production/POS Menu/Weekly Production workflows.
- Updated PostgreSQL uniqueness so duplicate-sensitive records such as product SKUs, POS SKUs, sales periods, production names, and production weeks are unique per location instead of globally.
- Deployed the API with location auth required and verified TEST-store isolation from the home store.

### Store Administration

- Added PowerShell tools to list stores, create/reset store credentials, rename stores, apply location schema migrations, apply location-aware uniqueness, and copy setup/master data between stores.
- Added controlled setup-copy support for products, aliases, inventory templates, alcohol setup, production setup, POS menu items, and production product mappings.
- Setup-copy tooling intentionally does not copy invoices, inventory counts, sales periods, labour history, generated production weeks, sessions, or passwords.

### Release Configuration

- Release packaging now bundles `location.login.required=true` by default so updated client PCs require Store Login without manual local configuration.
- Fixed the Store Login dialog startup path for JavaFX 25 by avoiding owner binding before the primary stage has a scene.

## v3.1.3

Date: 2026-09-08

### Labour Management

- Completed Labour Management manager workflow polish on Tuesday, September 8, 2026.
- Updated Labour Hours department headers to use a clearer styled band in the pop-out entry window.
- Fixed the Labour Hours pop-out window so it loads the shared application stylesheet.
- Updated Tip Pool Breakdown net payout amounts to round to the nearest nickel for Canadian cash payout handling.
- Added Tip Pool Breakdown printing with a compact black-and-white employee payout report and totals row.
- Labour Management is accepted as working as intended for the v3.1.3 development build.

### Update System

- Added SHA-256 checksum generation to release packaging.
- GitHub Releases now receive the Windows installer and matching `.sha256` checksum asset.
- In-app update downloads now verify the installer against the published SHA-256 checksum before offering to launch it when a checksum asset is available.
- Added a manual Check for Updates action to the System screen.
- Added a release-script guard for the stable Windows upgrade UUID so future installers keep upgrading the existing installed app entry.

### Alcohol Variance

- Connected Alcohol Variance Report generation to completed alcohol counts, period purchase usage, imported POS usage, and active Alcohol Sales Mappings.
- Added category filtering and actual-vs-sold variance rows for the v3.1.3 development build.
- Converted Sales Mapping ounce/mL portions into inventory-equivalent usage for bottle and keg alcohol products before variance is calculated.
- Added red/green color coding for variance quantity and variance-dollar values.
- Alcohol Variance Report is accepted as complete for the v3.1.3 development build.

## v3.1.2

Date: 2026-09-07

### API Mode / Administration

- Retired direct desktop Cloud PostgreSQL mode from normal app use.
- Changed bundled defaults so new local configs start in API mode.
- Existing `mode=postgres` desktop configs now normalize to API mode on startup.
- Removed direct cloud mode switching, direct cloud connection testing, and upload-this-PC-to-cloud controls from the System screen.
- Kept SQLite as a local cloud-snapshot/backup target rather than a normal daily operating mode.
- Updated backup behavior in API mode so creating a backup downloads a fresh cloud snapshot before copying the local SQLite backup file.
- Moved the System administrator password to local salted PBKDF2 hash storage in `%LOCALAPPDATA%\FoodInventory\database.properties`.
- Added legacy plain-text password migration after successful login.
- Added the local `scripts/Reset-AdminPassword.ps1` password recovery helper.
- Enabled Change Password from the System screen.

### Labour Management

- Added Labour Management Phase 1 and Phase 2 in the development build.
- Added administrator-protected Labour Setup for configurable positions, employees, hourly wages, tip-pool eligibility, uniform-deduction applicability, active/inactive state, target labour percentages, and default uniform deduction settings.
- Added Labour Hours with an inventory-count-style saved-week list and a separate pop-out spreadsheet-style Monday-Sunday Shift 1 / Shift 2 employee-hour entry window.
- Removed employee number, hourly rate, total pay, restaurant header, position total rows, and bottom labour total rows from the Labour Hours entry grid.
- Added Daily Labour Cost as a weekly start/end date range screen with editable daily net sales, BOH labour dollars/percentages, FOH labour dollars/percentages, total labour percentage, and a total row.
- Added Tip Pool with editable daily tip-out pool amounts, tip-pool eligible employee hours, calculated employee daily tip allocations, and weekly totals.
- Added Tip Pool Breakdown with start/end date selection, gross tip allocation, configured per-worked-day uniform deductions for applicable employees, and net payout totals.
- Added `/labour/weeks` for Labour Hours saved-week listing.
- Added Daily Labour API routes `GET /labour/daily/{workDate}` and `PUT /labour/daily`.
- Added employee, position, labour group, hourly wage, tip-pool eligibility, and uniform-deduction context to daily labour rows so historical Labour Management views stay aligned with setup.
- Added schema migrations 16 and 17 for Labour Management tables and daily labour snapshot columns.
- Added and deployed initial Labour API routes for setup and Labour Hours bulk load/save.
- Added `scripts/Apply-LabourSchemaMigration.ps1` to apply the Labour schema to AWS RDS through a schema-capable admin user without storing the admin password.
- Deployed Labour API updates for Daily Labour Cost, Tip Pool, Tip Pool Breakdown support fields, and Labour Hours saved-week listing on Monday, September 7, 2026.

### Printing

- Fixed Inventory Count Sheet printing after the v3.1.1 stable release so count sheets dispatch after printer selection.
- Inventory Count Sheet printing now creates the page layout after printer selection, scales pages to the selected printer's printable area, and shows a clear print-failure alert.
- Alcohol count sheet printing now uses the API-backed alcohol profile client in Cloud API mode.

## v3.1.1

Date: 2026-09-05

### API Migration

- Completed API migration across the normal desktop workflows.
- Normal users no longer need to configure direct client database connections.
- Added a database status indicator for clearer runtime mode and connection visibility.
- Updated the deployed API health version to `3.1.1`.
- Added SAM-managed API Lambda log retention and imported the existing CloudWatch log group into the stack.

## v3.0.6

Date: 2026-08-31

### UI Reliability

- Fixed shared combo box support.

### API Proof

- Started a standalone API proof for moving PostgreSQL credentials off client PCs.
- Added local `GET /health` and `GET /products` endpoints.
- Added AWS Lambda/API Gateway deployment support.
- Deployed proof stack `esm-operations-api` in `ca-central-1`.
- Verified deployed `GET /health` returns version `3.0.6`.
- Verified deployed `GET /products` requires `x-api-key` and returns active products from AWS RDS.
- Added initial desktop API mode support with API URL/key configuration, System API health testing, and read-only Product list loading through the deployed API.
- Added deployed `GET /pos-menu-items` and read-only desktop POS Menu Items loading through API mode.
- Added deployed Alcohol Sales Mappings API endpoints for list, add, edit, and deactivate, and wired the desktop Alcohol Sales Mappings screen to use them in API mode.
- Migrated Food Department API mode workflows: Products, Import Invoice, Manual Invoice, Count Templates, Inventory Counts, and Order Guide.
- Migrated Alcohol Department API mode workflows: Products with alcohol profile maintenance, Manual Invoice, Count Templates, Inventory Counts, Order Guide, Product Profiles for weighted counts, and Sales Mappings.
- Added Production API backing in the development build for production setup, POS menu item maintenance/import/KDS cleanup, Weekly Production generation/loading/refresh/override saves, product mappings, and Freezer Pull manual quantities.
- Deployed Production API routes to `esm-operations-api` and read-only smoke-tested production setup, POS, weekly production, product mapping, and Freezer Pull endpoints.
- Deployed Food and Alcohol API routes to `esm-operations-api` and smoke-tested protected read endpoints, count-line loading, order-guide generation, alcohol profile loading, and missing-key `401` behavior.
- Confirmed Production CSV/report import and Weekly Production generation work in API mode.
- Added the missing Log4j runtime provider required by Apache POI so production report imports no longer print a missing logging provider warning.
- Supplies Department API mode was implemented and deployed after the initial v3.0.6 release notes.
- Reporting/Sales API mode was implemented and deployed for Invoice History, Sales Entry/import persistence, Inventory Valuation, and Weekly Cost Report generation.
- Product support gaps were implemented and deployed: GFS product guide CSV parsing remains client-side, normalized products are upserted through the API, and product purchase history loads through the API.
- The SAM template now uses one proxy API Gateway trigger for the Lambda router to avoid Lambda resource-policy size limits as the API surface grows.
- Administrative upload/download sync was implemented and deployed through the API; local SQLite backup/restore remains local file-copy behavior.
- Release packaging now generates an ignored API-mode config resource from release-time secret values, so updated client installs can be configured automatically without committing API credentials.

## v3.0.5

Date: 2026-08-30

### Navigation / UI

- Renamed the main department navigation to Food Department, Alcohol Department, and Supplies Department.
- Added alcohol-specific Sales Mappings and Variance Report entry points under Alcohol Department.
- Removed the generic Variance Reports placeholder from the Production menu.
- Added shared UI styling for section titles, status bar, danger buttons, and primary-button hover states.

### Alcohol Variance

- Added schema migration 15 for `alcohol_sales_mappings`.
- Added Alcohol Sales Mappings screen for POS SKU/PLU to alcohol inventory product setup.
- Included alcohol sales mappings in PostgreSQL schema initialization and cloud upload/download table sync.
- Reused the existing Production POS Menu Items catalog in Alcohol Sales Mapping add/edit dialogs through a searchable POS item picker.
- Kept manual POS SKU/name entry available as a fallback, while the normal mapping workflow now fills those fields from the selected POS item.
- Added a placeholder Alcohol Variance Report entry point under Alcohol Department for the next calculation step.

### Cloud / UI Reliability

- Migrated the active cloud database target from Aiven PostgreSQL to AWS RDS PostgreSQL after a successful dump/restore and application load test.
- Added and verified restricted AWS RDS `operations_app` access for normal installed-app use.
- Avoided repeated PostgreSQL create/index attempts when `alcohol_sales_mappings` already exists, allowing lower-access cloud app users to open Sales Mappings after schema setup.
- Improved menu-open error dialogs with root-cause text, console stack traces, and expandable details.
- Adjusted shared searchable ComboBox behavior to cap live popup matches for large POS/product lists and reduce JavaFX VirtualFlow warnings.
- Fixed the shared production-module view toolbar initialization order used by Production and Alcohol Sales Mapping screens.

### Alcohol Products

- Fixed saved keg-size display so reopening an alcohol product shows the saved container type instead of defaulting visually to Full Keg.

### Cloud Performance

- Reduced PostgreSQL connection-pool validation round trips for recently validated idle connections.
- Batch-loaded alcohol profile data in alcohol inventory count entry and count-sheet printing paths.

## v3.0.4

### Alcohol Manual Invoice

- Fixed alcohol manual invoice entry to include `FOOD` reporting-category products for non-alcohol beverages purchased from alcohol suppliers.
- Confirmed the alcohol manual invoice FOOD-item fix is implemented and working as intended.

## v3.0.3

### Alcohol Manual Invoice

- Updated alcohol manual invoice entry to include FOOD-category products for non-alcohol beverages bought from alcohol suppliers.
- Fixed HST Included and Bottle Deposit Included checkbox label visibility.

## v3.0.2

### Alcohol Manual Invoice

- Updated the alcohol manual invoice product list to include `FOOD` reporting-category products for non-alcohol beverages purchased from alcohol suppliers.
- Styled the HST Included and Bottle Deposit Included checkbox labels so they are visible on the dark invoice screen.

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
