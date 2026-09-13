# Release History

## v4.0.0
Date: 2026-09-11

- Added multi-location Store Login for Cloud API mode.
- Added location credentials, hashed location sessions, `POST /auth/login`, and desktop session-token handling.
- Scoped main store-owned API workflows by location, including Food, Alcohol, Supplies, Production, Reporting/Sales, Purchasing, and Labour.
- Added `location_id` schema support and PostgreSQL location-aware uniqueness through schema version 21.
- Added location administration scripts for creating stores, editing stores, resetting store passwords, applying migrations, and copying selected setup/master data between stores.
- Release builds now enable Store Login by default with `location.login.required=true`.
- Deployed the API with location auth required and verified TEST-store isolation.

## v3.0.6
Date: 2026-08-31

- Added AWS API mode support across Food, Alcohol, Supplies, Production, Reporting/Sales, product import persistence, product purchase history, and administrative upload/download sync workflows.
- Kept CSV/Excel parsing and local SQLite backup/restore on the desktop client; the API receives normalized records and table snapshots for persistence.
- Consolidated API Gateway routing to one Lambda proxy trigger to avoid Lambda resource-policy size limits as routes grow.

## v3.0.0
Date: 2026-08-04

- Added SQLite default mode with optional Aiven PostgreSQL cloud mode.
- Added System menu controls for cloud connection test, mode switching, upload to cloud, and download from cloud.
- Added lower-access cloud app-user support and safe bundled default database configuration for fresh installs.
- Improved cloud performance for Weekly Production, Freezer Pull, and Inventory Valuation.
- Fixed Weekly Cost Report department filtering for Food vs Alcohol reports.
- Added persistent editable Order Guide Case values per count template line.


## v1.9.0
Date: 2026-07-04 11:34

- UI improvements, bug fixes

## v2.0.0
Date: 2026-07-06 12:11

- Added Production database schema (Migration6), Added Production Model Classes, Added initial Production Dao's, Fixed valuation calculations

## v2.0.1
Date: 2026-07-08 17:07

- Production module basic functions

## v2.0.2
Date: 2026-07-11 13:05

- Fixed Production Module

## v2.0.2
Date: 2026-07-12 11:50

- 

## v2.0.3
Date: 2026-07-12 11:51

- Updated installer updates

## v2.0.4
Date: 2026-07-12 11:56

- Updated Installer

## v2.0.5
Date: 2026-07-14 13:40

- Finished Alcohol module, Fixed Order guide layouts

## v2.0.6
Date: 2026-07-15 10:47

- Added updater

## v2.0.7
Date: 2026-07-15 10:53

- bug fixes

## v2.0.8
Date: 2026-07-19 16:47

- Updated invoice importer. Updated in app updates. Bug Fixes

## v2.0.9
Date: 2026-07-19 16:56

- update code

## v2.1.0
Date: 2026-07-22 13:02

- Fixed Counts, Invoice Importer

## v2.1.1
Date: 2026-07-22 13:35

- Fixed delte buttons not confirming

## v2.1.2
Date: 2026-07-28 12:44

- Reworked Alcohol Invoicing. Fixed Alcohol valuation Bug

## v2.1.3
Date: 2026-07-28 13:03

- Fixed display issues in count sheets

## v2.1.4
Date: 2026-07-31 11:37

- Fixed Production Module, Fixed update process

## v2.1.5
Date: 2026-07-31 15:23

- Updated freezer pull module

## v2.1.6
Date: 2026-08-01 13:27

- Bug fixes, Alcohol, Updater

## v2.1.7
Date: 2026-08-01 13:42

- Bug Fix

## v3.0.0
Date: 2026-08-04 12:38

- Added PostgreSQL migration for cloud based solution - Corrected Inventory vaulation reports to only show reporting categories based on the count periods selected. - Added ability for CASE column to be edited in ORDER GUIDE for different users ordering preferences. Added dual mode adminsitrative access to switch between classic (SQLite) and Cloud (PostgreSQL) laying groundwork for future 100 percent cloud based operations

## v3.0.0
Date: 2026-08-04 12:50

- Added support for cloud based database (PostgreSQL) while retaining offline mode (SQLite) this requires adminstrator access to switch modes. Made CASE column editible in order  guide. Other bug fixes and stability improvements

## v3.0.1
Date: 2026-08-09

- Added cloud connection diagnostic/debug support.
- Completed current work-PC cloud database rollout against Aiven PostgreSQL.
- Confirmed all current work PCs connect to the cloud database without error.
- Set Cloud PostgreSQL as the normal operating mode for configured work PCs.
- Documented that the cloud database is now the accurate master data source.
- Documented that Upload This PC to Cloud and Download Cloud to This PC are administrator migration/recovery tools, not routine daily sync actions.
- Updated project documentation snapshot for the v3.0.1 release.

## Unreleased Development After v3.1.1

- Added Labour Management Phase 1 and Phase 2 in the development build.
- Added administrator-protected Labour Setup for configurable positions, employees, hourly wages, tip-pool eligibility, uniform-deduction applicability, active/inactive state, target labour percentages, and default uniform deduction settings.
- Added Weekly Labour Entry with a compact spreadsheet-style Monday-Sunday grid and separate Shift 1 / Shift 2 employee-hour fields.
- Added schema migrations 16 and 17 for Labour Management tables and daily labour snapshot columns.
- Added and deployed Labour API routes for setup and Weekly Labour bulk load/save.
- Added `scripts/Apply-LabourSchemaMigration.ps1` for applying the Labour schema to AWS RDS through a schema-capable admin user.
- Fixed Inventory Count Sheet printing after the v3.1.1 stable release by creating layout after printer selection, scaling pages to the printable area, and using the API-backed alcohol profile client in API mode.

## v3.0.1
Date: 2026-08-09 14:41

- Added net sales after discounts function. Compelted Cloud Database set up

## v3.0.2
Date: 2026-08-09 14:42

- Working tree update on 2026-08-10: improved cloud-mode responsiveness for inventory count save/complete, Sales report import/save, and Weekly Production usage report import/generation by using background tasks and batched database operations.
- Changed POS Sales import to use fixed zero-based columns for sales amounts: gross sales column index 1 and net sales column index 3.

## v3.0.3
Date: 2026-08-10 13:35

- Fixed cloud syncing. Fixed sales importer
- Updated alcohol manual invoice entry to include FOOD-category products for non-alcohol beverages bought from alcohol suppliers, and fixed HST/deposit checkbox label visibility.

## v3.0.4
Date: 2026-08-17 12:34

- Fixed Alcohol Invoicing to Include food items

## v3.0.5 pre-release notes
Date: 2026-08-22

- Added Alcohol Department Sales Mappings and initial Variance Report entry points.
- Added schema version 15 with `alcohol_sales_mappings`.
- Reused the existing Production POS Menu Items catalog in Alcohol Sales Mapping add/edit.
- Migrated the active cloud database target from Aiven PostgreSQL to AWS RDS PostgreSQL after dump/restore and app-load verification.
- Added and verified restricted AWS RDS `operations_app` access for installed-app use.
- Improved PostgreSQL Sales Mappings schema handling for lower-access app users after schema setup.
- Improved menu error dialogs and large searchable ComboBox behavior.

## v3.0.5
Date: 2026-08-30 15:17

- Migrated to AWS database. Faster queries. Updated department names,

## v3.0.6
Date: 2026-08-31 11:04

- Fixed combo box support
- Added initial desktop API mode support with API URL/key configuration, System API health testing, and read-only Product list loading through the deployed API.
- Added deployed `GET /pos-menu-items` and read-only desktop POS Menu Items loading through API mode.
- Added deployed Alcohol Sales Mappings API endpoints for list, add, edit, and deactivate, and wired the desktop Alcohol Sales Mappings screen to use them in API mode.
- Migrated Food Department API mode workflows: Products, Import Invoice, Manual Invoice, Count Templates, Inventory Counts, and Order Guide.
- Migrated Alcohol Department API mode workflows: Products with alcohol profile maintenance, Manual Invoice, Count Templates, Inventory Counts, Order Guide, Product Profiles for weighted counts, and Sales Mappings.
- Added Production API backing in the development build for production setup, POS menu item maintenance/import/KDS cleanup, Weekly Production generation/loading/refresh/override saves, product mappings, and Freezer Pull manual quantities.
- Deployed Production API routes to `esm-operations-api` and read-only smoke-tested production setup, POS, weekly production, product mapping, and Freezer Pull endpoints.
- Deployed and smoke-tested the Food and Alcohol API routes against AWS RDS.
- Confirmed Production CSV/report import and Weekly Production generation work in API mode.
- Added the missing Log4j runtime provider required by Apache POI so production report imports no longer print a missing logging provider warning.
- API migration now covers operational departments, Reporting/Sales, product support, and administrative upload/download sync; variance reporting remains deferred.

## v3.1.1
Date: 2026-09-05 15:31

- Completed API migration, Users no longer need to configure client connections. Database status indicator added. 
- Updated the deployed API health version to 3.1.1.
- Added SAM-managed API Lambda log retention and imported the existing CloudWatch log group into the stack.

## v3.1.2
Date: 2026-09-07 10:46

- Fixed Printing bug, added labour module work in progress

## v3.1.3
Date: 2026-09-08 18:44

- Completed Alcohol Varience Reporting. Completed Labour Management.

## v4.0.0
Date: 2026-09-11 17:08

- Multi Store mode activated. User name and password login requred.

## v4.0.0
Date: 2026-09-13 12:46

- Fixed window resizing, and log in screen

## v4.0.1
Date: 2026-09-13 12:48

- Fixed login, fixed window resize
