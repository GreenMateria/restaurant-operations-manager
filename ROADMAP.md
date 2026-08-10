# ROADMAP

## Cloud Database

### Status

-   Cloud database setup is complete for the current work PCs.
-   v3.0.2 working tree includes the completed controlled hybrid database release path plus cloud-mode save/import responsiveness fixes.
-   SQLite remains the default startup mode for fresh installs and fallback use.
-   Configured work PCs should run in Aiven PostgreSQL cloud mode for daily shared-data operation.
-   Aiven PostgreSQL cloud mode is available through the password-protected System module and takes effect after restart.
-   Development connectivity, upload, download, DAO/service smoke tests, and manual UI testing against Aiven PostgreSQL `defaultdb` have been completed.
-   A lower-access Aiven application database user is configured for normal app access.
-   All current work PCs have connected to the cloud database without error.
-   The cloud database is now treated as the accurate master data source.
-   Inventory count save/complete, Sales report import/save, and Weekly Production usage report import/generation now run long work off the JavaFX thread where applicable.
-   Full plan is documented in `CLOUD_DATABASE_PLAN.md`.

### Planned

-   Package and publish the next installer with the v3.0.2 cloud performance and POS sales import fixes.
-   Validate the v3.0.2 installer upgrade on the work PCs.
-   Continue monitoring cloud-mode daily use for workflow or record-edit conflicts.
-   Consider a backend/API layer later if the app expands beyond controlled internal use.

## Update System

### Completed

-   Automatic startup update checking
-   GitHub Release version comparison
-   Version shown in application UI
-   Version shown in window title
-   In-app installer download
-   Download progress dialog
-   Update-ready installer launch prompt
-   Stable Windows installer upgrade UUID in release packaging

### Planned

-   Installer verification / checksum handling
-   Manual Check for Updates action
-   Additional failure and retry polish around updater UX
-   Verify first installer upgrade built with the stable upgrade UUID replaces the existing install cleanly

## Alcohol Inventory

### Status

-   Implemented features through v3.0.2 are working as intended.
-   Alcohol workflow changes should be driven by specific live-data issues when they appear.
-   Freezer Pull is working as intended and is not an active roadmap item.

### Future

-   Production Variance
-   Recipe Costing
-   Advanced Reporting
