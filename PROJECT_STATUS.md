# PROJECT STATUS

## Current Version

**Current Project Version:** v3.0.0

Current database schema version: **13**

Application compiles successfully.

All implemented features through v3.0.0 are considered working as intended.

# Completed This Session

## Current Working State

Completed:

-   PostgreSQL JDBC dependency and controlled cloud database mode using Aiven PostgreSQL
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

Current behaviour:

-   Default startup still uses the local SQLite runtime database.
-   PostgreSQL mode must be explicitly enabled for development testing.
-   PostgreSQL mode has passed broad DAO/service smoke tests and manual JavaFX UI testing, including the previously slow Weekly Production, Freezer Pull, and Inventory Valuation screens.
-   Installed versions automatically check GitHub Releases on startup.
-   If a newer version exists, the user is prompted to download it.
-   The installer is downloaded inside the application when a Windows installer asset is available.
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

1.  Package and publish v3.0.0 when ready.
2.  Validate the first v3.0.0 installer upgrade on another PC and confirm existing local data/config are preserved.
3.  Continue controlled live testing of SQLite default mode and optional Cloud PostgreSQL mode.
4.  Add installer verification / checksum handling.
5.  Add a manual Check for Updates action.
6.  Confirm future installer upgrades replace the existing installed app entry and do not create duplicate installs.
7.  Resume Production Variance development.
8.  Continue advanced reporting and workflow polish.
9.  Address alcohol workflow changes only when live-data validation identifies a concrete issue.
