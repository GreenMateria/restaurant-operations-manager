# PROJECT STATUS

## Current Version

**Current Project Version:** v2.1.5

Current database schema version: **12**

Application compiles successfully.

All implemented features through v2.1.5 are considered working as intended.

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

# Future Enhancement

-   Verify installer integrity before launch.
-   Add a manual Check for Updates action.
-   Continue alcohol workflow polish only as new live-data issues are identified.

# Next Development Priorities

1.  Add installer verification / checksum handling.
2.  Add a manual Check for Updates action.
3.  Resume Production Variance development.
4.  Continue advanced reporting and workflow polish.
5.  Address alcohol workflow changes only when live-data validation identifies a concrete issue.
