# PROJECT STATUS

## Current Version

**Current Project Version:** v2.1.6

Current database schema version: **12**

Application compiles successfully.

All implemented features through v2.1.6 are considered working as intended.

# Completed This Session

## Current Working State

Completed:

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

1.  Add installer verification / checksum handling.
2.  Add a manual Check for Updates action.
3.  Confirm future installer upgrades replace the existing installed app entry and do not create duplicate installs.
4.  Resume Production Variance development.
5.  Continue advanced reporting and workflow polish.
6.  Address alcohol workflow changes only when live-data validation identifies a concrete issue.
