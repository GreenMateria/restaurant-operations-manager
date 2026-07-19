# PROJECT STATUS

## Current Version

**Development Branch:** v2.0.6-dev

Current database schema version: **9**

Application compiles successfully.

# Completed This Session

## Application Update System

Completed:

-   Automatic GitHub release checking at application startup
-   Version display in application UI
-   Version display in application window title
-   Background update checks (non-blocking)
-   Automatic comparison against latest GitHub Release
-   Download prompt when a newer version is available
-   Silent failure when offline or GitHub is unavailable

Current behaviour:

-   Installed versions automatically check GitHub Releases on startup.
-   If a newer version exists, the user is prompted to download it.
-   Downloads currently open the GitHub release/installer in the default
    web browser.

# Future Enhancement

-   Replace browser download with an in-app downloader.
-   Display download progress.
-   Verify installer integrity before launch.
-   Launch installer directly from the application after download.

# Next Development Priorities

1.  Alcohol Inventory Valuation
2.  Alcohol Cost Report
3.  Alcohol Order Guide
4.  In-app updater
5.  Resume Production Variance development
