# CODING_STANDARDS.md

_Last Updated: July 28, 2026_

This file documents coding conventions and project-specific implementation rules for the Food Inventory / ESM Operations Manager application.

Read this before making broad code changes.

---

# General Development Rules

- Preserve the current JavaFX + SQLite + Maven architecture.
- Prefer targeted changes over large rewrites.
- Do not rename packages, models, DAOs, or major screens unless explicitly requested.
- Do not delete user data or reset the database unless explicitly asked.
- After Java changes, run:

```bash
mvn clean test
```

---

# Package Structure

Main package:

```text
ca.foodinventory
```

Common package layout:

```text
ca.foodinventory.ui
ca.foodinventory.dao
ca.foodinventory.model
ca.foodinventory.service
ca.foodinventory.database
```

Use the existing structure:

- UI screens -> `ui`
- Database access -> `dao`
- Plain data objects -> `model`
- Business/report/import logic -> `service`
- Schema/migrations -> `database`

---

# JavaFX UI Patterns

Most screens use:

- `BorderPane`
- `VBox`
- `HBox`
- `GridPane`
- `TableView`
- `Dialog`
- `FileChooser`

Common conventions:

- Add root style class where appropriate:

```java
getStyleClass().add("root-dark");
```

- Page titles use:

```java
title.getStyleClass().add("page-title");
```

- Primary action buttons use:

```java
button.getStyleClass().add("primary-button");
```

- Prefer hand-built JavaFX layouts over FXML unless the project is explicitly migrated.

---

# TableView Rules

Use JavaBean-style getters in model classes so `PropertyValueFactory` works correctly.

Example:

```java
TableColumn<Product, String> descriptionCol = new TableColumn<>("Description");
descriptionCol.setCellValueFactory(new PropertyValueFactory<>("description"));
```

If adding a new table column:

1. Confirm the model has a matching getter.
2. Confirm the DAO populates the field.
3. Confirm null values render safely.

---

# Dialog Rules

For add/edit workflows:

- Use a dedicated dialog class when the form is reusable or complex.
- Use inline dialogs for very small one-off prompts.
- Validate required fields before saving.
- Show clear warning alerts for missing inputs.
- Show error alerts for failed save/import operations.

Alert pattern:

```java
showAlert(Alert.AlertType.WARNING, "Title", "Message");
```

---

# DAO Rules

DAO classes should:

- Own SQL for one model area.
- Use prepared statements.
- Avoid mixing UI logic into DAO classes.
- Throw runtime exceptions with useful messages when database operations fail.
- Keep connection management consistent with existing code.

Do not make DAO methods depend on JavaFX classes.

---

# Service Rules

Service classes should contain business logic, import parsing, report generation, or calculations.

Examples:

- CSV/XLSX import logic
- Inventory valuation
- Weekly cost reporting
- Production report generation
- Backup logic

Avoid putting calculations directly into JavaFX view classes if they may be reused.

---

# Migration Rules

When database structure changes:

1. Create a new migration class.
2. Increment the schema version in `DatabaseMigrationRunner`.
3. Keep migrations backward-compatible.
4. Avoid destructive schema changes.
5. Use repair logic if older databases may have partially upgraded schemas.
6. Verify app startup against an existing runtime database.

Never assume the project-root database is the active database.

Runtime DB path is under:

```text
%LOCALAPPDATA%/FoodInventory/food_inventory.db
```

---

# Import Rules

## GFS Invoice CSV

Preserve current mapping unless explicitly changed.

Known mapping:

- Column B = SKU
- Column C = Case Qty
- Column D = Split Qty
- Column F = Pack Size
- Column H = Description
- Column K = Case Cost
- Column L = Each Cost
- Header row contains invoice/date/total data.

Important:

- Support duplicate SKUs/lines on the same invoice.
- Preserve duplicate invoice overwrite behavior.
- When a manual invoice line uses split / each cost without a case cost, preserve valuation fallback by deriving the product's last known purchased-unit cost from each cost and `conversion_factor`.
- Never overwrite a valid `last_case_cost` with zero just because the purchase was entered on the split / each side.
- For alcohol-specific manual invoices, treat line-level inclusion flags as merchandise-cost calculation helpers only.
- Save exact paper HST and exact paper bottle deposit totals as the accounting adjustment values.
- If a final invoice balancing difference remains, reconcile it into merchandise categories rather than modifying the saved HST amount.

---

## POS Sales / Usage XLSX

Use Apache POI.

For Production Usage Report:

- Column B = SKU/PLU
- Column D = Monday qty
- Column F = Tuesday qty
- Column H = Wednesday qty
- Column J = Thursday qty
- Column L = Friday qty
- Column N = Saturday qty
- Column P = Sunday qty
- Column R = Weekly qty

Skip KDS-only rows between markers:

```text
kds dnu.ESM
Gifts and Selling Suppli.ESM
```

---

# Weekly Production Rules

`WeeklyProductionView.java` is currently the baseline for Weekly Production.

Preserve:

- Week selection.
- Usage Report import.
- Day tabs Monday through Sunday.
- `Include all active production items`.
- Override Par editing.
- Save Overrides.
- Prep Sheet selector.
- Prep Sheet filtering.
- Preview Prep Sheet.
- Print Prep Sheet.

## Printing Rules

Prep sheets should print as:

- One prep sheet per page.
- Compact layout.
- Station headers included.
- Columns:
  - `ITEM`
  - `UNIT`
  - `LIFE`
  - day name
  - `COUNT`
  - `TO DO`
  - `INITIAL`

Important:

- Do not reintroduce the old 32-row pagination rule.
- Do not split Main Line prep into multiple pages unless explicitly requested.
- Future print tuning should first adjust:
  - row height
  - font size
  - padding
  - column width
  - page orientation
- Avoid complicated JavaFX transform chains unless required.

---

# Freezer Pull Rules

Freezer Pull:

- Uses the same Usage Report import.
- Filters to Production Items assigned to `Freezer Pull`.
- Prints only freezer-pull lines.
- Unit comes from the Production Profile line where applicable.

Do not merge freezer pull into Weekly Production printouts unless explicitly requested.

---

# Production Module Rules

Production setup workflow:

1. Production Stations
2. Production Items
3. Production Profiles
4. POS Menu Items
5. Product Mappings

Production Profiles define the production quantities for sold POS items.

POS Menu Items connect POS SKUs/PLUs to Production Profiles.

Product Mappings connect Production Items to Inventory Products for future variance reporting.

---

# Naming Style

Use descriptive class and method names.

Examples:

```java
ProductionUsageReportImportService
ProductionReportService
ProductionWeekDao
WeeklyProductionView
```

Prefer clarity over cleverness.

---

# Error Handling

Use clear user-facing alerts for:

- Missing required input.
- Failed import.
- Failed save.
- Failed print.
- Empty selection.

Use exception messages that help diagnose file/import/database issues.

---

# Build / Verification Checklist

After changes:

```bash
mvn clean test
```

Manual verification when relevant:

- App launches.
- Screen opens.
- Data loads.
- Save works.
- Import works.
- Print/preview works.
- No runtime database migration errors.

---

# Codex Guidance

When using Codex in IntelliJ:

1. Ask Codex to read:
   - `PROJECT_REFERENCE.md`
   - `PROJECT_STATUS.md`
   - `DATABASE_SCHEMA.md`
   - `CODING_STANDARDS.md`
2. Ask it to inspect the exact files involved.
3. Ask it for a small plan before editing.
4. Ask it to run `mvn clean test` after edits.
5. Avoid broad rewrites unless specifically needed.

Good prompt:

```text
Read PROJECT_REFERENCE.md, PROJECT_STATUS.md, DATABASE_SCHEMA.md, and CODING_STANDARDS.md.
Then inspect the files needed for this task only.
Make a minimal targeted change and run mvn clean test.
```
