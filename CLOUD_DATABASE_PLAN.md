# CLOUD DATABASE PLAN

_Created: Saturday, August 1, 2026_
_Last Updated: Tuesday, September 8, 2026_

This document records the completed cloud database transition for ESM Operations Manager.

## Goal

Allow multiple installed PCs to access the same live application data without manually backing up, transferring, and restoring local SQLite database files.

The target workflow is:

```text
PC 1 app \
PC 2 app  -> Cloud API -> AWS RDS PostgreSQL database
PC 3 app /
```

Each PC connects outbound to the cloud database. The office PC does not need to accept remote connections, port forwarding, VPN access, or outside network access.

## Current State

As of Monday, September 7, 2026, the active cloud database target for configured work PCs is AWS RDS PostgreSQL behind the Cloud API:

```text
Normal work PCs -> Cloud API mode -> AWS RDS PostgreSQL postgres database
SQLite         -> local cloud snapshot, backup/recovery file, and development fallback
```

The previous Aiven PostgreSQL cloud database was dumped and restored into AWS RDS PostgreSQL. Development and installed-app connection tests succeeded, data loaded correctly, and AWS RDS performed faster in the tested workflows. The AWS RDS database is now treated as the accurate master data source for normal daily use.

Direct desktop Cloud PostgreSQL mode has been retired from normal app use. The password-protected System module no longer exposes direct PostgreSQL mode switching or upload-this-PC-to-cloud controls. Fresh installs seed `%LOCALAPPDATA%\FoodInventory\database.properties` from bundled defaults when it does not already exist, and configured release desktops should use API mode for daily operation.

An API layer proof now exists for the next credential-protection step:

```text
Desktop app -> API Gateway HTTP API -> AWS Lambda -> AWS RDS PostgreSQL
```

The API stack is deployed as `esm-operations-api` in `ca-central-1`. As of the current development build after v3.1.1, the desktop app can use API mode for normal Food Department, Alcohol Department, Supplies Department, Production, Reporting/Sales, Labour Setup, Labour Hours, Daily Labour Cost, Tip Pool, Tip Pool Breakdown, GFS product guide import persistence, product purchase history, and administrative cloud snapshot workflows. Current work PCs should use Cloud API mode for daily operation.

The local SQLite file is retained as the backup/snapshot target:

```text
%LOCALAPPDATA%\FoodInventory\food_inventory.db
```

Previous manual sync process:

```text
PC 1 local SQLite -> database backup -> transfer file -> restore on PC 2
```

This process worked, but it was manual and created separate database copies that could drift apart.

Current normal operating process:

```text
PC 1 app \
PC 2 app  -> Cloud API -> AWS RDS PostgreSQL
PC 3 app /
```

When the app is running in Cloud API mode, normal reads and saves go through the API to the shared cloud database. Multiple PCs can be connected at the same time. The application does not currently provide live screen refresh or edit-conflict warnings, so users should reopen or refresh a screen to see another user's recent changes and should avoid editing the same record at the same time.

Cloud connectivity status:

- AWS RDS PostgreSQL connection test succeeded on Sunday, August 30, 2026.
- Aiven PostgreSQL `defaultdb` was dumped with `pg_dump` and restored into AWS RDS PostgreSQL with `pg_restore` on Sunday, August 30, 2026.
- AWS RDS schema/table restoration was verified, including 24 public tables and key application table counts.
- The development app and installed app loaded data successfully from AWS RDS PostgreSQL.
- Normal app access now uses a lower-access AWS RDS `operations_app` database user.
- Labour Management schema version 17 was applied to AWS RDS on Sunday, September 6, 2026 after deploying the initial Labour Hours API routes.
- Daily Labour Cost, Tip Pool support fields, and Labour Hours saved-week listing were deployed on Monday, September 7, 2026.
- Labour Management was manager-tested and accepted as working as intended on Tuesday, September 8, 2026.
- `scripts/Apply-LabourSchemaMigration.ps1` is available for the Labour schema setup path and prompts securely for the RDS admin password.
- Java connection test succeeded on Sunday, August 2, 2026.
- Connected to Aiven PostgreSQL `defaultdb` using SSL.
- Initial test used the Aiven admin user only for connection proof and schema/grant setup.
- The Aiven app user remains historical/fallback only after the AWS RDS cutover.

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
- Work PCs were connected to the cloud database without error by Sunday, August 9, 2026.
- The cloud database is now the master copy for current production data.

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

Current provider:

```text
AWS RDS for PostgreSQL
```

Reasoning:

- AWS RDS does not power off from inactivity in the tested provisioned configuration.
- No inbound access to the office network is required.
- PostgreSQL is a proper shared database server, unlike a shared SQLite file.
- AWS RDS provides hosted connection details, SSL-capable PostgreSQL access, backups, and better tested responsiveness for this app than the previous Aiven free-tier service.

Earlier provider:

```text
Aiven for PostgreSQL
```

Aiven was used for the initial cloud rollout and remains a temporary fallback copy, but it is no longer the intended active database because the free-tier service can power off after prolonged inactivity.

## Important Implementation Rule

SQLite remains the local backup/snapshot and development fallback path.

The v3.0.1 release includes completed cloud support for the current internal rollout:

```text
Normal work PCs -> Cloud API mode
SQLite snapshot -> backup/recovery file and cloud download target
Administrator   -> System-menu API connection test, backup, restore, and cloud snapshot download
```

Cloud API mode is the normal operating mode on the current work PCs. The old Upload This PC to Cloud workflow has been removed from normal desktop use.

## Current Cloud Database

Current AWS RDS database:

```text
postgres
```

Current AWS RDS endpoint:

```text
esm-operations-db.cdcok68as3cr.ca-central-1.rds.amazonaws.com
```

Current JDBC URL shape:

```text
jdbc:postgresql://esm-operations-db.cdcok68as3cr.ca-central-1.rds.amazonaws.com:5432/postgres?sslmode=require
```

Preferred future production database name, if the app later separates test and production services:

```text
food_inventory_prod
```

Use a separate development database or RDS instance for destructive testing once live cloud data is treated as production data.

## Recommended App User

Use a dedicated AWS RDS database user for the app:

```text
operations_app
```

Do not use the main AWS RDS admin user inside the desktop application or Lambda runtime.

Use a schema-capable admin connection only for controlled migrations/grants, then keep normal runtime on the restricted app user.

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
App writes live data -> AWS RDS PostgreSQL
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

Historical v3.0.1 direct PostgreSQL rollout used a password-protected Database Sync interface:

- Test Cloud Connection
- Upload This PC to Cloud
- Download Cloud to This PC
- Use SQLite Mode
- Use Cloud Mode

The visible user flow stays simple:

- `Migrating data...`
- `Data migration complete.`
- `Data migration failed. Please contact the administrator.`

In that retired direct-sync workflow, upload replaced the configured cloud PostgreSQL database with this PC's local SQLite data. Current release desktops should use Cloud API mode and cloud snapshot download instead.

The retired direct mode-switching workflow wrote the selected next-startup mode to:

```text
%LOCALAPPDATA%\FoodInventory\database.properties
```

Current release desktops should not use direct PostgreSQL mode switching for normal work.

Fresh installs create this file automatically from the bundled default config if it does not already exist. The bundled default starts in API mode and intentionally leaves API credentials blank unless release packaging supplies ignored release-only settings.

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
- In API mode, regular Backup downloads a fresh cloud snapshot before copying the local SQLite backup file. Restore remains a local SQLite emergency recovery action.
- Showing last backup status in the System screen remains optional future polish.

### Phase 6: Release Decision

- v3.0.1 was the direct cloud database release candidate planned for release on Sunday, August 9, 2026.
- Current release installers should start in API mode when release API settings are packaged.
- Database details are hidden from normal workflows.
- Current cloud backup and API diagnostic controls live behind the existing password-protected System module.
- Current work PCs should run in Cloud API mode for daily shared-data use, with AWS RDS PostgreSQL behind the API.

### Phase 7: AWS RDS Cutover

- Created AWS RDS PostgreSQL database in `ca-central-1`.
- Confirmed direct Java/JDBC connection from the development environment.
- Dumped Aiven PostgreSQL `defaultdb` with `pg_dump`.
- Restored the dump into AWS RDS PostgreSQL database `postgres` with `pg_restore`.
- Verified restored schema and key table row counts.
- Launched the development app against AWS RDS and confirmed data loaded.
- Updated installed-app `database.properties` to AWS RDS and confirmed screens loaded.
- Created and verified lower-access AWS RDS `operations_app` user for normal app access.
- Aiven remains temporary fallback only; do not write new production data to Aiven unless intentionally rolling back.

## Security Notes

- Do not commit AWS RDS or Aiven admin passwords.
- Do not hardcode database passwords in the app.
- Use a dedicated app database user.
- Require SSL.
- The app seeds local config from safe bundled defaults; cloud credentials must be supplied locally outside source control.
- Continue moving toward the backend/API layer so PostgreSQL credentials do not need to live on client PCs.

## Long-Term Architecture Option

The simplest first version is:

```text
Desktop app -> Aiven PostgreSQL
```

The current operational version of that direct-database architecture is:

```text
Desktop app -> AWS RDS PostgreSQL
```

A more secure long-term architecture is:

```text
Desktop app -> application backend/API -> PostgreSQL
```

The first version of this architecture has been deployed with API Gateway HTTP API and AWS Lambda. API mode now covers Food Department, Alcohol Department, Supplies Department, Production, Reporting/Sales, Labour Setup, Labour Hours, Daily Labour Cost, Tip Pool, Tip Pool Breakdown, GFS product guide import persistence, product purchase history, and administrative cloud snapshot workflows. Direct desktop PostgreSQL mode has been retired from normal client use.
