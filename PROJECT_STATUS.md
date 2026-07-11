# PROJECT_STATUS.md

_Last Updated: July 2026_

This file is intended to be read **after** `PROJECT_REFERENCE.md`.

`PROJECT_REFERENCE.md` contains the permanent architecture and design decisions.
`PROJECT_STATUS.md` contains the current development state, recent changes, known issues, and next priorities.

---

# Current Version

**Development Branch:** Production Module

Current database schema version: **9**

Application compiles successfully.

---

# Completed This Session

## Weekly Production Printing

- Reworked the printable prep sheet implementation.
- Removed the previous hard-coded 32-row pagination.
- Prep sheets are intended to print as **one page per prep sheet**.
- Compact printable layout implemented.
- Current `WeeklyProductionView.java` is the new baseline implementation.
- Do **not** restore the previous pagination logic unless specifically requested.

## Production Module

Completed:

- Production Stations
- Production Items
- Production Profiles
- POS Menu Items
- Product Mappings
- Weekly Production
- Freezer Pull
- Permanent Production Item override pars

Working features:

- Usage Report import
- Weekly Production generation
- Refresh Week updates selected Weekly Production weeks from current Production Item settings.
- Override Par editing
- Save Permanent Override from selected Weekly Production lines
- Prep Sheet filtering
- Prep Sheet preview
- Prep Sheet printing
- Print All for the selected Weekly Production prep sheet across Monday through Sunday
- Freezer Pull generation
- Production Items can store a permanent override par that Weekly Production applies during generation.

---

# Known Issues

## Weekly Production Print Layout

The print system is functional.

Future improvements should focus on:

- Better use of page space.
- Keeping fonts readable.
- Matching the restaurant's original Excel prep sheet as closely as practical.

Avoid rewriting the print engine unless there is a clear regression.

---

# Next Development Priorities

## 1. Production Variance

Highest priority.

Use:

- Production Item ↔ Inventory Product mappings
- Weekly Production
- Inventory counts
- Invoice history

Goal:

Compare theoretical usage against actual inventory usage.

---

## 2. Production Reports

Potential additions:

- Weekly production summary
- Station summaries
- Production history
- Export to PDF

---

## 3. Locked Production Weeks

Consider:

- Lock week after approval.
- Prevent accidental edits.
- Allow manager override.

---

# Build Checklist

After significant code changes:

```bash
mvn clean test
```

Verify:

- Application compiles.
- Weekly Production imports.
- Prep Sheet preview.
- Prep Sheet printing.
- Freezer Pull printing.

---

# Notes For Codex

Always read:

1. PROJECT_REFERENCE.md
2. PROJECT_STATUS.md

before making changes.

When editing:

- Prefer minimal targeted changes.
- Preserve existing architecture.
- Do not rewrite large sections unless requested.
- Maintain SQLite compatibility.
- Keep JavaFX styling consistent.

If modifying WeeklyProductionView:

- Preserve one-page prep sheet behavior.
- Preserve Prep Sheet filtering.
- Preserve Override Par functionality.
- Preserve ProductionWeekDao workflow.

When uncertain, extend existing code rather than replacing it.

---

# Session Starting Prompt

Read:

- PROJECT_REFERENCE.md
- PROJECT_STATUS.md

Then inspect only the files required for the requested task.

Run:

```bash
mvn clean test
```

after Java changes before considering the task complete.
