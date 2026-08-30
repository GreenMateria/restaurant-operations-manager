# ROADMAP

## Cloud Database

### Status

-   Cloud database setup is complete for the current work PCs.
-   v3.0.5 development working tree includes the completed controlled hybrid database release path, cloud-mode save/import responsiveness fixes, POS sales import fix, alcohol manual invoice FOOD-item fix, alcohol Sales Mappings, and initial Alcohol Variance entry points.
-   Current v3.0.5 development work has not been packaged, tagged, published, or promoted to stable release yet.
-   SQLite remains the default startup mode for fresh installs and fallback use.
-   Configured work PCs should run in AWS RDS PostgreSQL cloud mode for daily shared-data operation.
-   AWS RDS PostgreSQL cloud mode is available through the password-protected System module and takes effect after restart.
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

-   Validate alcohol Sales Mappings setup from the POS Menu Items catalog before packaging a stable installer.
-   Package and publish the next installer only after the current v3.0.5 development tree is approved for stable.
-   Validate the v3.0.5 installer upgrade on the work PCs after packaging.
-   Continue monitoring cloud-mode daily use for workflow or record-edit conflicts.
-   Consider a backend/API layer later if the app expands beyond controlled internal use.

## Future Web / API Architecture

### Future

-   Consider a browser-based version after the current desktop application is stable, core workflows are proven in daily use, and alcohol variance/reporting work is complete.
-   Treat this as a long-term migration path, not an immediate rewrite.
-   Preferred future architecture:

```text
Browser frontend
-> API/backend
-> PostgreSQL database
```

-   React is a reasonable candidate for the browser frontend, replacing the current JavaFX UI layer.
-   Java can remain the backend language through an API service, preserving more of the current project knowledge and business logic than a full JavaScript rewrite.
-   A backend/API layer would keep database credentials off client machines, centralize business rules, support stronger authentication, and make auditing/permissions easier.
-   Plan any migration in phases:
    -   Keep the current JavaFX desktop app stable for internal operations.
    -   Strengthen service boundaries so business logic is not trapped in JavaFX screens.
    -   Introduce a Java API/backend when there is a clear security, scaling, or browser-access need.
    -   Build browser screens module by module, starting with lower-risk reporting/admin views before replacing core workflows.
    -   Retire the desktop app only after the browser version fully covers daily manager workflows.
-   Re-evaluate this direction after Cloud PostgreSQL daily use, alcohol variance, reporting, order guide, and production workflows are stable enough that requirements are no longer changing quickly.

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

-   Implemented features through v3.0.5 are working as intended.
-   Alcohol manual invoice entry includes `FOOD` reporting-category products for non-alcohol beverages purchased from alcohol suppliers; this fix has been implemented and is working as intended.
-   Alcohol Department navigation includes alcohol-specific Sales Mappings and Variance Report entry points.
-   Alcohol Sales Mappings is unlocked for POS SKU/PLU to alcohol inventory product setup.
-   Alcohol Sales Mapping add/edit reuses the existing Production POS Menu Items catalog so managers select imported POS items instead of retyping SKU/name details.
-   Alcohol Variance Report currently has the navigation and setup shell; calculation/report output remains the next build step.
-   Alcohol workflow changes should be driven by specific live-data issues when they appear.
-   Freezer Pull is working as intended and is not an active roadmap item.

### Future

-   Populate alcohol sales mappings from POS Menu Items and live bar item review
-   Alcohol variance reporting calculation and output
-   Food production variance later, after alcohol variance is working
-   Recipe Costing
-   Advanced Reporting
