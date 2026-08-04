# Release History

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
