# ROADMAP

## Cloud Database

### Status

-   Cloud database setup is complete for the current work PCs.
-   v4.2.0 is the current stable release target.
-   v3.0.6 includes the completed controlled hybrid database release path, AWS RDS cutover, cloud-mode save/import responsiveness fixes, POS sales import fix, alcohol manual invoice FOOD-item fix, alcohol Sales Mappings, initial Alcohol Variance entry points, and shared combo box support fixes.
-   v3.1.1 completed the desktop API migration, removed the need for normal users to configure direct client database connections, and added a database status indicator.
-   Cloud API mode is now the normal desktop startup mode when API settings are configured.
-   v4.0.0 added multi-location Store Login, location-scoped API reads/writes, location-aware uniqueness in PostgreSQL, and setup-copy tooling between stores.
-   v4.2.0 is the cleaned presentation release: StoreOps Manager branding, neutral logo, polished card-based navigation, maximized launch, About panel, and Switch Store support.
-   v4.2.0 also adds store-scoped System administrator and Labour Setup protected passwords in Cloud API mode so protected access follows the signed-in store across workstations.
-   SQLite remains only as a local cloud-snapshot/backup target and development fallback.
-   Configured work PCs should run in Cloud API mode for daily shared-data operation, with AWS RDS PostgreSQL as the backend data store.
-   Direct desktop AWS RDS PostgreSQL mode has been retired from the System module.
-   Development connectivity, upload, download, DAO/service smoke tests, and manual UI testing against Aiven PostgreSQL `defaultdb` have been completed.
-   A lower-access Aiven application database user is configured for normal app access.
-   All current work PCs have connected to the cloud database without error.
-   The cloud database is now treated as the accurate master data source.
-   On Sunday, August 30, 2026, production cloud data was copied from Aiven PostgreSQL to AWS RDS PostgreSQL.
-   AWS RDS PostgreSQL is now the active cloud database target for configured work PCs.
-   A restricted AWS RDS `operations_app` database user is configured for normal app access.
-   Inventory count save/complete, Sales report import/save, and Weekly Production usage report import/generation now run long work off the JavaFX thread where applicable.
-   Full plan is documented in `CLOUD_DATABASE_PLAN.md`.

### Planned

-   Validate alcohol Sales Mappings setup from the POS Menu Items catalog during live bar item review.
-   Continue monitoring cloud-mode daily use for workflow or record-edit conflicts.
-   Continue using the backend/API layer as the normal client path and keep direct PostgreSQL credentials out of desktop clients.

## Future Web / API Architecture

### Status

-   API layer planning has started in `API_LAYER_PLAN.md`.
-   The main goal is to remove PostgreSQL credentials from client PCs by moving direct database access behind an authenticated HTTPS API.
-   Recommended first AWS direction is Amazon API Gateway HTTP API plus AWS Lambda in front of the current AWS RDS PostgreSQL database.
-   AWS proof stack `esm-operations-api` is deployed in `ca-central-1`.
-   Proof API URL is `https://rn0j30p2vf.execute-api.ca-central-1.amazonaws.com/prod`.
-   `GET /health` is public and returns the deployed API version.
-   `GET /products` requires `x-api-key` and has been verified against AWS RDS with `359` active products returned.
-   The desktop app now supports `mode=api`, `api.url`, and `api.key` configuration.
-   The System screen can test the API health endpoint and set API mode for next startup.
-   The Products screen can load active products from `GET /products` in API mode.
-   Food, Alcohol, and Supplies Products add/edit/deactivate are implemented for API mode; GFS product guide CSV files are still parsed client-side, then normalized product records are sent to `POST /products/import`.
-   `GET /pos-menu-items` requires `x-api-key` and has been verified against AWS RDS with `642` POS menu items returned.
-   The POS Menu Items screen can load POS menu items from `GET /pos-menu-items` in API mode.
-   Product purchase history is available in API mode through `GET /products/{id}/purchase-history`.
-   POS Menu Item writes/imports, KDS cleanup, and usage-report import are implemented for API mode through the Production API.
-   Alcohol Sales Mappings API endpoints for list, add, edit, and deactivate are deployed.
-   The Alcohol Sales Mappings screen can load, save, and deactivate mappings through the API in API mode.
-   Alcohol Variance Report is complete in the desktop app by reusing API-backed counts, order guide usage, valuation cost, product profiles, sales mappings, and client-side POS usage import.
-   API migration strategy is department-first. Food Department, Alcohol Department, Supplies Department, Production, Reporting/Sales, Labour Setup, Labour Hours, Daily Labour Cost, Tip Pool, Tip Pool Breakdown, product support, and administrative cloud snapshot workflows are now API-backed.
-   Supplies Department API routes were deployed on Saturday, September 5, 2026, and read-only smoke checks passed.
-   Food Department API migration is implemented and deployed for products, import/manual invoice saves, count templates, inventory counts, and order guide generation/case-size save.
-   Read-only smoke checks passed for Food templates, counts, completed counts, count lines, order guide generation, and missing-key `401` behavior.
-   Alcohol Department API migration is implemented and deployed for product/profile maintenance, manual invoice saves, count templates, inventory counts, order guide generation/case-size save, and alcohol product profile reads for weighted inventory counts.
-   Read-only smoke checks passed for Alcohol templates, counts, completed counts, count lines, order guide generation, alcohol product profiles, and missing-key `401` behavior.
-   Product API smoke check passed for Alcohol profile loading: 85 Alcohol products returned and all 85 include active profile data.
-   Production API backing is implemented in the development build for production setup, POS menu item maintenance/import/KDS cleanup, Weekly Production generation/loading/refresh/override saves, product mappings, and Freezer Pull manual quantities.
-   Production API backing was deployed to `esm-operations-api` in `ca-central-1`; read-only smoke checks passed for the production setup, POS, weekly production, product mapping, and Freezer Pull endpoints.
-   Live desktop validation confirmed Production CSV/report import and Weekly Production generation complete in API mode, and the Apache POI Log4j provider warning has been resolved.
-   Reporting/Sales and product support routes were deployed to `esm-operations-api` in `ca-central-1` on Saturday, September 5, 2026; smoke checks passed for Invoice History, invoice line/breakdown loading, Sales Periods, Inventory Valuation, Weekly Cost Report, product purchase history, and missing-key `401` behavior.
-   Administrative upload/download sync routes were deployed on Saturday, September 5, 2026; smoke checks passed for missing-key `401` behavior and authenticated read-only cloud snapshot download.
-   Labour Setup, Labour Hours, Daily Labour Cost, Tip Pool, and Tip Pool Breakdown API support is implemented in the development build for positions, employees, default uniform deduction settings, weekly employee hour entry, manual daily net sales, daily tip pool amounts, labour cost percentages, tip allocation, and uniform-deduction payout reporting.
-   Normal API workflows for Food, Alcohol, Supplies, Production, Reporting/Sales, product import, and product purchase history are confirmed working for the v4.2.0 release target.
-   Store Login and location token enforcement are deployed for the main store-owned API workflows; TEST-store validation confirmed store data isolation.
-   Protected-password API routes are deployed, and the live AWS RDS schema has been advanced through schema version 22.

### Future

-   Monitor the multi-location rollout and add reporting rollups or additional administration tooling only after first-store validation.
-   Convert the remaining manually managed cloud infrastructure into Infrastructure as Code after the first multi-location rollout feedback is clear.
-   Consider a browser-based version after the current desktop application is stable, core workflows are proven in daily use, and multi-location requirements are clearer.
-   Treat this as a long-term migration path, not an immediate rewrite.
-   Preferred future architecture:

```text
Browser frontend
-> API/backend
-> PostgreSQL database
```

-   Near-term desktop architecture:

```text
Desktop app
-> HTTPS API
-> PostgreSQL database
```

-   React is a reasonable candidate for the browser frontend, replacing the current JavaFX UI layer.
-   Java can remain the backend language through an API service, preserving more of the current project knowledge and business logic than a full JavaScript rewrite.
-   A backend/API layer would keep database credentials off client machines, centralize business rules, support stronger authentication, and make auditing/permissions easier.
-   Plan any migration in phases:
    -   Keep the current JavaFX desktop app stable for internal operations.
    -   Strengthen service boundaries so business logic is not trapped in JavaFX screens.
    -   Reuse the existing Java API/backend and extend it only when browser-access needs are clear.
    -   Build browser screens module by module, starting with lower-risk reporting/admin views before replacing core workflows.
    -   Retire the desktop app only after the browser version fully covers daily manager workflows.
-   Re-evaluate this direction after Cloud API daily use, alcohol variance, reporting, order guide, production, and Labour Management workflows are stable enough that requirements are no longer changing quickly.

### Planned

-   Extend the completed multi-location foundation with reporting rollups, backup policies by store, and safer non-direct-RDS admin operations when requirements are clear.
-   Use that design to decide what cloud resources should move into IaC first.
-   Keep CSV/Excel parsing in the desktop client, with only normalized records sent through the API for persistence unless a browser/file-processing requirement changes that.
-   Keep direct Cloud PostgreSQL credentials out of normal desktop clients.

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
-   Installer SHA-256 checksum generation and GitHub Release upload
-   In-app installer checksum verification before launch when a checksum asset is published
-   Manual Check for Updates action from the System screen
-   Release script guard that prevents accidental Windows upgrade UUID changes

### Planned

-   Additional failure and retry polish around updater UX

## Purchasing / Invoice Import

### Future

-   Add invoice split rules for supplier combo-case products, such as takeout container cases that contain both lids and bottoms but import as one supplier line.
-   A split rule should map one imported supplier SKU to multiple inventory products, with a quantity-per-imported-unit and cost allocation for each split line.
-   During invoice import, the preview/save workflow should replace the imported combo line with the configured inventory lines so valuation, purchase history, and count workflows track the individual products.
-   Example: one imported case of takeout containers can become separate lid and bottom invoice lines, each with its own inventory quantity and allocated cost.
-   Keep the first version simple:
    -   supplier
    -   supplier SKU
    -   child inventory product
    -   quantity per imported unit
    -   cost percentage or cost ratio
    -   active flag
-   Show enough context in the invoice preview to make clear that generated lines came from a split supplier SKU, without double-counting the original imported line.
-   This should be treated as a purchasing/import enhancement, not a manual inventory-count workaround.

## Alcohol Department

### Status

-   Implemented features through v3.1.1 are working as intended.
-   Alcohol manual invoice entry includes `FOOD` reporting-category products for non-alcohol beverages purchased from alcohol suppliers; this fix has been implemented and is working as intended.
-   Alcohol Department navigation includes alcohol-specific Sales Mappings and Variance Report entry points.
-   Alcohol Sales Mappings is unlocked for POS SKU/PLU to alcohol inventory product setup.
-   Alcohol Sales Mapping add/edit reuses the existing Production POS Menu Items catalog so managers select imported POS items instead of retyping SKU/name details.
-   Alcohol Variance Report now calculates actual-vs-sold usage from opening/closing alcohol counts, period purchases, imported POS usage, and active Sales Mappings, including bottle/keg portion conversion and red/green variance quantity and dollar columns.
-   Alcohol Variance Report is accepted as complete for the v4.2.0 release target.
-   Alcohol workflow changes should be driven by specific live-data issues when they appear.
-   Freezer Pull is working as intended and is not an active roadmap item.

### Future

-   Food production variance later, after multi-location and infrastructure priorities are settled
-   Recipe Costing later, after multi-location and infrastructure priorities are settled
-   Advanced Reporting

## Labour Management

### Status

-   Labour Management is complete and accepted as working as intended for the v4.2.0 release target.
-   Labour Management is a top-level dashboard module.
-   Labour Setup is administrator-protected and supports configurable positions, employees, hourly wages, tip-pool eligibility, uniform-deduction applicability, active/inactive state, target labour percentages, and default uniform deduction settings.
-   Labour Hours replaced the old Weekly Labour screen name. It uses an inventory-count-style saved-week list and opens a separate spreadsheet-style pop-out editor for Monday-Sunday Shift 1 / Shift 2 manual hour entry.
-   Labour Hours no longer shows employee number, hourly rate, total pay, bottom total rows, or the restaurant header.
-   Labour Hours department headers use a clearer styled band in the pop-out entry window.
-   Labour Setup, Labour Hours, Daily Labour Cost, Tip Pool, and Tip Pool Breakdown support Cloud API mode through `/labour` routes and local DAO fallback for development.
-   Labour API routes are deployed to `esm-operations-api`, including `/labour/weeks`, `/labour/weekly`, and `/labour/daily`, and the live AWS RDS schema has been advanced through schema version 22.
-   Migration 16 added Labour Management setup tables and future daily labour/sales foundations.
-   Migration 17 added daily labour snapshot columns for historical wage/position/group reporting integrity.
-   Daily Labour Cost is a weekly start/end date range screen with Monday-Sunday rows, editable net sales, BOH labour dollars/percentages, FOH labour dollars/percentages, total labour percentage, and a total row.
-   Tip Pool is implemented with daily tip-out pool entry under each day's Tip column, tip-pool eligible hours, calculated employee tip allocations, and weekly totals.
-   Tip Pool Breakdown is implemented as a start/end date range payout report with gross tips, configured per-worked-day uniform deductions for applicable employees, nickel-rounded net payout totals, and compact black-and-white printing.

### Future

-   Preserve current Labour Management workflows unless a future live issue or specific manager request is reported.
