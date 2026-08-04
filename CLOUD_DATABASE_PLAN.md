# CLOUD DATABASE PLAN

_Created: Saturday, August 1, 2026_
_Last Updated: Tuesday, August 4, 2026_

This document tracks the planned cloud database transition for ESM Operations Manager.

## Goal

Allow multiple installed PCs to access the same live application data without manually backing up, transferring, and restoring local SQLite database files.

The target workflow is:

```text
PC 1 app \
PC 2 app  -> Aiven PostgreSQL database
PC 3 app /
```

Each PC connects outbound to the cloud database. The office PC does not need to accept remote connections, port forwarding, VPN access, or outside network access.

## Current State

As of v3.0.0, the application supports a controlled hybrid rollout:

```text
Default startup -> local SQLite
Optional mode   -> Aiven PostgreSQL
```

The mode can be changed from the password-protected System module. The selected mode is stored in `%LOCALAPPDATA%\FoodInventory\database.properties` and takes effect after application restart. Fresh installs seed this config file from bundled defaults when it does not already exist.

The default released application mode uses a local SQLite database on each installed PC:

```text
%LOCALAPPDATA%\FoodInventory\food_inventory.db
```

Current manual sync process:

```text
PC 1 local SQLite -> database backup -> transfer file -> restore on PC 2
```

This process works, but it is manual and creates separate database copies that can drift apart.

Cloud connectivity status:

- Java connection test succeeded on Sunday, August 2, 2026.
- Connected to Aiven PostgreSQL `defaultdb` using SSL.
- Initial test used the Aiven admin user only for connection proof and schema/grant setup.
- Normal app access now uses a lower-access Aiven application database user.

Cloud upload status:

- SQLite-to-PostgreSQL upload succeeded on Sunday, August 2, 2026.
- Source database:

```text
C:\Users\mrman\AppData\Local\FoodInventory\food_inventory.db
```

- Target database:

```text
Aiven PostgreSQL defaultdb
```

- Rows uploaded: `6576`.
- Read-back spot checks from PostgreSQL confirmed the uploaded table counts.
- PostgreSQL schema initializer was aligned to the live SQLite schema, including `production_profile_lines.yield_factor`.

Cloud runtime compatibility status:

- Products DAO smoke test succeeded against Aiven PostgreSQL on Tuesday, August 4, 2026.
- Tested product list loading, product import-style upsert, detail update, SKU alias upsert, deactivate, and purchase history lookup.
- Product list loading now batches alcohol product profiles instead of querying once per product row.
- Invoice DAO smoke test succeeded against Aiven PostgreSQL on Tuesday, August 4, 2026.
- Tested invoice list loading, duplicate check, invoice line loading, adjustment loading, category breakdown, save, and delete.
- Inventory count template/count DAO smoke test succeeded against Aiven PostgreSQL on Tuesday, August 4, 2026.
- Tested template add, template line add, duplicate template, count creation, count line creation, quantity update, completion, and cleanup.
- Inventory valuation and weekly cost report smoke tests succeeded against Aiven PostgreSQL on Tuesday, August 4, 2026.
- Tested valuation for uploaded count `20`, weekly cost report for opening count `15` and closing count `18`, and sales period save/upsert/read/totals with temporary cleanup.
- Order Guide generation smoke test succeeded against Aiven PostgreSQL on Tuesday, August 4, 2026, returning `189` rows for opening count `15` and closing count `18`.
- Production setup/week smoke tests succeeded against Aiven PostgreSQL on Tuesday, August 4, 2026.
- Tested production stations, production items, production profiles, production profile lines, POS menu items, product mappings, generated weeks, week days, week lines, override save, Freezer Pull setting persistence, and cleanup.
- A broad PostgreSQL read smoke check passed for order guide, production setup data, existing production weeks, sales category mappings, and system settings.

SQLite regression check:

- Default SQLite runtime path was re-verified on Tuesday, August 4, 2026, with no PostgreSQL mode environment variable set.
- Confirmed runtime database path:

```text
C:\Users\mrman\AppData\Local\FoodInventory\food_inventory.db
```

- Smoke-tested existing product, invoice, count, valuation, weekly report, and sales-period reads.
- Smoke-tested temporary product, SKU alias, invoice, template/count, count quantity, and sales-period writes.
- Temporary SQLite smoke-test records were cleaned up.

## Planned Cloud Provider

Initial test provider:

```text
Aiven for PostgreSQL
```

Reasoning:

- Free PostgreSQL tier is available.
- No inbound access to the office network is required.
- PostgreSQL is a proper shared database server, unlike a shared SQLite file.
- Aiven provides hosted connection details, SSL support, and managed backups on the free PostgreSQL tier.

## Important Implementation Rule

SQLite remains the default startup mode.

The v3.0.0 release includes cloud support, but it is intentionally controlled:

```text
Normal users  -> local SQLite default
Administrator -> optional System-menu mode switch and upload/download tools
```

Do not make Cloud PostgreSQL the normal operating mode until the v3.0.0 hybrid release has been tested on the intended PCs.

## Current Cloud Database

Current Aiven database:

```text
defaultdb
```

Preferred future production database name, if the app later separates test and production services:

```text
food_inventory_prod
```

Use a separate development database or Aiven service for destructive testing once live cloud data is treated as production data.

## Recommended App User

Use a dedicated Aiven database user for the app:

```text
operations_app
```

Do not use the main Aiven admin user inside the desktop application.

The app user should be used with:

- Restricted permissions appropriate for the application
- SSL-required connections
- Lower-access permissions for normal table/sequence usage
- Credentials stored outside public source control

## User Experience Target

Normal users should not configure database connections.

Expected user workflow:

```text
Open app
App connects automatically
User completes normal work
All PCs see the same current data
```

The application may later show a simple status in the System screen:

```text
Database: Online
Last Connected: Aug 1, 2026 2:15 PM
```

If the cloud database cannot be reached, the app should show a plain-language message:

```text
Cloud database unavailable.
Please check the internet connection or contact support.
```

## Local Safety Backup Strategy

The preferred "just in case" design is cloud-first with local backups, not full offline sync.

Recommended workflow:

```text
App writes live data -> Aiven PostgreSQL
App periodically saves local safety backup/export
```

This protects against accidental loss or provider issues without introducing complex sync conflicts.

Avoid true offline editing and sync for the first version. True offline sync requires conflict handling, device IDs, change logs, timestamps, deleted markers, and merge rules.

## Development Phases

### Phase 1: Aiven Setup

- Create Aiven PostgreSQL service.
- Create `food_inventory_dev` database.
- Create `food_inventory_app` user.
- Confirm connection details are available:
  - Host
  - Port
  - Database
  - Username
  - Password
  - SSL requirement

### Phase 2: Connection Proof

- Add PostgreSQL JDBC dependency in a development branch/build. Done.
- Add a dev-only database configuration mechanism. Done.
- Confirm the Java app can connect to Aiven. Done.
- Do not migrate production data yet.

Supported environment configuration names:

```text
FOOD_INVENTORY_DB_MODE=postgres
FOOD_INVENTORY_DB_URL=jdbc:postgresql://<host>:<port>/food_inventory_dev?sslmode=require
FOOD_INVENTORY_DB_USER=operations_app
FOOD_INVENTORY_DB_PASSWORD=<app-user-password>
```

Equivalent JVM system properties are also supported:

```text
-Dfoodinventory.db.mode=postgres
-Dfoodinventory.db.url=jdbc:postgresql://<host>:<port>/food_inventory_dev?sslmode=require
-Dfoodinventory.db.user=operations_app
-Dfoodinventory.db.password=<app-user-password>
```

When PostgreSQL mode is active, startup uses the PostgreSQL connection path and validates the schema for lower-access app users. Schema-capable users can initialize or repair the PostgreSQL schema.

Use this command-line class for the first connection proof so the JavaFX app does not try to load application tables before PostgreSQL schema support exists:

```text
ca.foodinventory.database.DatabaseConnectionCheck
```

Local IntelliJ run configurations have been added:

```text
ESM Operations Manager - SQLite
Aiven Connection Check
ESM Operations Manager - PostgreSQL Dev
```

The PostgreSQL configurations should use the lower-access application database user, not the Aiven admin user.

### Phase 3: Schema Compatibility

- Review current SQLite schema and migrations. Done.
- Identify SQLite-specific SQL that needs PostgreSQL equivalents. Done.
- Create PostgreSQL-compatible schema setup. Done.
- Test database startup and lower-access schema validation against Aiven PostgreSQL. Done.

### Phase 4: Data Migration And Sync Test

- Use a copy of the current SQLite runtime database. Done for development upload.
- Export data from SQLite. Done.
- Import data into `food_inventory_dev`. Done against Aiven `defaultdb` for initial development testing.
- Test all major app workflows against Aiven:
  - Products. DAO smoke test complete.
  - Invoices. DAO smoke test complete.
  - Inventory count templates. DAO smoke test complete.
  - Inventory count entry. DAO smoke test complete.
  - Inventory valuation
    - DAO/service smoke test complete.
  - Sales entry
    - Sales period DAO smoke test complete.
  - Order guides
    - Service smoke test complete.
  - Alcohol workflows. Manual UI/report testing complete for current reported issue.
  - Production workflows
    - DAO/service smoke test complete.
  - System screen
    - Settings DAO smoke test complete. Backup/restore remains intentionally blocked in cloud mode.

Manual UI validation:

- Full UI pass completed in PostgreSQL mode.
- Weekly Production, Freezer Pull, and Reporting -> Inventory Valuation were retested after optimization and no longer hang in the tested data set.
- Weekly Cost Report department filtering was corrected and retested.

Cloud timing smoke after the fix, using the uploaded Aiven data:

```text
pass=1 week_list_ms=745 weekly_ms=1186 freezer_ms=305 valuation_ms=350 weekly_lines=483 valuation_lines=84
pass=2 week_list_ms=254 weekly_ms=547 freezer_ms=274 valuation_ms=285 weekly_lines=483 valuation_lines=84
```

These timings do not replace manual UI testing, but they confirm the slow paths no longer depend on one database connection or query per row.

Development upload tooling has been added:

```text
ca.foodinventory.database.SQLiteToPostgresUploadTool
```

Default SQLite source:

```text
%LOCALAPPDATA%\FoodInventory\food_inventory.db
```

The project root also contains an older `food_inventory.db`; do not use that file unless specifically testing with the older copy.

The upload tool:

- Creates the PostgreSQL schema if needed.
- Replaces data in the target PostgreSQL tables.
- Preserves source row IDs so relationships remain intact.
- Resets PostgreSQL serial sequences after import.
- Rolls back the full upload if any table fails.
- Requires `FOOD_INVENTORY_UPLOAD_CONFIRM=UPLOAD_TO_POSTGRES`.

The password-protected System module now includes a simple Database Sync interface:

- Test Cloud Connection
- Upload This PC to Cloud
- Download Cloud to This PC
- Use SQLite Mode
- Use Cloud Mode

The visible user flow stays simple:

- `Migrating data...`
- `Data migration complete.`
- `Data migration failed. Please contact the administrator.`

Upload replaces Aiven with this PC's local SQLite data. Download backs up the local SQLite database first, then replaces this PC's local SQLite data with Aiven data. The download completion message tells the user to restart the application.

Mode switching writes the selected next-startup mode to:

```text
%LOCALAPPDATA%\FoodInventory\database.properties
```

The running session keeps using its startup database mode. Restart the application after changing modes.

Fresh installs create this file automatically from the bundled default config if it does not already exist. The bundled default starts in SQLite mode and intentionally leaves cloud connection credentials blank.

Local IntelliJ run configuration:

```text
Upload SQLite to Aiven PostgreSQL
```

Before running it, replace these local placeholders in IntelliJ:

```text
FOOD_INVENTORY_DB_PASSWORD=REPLACE_WITH_AIVEN_PASSWORD
FOOD_INVENTORY_UPLOAD_CONFIRM=REPLACE_WITH_UPLOAD_TO_POSTGRES
```

Only set the confirmation value to this exact text when intentionally replacing Aiven data:

```text
UPLOAD_TO_POSTGRES
```

### Phase 5: Local Backup Support

- Add local backup/export behavior for cloud download. Done.
- Cloud-to-local download backs up the local SQLite database first. Done.
- Regular Database Backup and Restore are intentionally blocked while cloud mode is active.
- Showing last backup status in the System screen remains optional future polish.

### Phase 6: Release Decision

- v3.0.0 is the controlled hybrid release candidate.
- Release installer starts in SQLite mode by default.
- Database details are hidden from normal workflows.
- Cloud controls live behind the existing password-protected System module.

## Security Notes

- Do not commit Aiven admin passwords.
- Do not hardcode the Aiven admin password in the app.
- Use a dedicated app database user.
- Require SSL.
- The v3.0.0 rollout seeds local config from safe bundled defaults; cloud credentials must be supplied locally outside source control.
- Consider a backend/API layer later if stronger credential protection is needed.

## Long-Term Architecture Option

The simplest first version is:

```text
Desktop app -> Aiven PostgreSQL
```

A more secure long-term architecture is:

```text
Desktop app -> application backend/API -> PostgreSQL
```

The direct PostgreSQL approach is acceptable for a controlled internal desktop app test, but the backend/API design should be reconsidered if the app expands to more users, more locations, or more sensitive permissions.
