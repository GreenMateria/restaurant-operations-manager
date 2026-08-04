# ROADMAP

## Cloud Database

### Status

-   v3.0.0 includes a controlled hybrid database release path.
-   SQLite remains the default startup mode.
-   Aiven PostgreSQL cloud mode is available through the password-protected System module and takes effect after restart.
-   Development connectivity, upload, download, DAO/service smoke tests, and manual UI testing against Aiven PostgreSQL `defaultdb` have been completed.
-   A lower-access Aiven application database user is configured for normal app access.
-   Full plan is documented in `CLOUD_DATABASE_PLAN.md`.

### Planned

-   Package and publish v3.0.0 after final release verification.
-   Validate v3.0.0 on two PCs using SQLite default mode, System mode switching, upload, and download.
-   Continue controlled cloud-mode live testing before making Cloud PostgreSQL the normal operating mode.
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

-   Implemented features through v3.0.0 are working as intended.
-   Alcohol workflow changes should be driven by specific live-data issues when they appear.
-   Freezer Pull is working as intended and is not an active roadmap item.

### Future

-   Production Variance
-   Recipe Costing
-   Advanced Reporting
