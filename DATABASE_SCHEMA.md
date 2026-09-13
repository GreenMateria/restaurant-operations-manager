# DATABASE_SCHEMA.md

_Last Updated: Friday, September 11, 2026_

This file documents the current database structure for the Food Inventory / StoreOps Manager application.

Read this after `PROJECT_REFERENCE.md` when working on database, DAO, reporting, inventory, or production features.

---

# General Rules

- Normal shared data path for configured work PCs: Cloud API mode backed by PostgreSQL on AWS RDS.
- SQLite remains available as a local cloud-snapshot/backup target, emergency local restore file, and development fallback.
- Direct desktop PostgreSQL mode is retired from normal client use. PostgreSQL credentials should stay off client PCs.
- Runtime database path is controlled by `DatabaseManager`.
- Runtime database is under:

```text
%LOCALAPPDATA%/FoodInventory/food_inventory.db
```

- A root-level `food_inventory.db` may exist in the project directory, but it is not necessarily the active runtime database.
- Database setup and migrations live in:

```text
src/main/java/ca/foodinventory/database
```

- Current migration version: **21**.
- Do not manually edit user databases unless explicitly asked.
- Prefer adding schema changes through a new migration.

---

# Migration Files

Current known migration files:

- `Migration2`
- `Migration3`
- `Migration4`
- `Migration5`
- `Migration6`
- `Migration7`
- `Migration8`
- `Migration9`
- `Migration10`
- `Migration11`
- `Migration12`
- `Migration13`
- `Migration14`
- `Migration15`
- `Migration16`
- `Migration17`
- `Migration18`
- `Migration19`
- `Migration20`
- `Migration21`

PostgreSQL support:

- `PostgresSchemaInitializer`
  - Creates or repairs a PostgreSQL-compatible version of the current schema when the connected user has schema privileges.
  - Validates required schema for lower-access application users.
  - Lower-access application users may use existing tables after a schema-capable user applies new PostgreSQL schema changes.
- `SQLiteToPostgresUploadTool`
  - Copies data from the runtime SQLite database into PostgreSQL through `DatabaseSyncService`.
  - Requires explicit upload confirmation before replacing PostgreSQL table data.
  - Initial upload to Aiven `defaultdb` copied 6,576 rows on Sunday, August 2, 2026.
  - Production cloud data was later copied from Aiven PostgreSQL to AWS RDS PostgreSQL on Sunday, August 30, 2026.
- `DatabaseSyncService`
  - Uploads local SQLite data to cloud PostgreSQL.
  - Downloads cloud PostgreSQL data to local SQLite, backing up the local database first.
  - These actions are administrator migration/recovery tools. Local-to-cloud upload has been removed from normal desktop use; normal daily cloud use happens through Cloud API mode and backup/cloud snapshot download flows.
- `scripts/Apply-LabourSchemaMigration.ps1`
  - Applies the Labour Management schema foundation and Migration 17 snapshot columns to AWS RDS through a schema-capable admin user.
  - Prompts for the RDS admin password securely and does not store it.
  - Refreshes `operations_app` grants for Labour tables/sequences after schema creation.
- `scripts/Apply-LocationAuthSchemaMigration.ps1`
  - Applies the location-auth schema foundation to AWS RDS through a schema-capable admin user.
  - Prompts for the RDS admin password and initial location password securely.
  - Stores only salted PBKDF2 password hash data in `locations`.
- `scripts/Apply-StoreLocationSchemaMigration.ps1`
  - Applies store-owned `location_id` columns for the multi-location data model.
  - Prompts for the RDS admin password securely and does not store it.
- `scripts/Apply-LocationUniqueConstraintsMigration.ps1`
  - Applies the Migration 21 PostgreSQL uniqueness changes to AWS RDS through a schema-capable admin user.
  - Prompts for the RDS admin password securely and does not store it.
- `scripts/Copy-LocationSetup.ps1`
  - Copies selected setup/master data from one location to another after both locations exist.
  - Supports products, aliases, inventory templates, alcohol setup, production setup, POS menu items, and production product mappings.
  - Does not copy invoices, counts, sales periods, labour history, generated production weeks, sessions, or passwords.

Migration responsibilities:

- `Migration6`
  - Added production module tables.
- `Migration7`
  - Added `production_items.shelf_life`.
- `Migration8`
  - Added `production_stations.prep_sheet`.
- `Migration9`
  - Added `production_items.permanent_override_par`.
- `Migration10`
  - Added invoice subtotal/allocation fields to `invoices`:
    `imported_total`, `merchandise_subtotal`, `freight`, and `hst`.
- `Migration11`
  - Added `invoice_adjustments`.
  - Preserved any previously saved Freight/HST values as adjustment rows.
- `Migration12`
  - Added `production_items.yield_factor`.
  - Existing production items default to a yield factor of `1.0`.
- `Migration13`
  - Added `inventory_count_template_lines.order_guide_case_size`.
  - Order Guide case-size edits now persist per count template line.
- `Migration14`
  - Added net-sales fields to `sales_periods`:
    `food_net_sales`, `beer_net_sales`, `wine_net_sales`, `draught_net_sales`,
    `import_draught_net_sales`, and `liquor_net_sales`.
  - Existing net-sales values are seeded from the matching gross sales fields when missing.
- `Migration15`
  - Added `alcohol_sales_mappings` for alcohol-only POS item to inventory product variance mappings.
- `Migration16`
  - Added Labour Management Phase 1 tables:
    `labour_positions`, `labour_employees`, `labour_daily_sales`, and `labour_daily_entries`.
  - Added setting key `labour.default_uniform_deduction`.
  - Added indexes for labour position/group lookup, employee/position lookup, and daily labour entry date lookup.
- `Migration17`
  - Added `labour_daily_entries` snapshot columns:
    `employee_name_snapshot`, `position_name_snapshot`, and `labour_group_snapshot`.
  - These snapshots preserve historical Labour Hours context when employee wages or positions later change.
- `Migration18`
  - Added `locations` for one username/password credential per store/location.
  - Added `location_sessions` for hashed API session tokens.
  - Seeded the current store as location `1` with code `STORE` and username `store`.
- `Migration19`
  - Added `location_id` to Labour Management tables:
    `labour_positions`, `labour_employees`, `labour_daily_sales`, and `labour_daily_entries`.
  - Existing Labour records default to location `1`.
  - Labour API reads and writes are scoped to the resolved location session.
- `Migration20`
  - Added `location_id` to store-owned operational tables, including products, aliases, invoices, inventory templates/counts, sales periods, alcohol setup, and production setup/weeks.
  - Existing records default to location `1`.
  - Main store-owned API routes now use the resolved location session to filter and write these tables by `location_id`.
- `Migration21`
  - PostgreSQL multi-location uniqueness migration.
  - Replaces global uniqueness for product SKUs, product alias SKUs, sales periods, alcohol sales mapping POS SKUs, production station/item/profile names, POS menu SKUs, and production weeks with location-aware composite uniqueness.
  - SQLite fallback keeps its original single-location uniqueness model.

---

# Core Inventory Tables

## products

Stores inventory products.

Important fields:

- `id`
- `sku`
- `description`
- `category`
- `reporting_category`
- `unit`
- `conversion_factor`
- `pack_size`
- `pack_count`
- `last_case_cost`
- `last_purchased_date`
- `active`

Notes:

- `sku` is unique per location in PostgreSQL API mode.
- `reporting_category` drives department/reporting grouping.
- `last_case_cost` is used as a fallback for valuation when no period purchase cost exists.

---

## product_sku_aliases

Maps supplier-specific SKUs to products.

Important fields:

- `id`
- `product_id`
- `supplier`
- `sku`
- `description`
- `pack_size`
- `active`

Used by invoice imports to connect supplier CSV lines to existing products.

---

## invoices

Stores invoice headers.

Important fields:

- `id`
- `invoice_number`
- `supplier`
- `invoice_date`
- `imported_total`
- `merchandise_subtotal`
- `freight`
- `hst`
- `invoice_total`

Notes:

- Invoice duplicate handling relies on `invoice_number`.
- `merchandise_subtotal` is the inventory-cost portion of the invoice.
- `freight`, `hst`, and other non-inventory charges are excluded from valuation logic.

---

## invoice_lines

Stores invoice line items.

Important fields:

- `id`
- `invoice_id`
- `product_id`
- `quantity`
- `base_quantity`
- `pack_size`
- `case_cost`
- `extended_cost`

Notes:

- `base_quantity` is used for usage and valuation calculations.
- GFS import calculates base quantity from cases and splits.
- `case_cost` remains the stored purchased-unit cost field.
- When manual invoices use split / each pricing without a case cost, product fallback valuation cost is derived from the each cost and the product conversion factor rather than overwriting `last_case_cost` with zero.

---

## invoice_adjustments

Stores non-inventory invoice charges and balancing rows.

Important fields:

- `id`
- `invoice_id`
- `description`
- `amount`
- `display_order`

Notes:

- Used for HST, Freight, Bottle Deposit, Keg Deposit, and similar non-inventory charges.
- These rows are saved for invoice balancing and display, but are not part of inventory valuation.
- For alcohol invoices, exact paper HST and exact paper bottle deposit should be saved here as the accounting values.
- Any remaining reconciliation difference should be absorbed into merchandise line allocation rather than changing the saved HST amount.

---

# Inventory Count Tables

## inventory_count_templates

Stores count sheet templates.

Important fields:

- `id`
- `name`
- `department`
- `active`

---

## inventory_count_template_lines

Stores products and layout settings for count templates.

Important fields:

- `id`
- `template_id`
- `product_id`
- `section_name`
- `sort_order`
- `count_unit`
- `conversion_factor_to_base`
- `display_name`
- `order_guide_case_size`
- `active`

Notes:

- `section_name` and `sort_order` control printed count sheet/order guide layout.
- `conversion_factor_to_base` converts counted units to product base units.
- `order_guide_case_size` overrides the product pack size on generated order guides without changing the product record.

---

## inventory_counts

Stores completed or in-progress inventory counts.

Important fields:

- `id`
- `template_id`
- `count_date`
- `period_start_date`
- `period_end_date`
- `notes`
- `completed`

---

## inventory_count_lines

Stores individual counted quantities.

Important fields:

- `id`
- `count_id`
- `product_id`
- `quantity`
- `count_unit`
- `converted_quantity`

Notes:

- `converted_quantity` is the calculated quantity in product base units.

---

# Sales Tables

## sales_periods

Stores sales totals by period.

Important fields:

- `id`
- `period_start_date`
- `period_end_date`
- `food_sales`
- `beer_sales`
- `wine_sales`
- `draught_sales`
- `import_draught_sales`
- `liquor_sales`

Notes:

- Imported from POS sales `.xlsx`.
- Weekly/category cost reporting uses these values.

---

# Alcohol Variance Tables

## alcohol_sales_mappings

Maps POS alcohol items from the item-level POS usage report to alcohol inventory products.

Important fields:

- `id`
- `pos_sku`
- `pos_item_name`
- `reporting_category`
- `product_id`
- `quantity_per_sale`
- `unit`
- `active`

Notes:

- This table is intentionally separate from production item mappings.
- It supports alcohol variance reporting without adding bar workflows to the Production menu.
- `pos_sku` should match the SKU/PLU from the POS usage report used by Weekly Production.
- The Alcohol Sales Mapping dialog reuses active records from `pos_menu_items` to fill `pos_sku` and `pos_item_name`, but `alcohol_sales_mappings` remains the authoritative alcohol variance mapping table.
- `quantity_per_sale` and `unit` describe how much inventory product is consumed by one sale, such as `20 OZ` for a draught pint or `1 EACH` for a bottle.

---

# Location Authentication Tables

Migrations 18 through 21 provide the multi-location Store Login, row ownership, and location-aware uniqueness foundation used by v4.0.0 and later releases.

## locations

Stores one login credential per location.

Important fields:

- `id`
- `code`
- `name`
- `username`
- `password_hash`
- `password_salt`
- `password_iterations`
- `active`
- `created_at`

Notes:

- Passwords are stored as salted PBKDF2 hashes, not plain text.
- The initial current-store row is location `1`, code `STORE`, username `store`.
- This is location identity, not employee/user identity.

## location_sessions

Stores API login sessions for location credentials.

Important fields:

- `id`
- `location_id`
- `token_hash`
- `created_at`
- `expires_at`
- `revoked`

Notes:

- The API returns the raw session token to the desktop after successful login.
- Only a SHA-256 hash of the session token is stored in the database.
- Labour and the main store-owned operational API routes filter reads and writes by `location_id` from the resolved store session.

---

# Settings Tables

## settings

Stores shared application settings.

Known setting use cases:

- Labour default uniform deduction amount: `labour.default_uniform_deduction`.

Admin password note:

- The System administrator password is now a local machine setting in `%LOCALAPPDATA%\FoodInventory\database.properties`, stored as a salted PBKDF2 hash.
- Legacy `admin_password` / `password_initialized` database settings may exist for old databases, but new desktop code should not use the shared database as the administrator password authority.

---

# Labour Tables

Labour tables were introduced by Migration 16.

## labour_positions

Stores configurable labour positions/departments for Labour Management.

Important fields:

- `id`
- `name`
- `labour_group`
- `sort_order`
- `target_labour_percentage`
- `active`

Notes:

- `labour_group` supports the reporting concepts `FOH` and `BOH`.
- Do not hard-code calculations around position names such as Server, Bartender, Cook, or Dish.
- Positions are deactivated with `active = 0` rather than deleted.

## labour_employees

Stores Labour Setup employee configuration.

Important fields:

- `id`
- `name`
- `position_id`
- `hourly_wage`
- `tip_pool_eligible`
- `uniform_deduction_applicable`
- `active`

Notes:

- Employee names and wages are manually configured by managers/admins; they are not imported or hard-coded.
- Employees are deactivated with `active = 0` rather than deleted.
- Future finalized labour/tip records should use stored daily entry snapshots for historical accuracy when wages or positions later change.

## labour_daily_sales

Stores future daily operational sales values for Labour Management.

Important fields:

- `id`
- `sales_date`
- `net_sales`
- `tip_out_pool`
- `finalized`

Notes:

- These records are separate from official weekly imported `sales_periods`.
- Daily Labour Cost uses `net_sales` for weekly cost percentages.
- Tip Pool uses `tip_out_pool` as the daily amount to allocate across tip-pool eligible employee hours.

## labour_daily_entries

Stores future daily employee labour-entry foundations by actual calendar date.

Important fields:

- `id`
- `work_date`
- `employee_id`
- `position_id`
- `hourly_wage`
- `shift_1_hours`
- `shift_2_hours`
- `employee_name_snapshot`
- `position_name_snapshot`
- `labour_group_snapshot`
- `finalized`

Notes:

- `shift_1_hours` and `shift_2_hours` are stored separately for the spreadsheet-style Labour Hours workflow.
- `hourly_wage`, `position_id`, `employee_name_snapshot`, `position_name_snapshot`, and `labour_group_snapshot` are stored on each entry so historical labour reports do not silently change after employee setup changes.
- Daily hours are calculated as `shift_1_hours + shift_2_hours`.
- Daily labour dollars are calculated as daily hours multiplied by the stored `hourly_wage` snapshot.
- A unique rule on `work_date, employee_id` prevents duplicate current entries for the same employee/date.

## Labour API Routes

Cloud API mode uses the existing authenticated desktop API pattern.

Setup routes:

- `GET /labour/positions`
- `GET /labour/positions/active`
- `POST /labour/positions`
- `PUT /labour/positions/{id}`
- `POST /labour/positions/{id}/deactivate`
- `GET /labour/employees`
- `POST /labour/employees`
- `PUT /labour/employees/{id}`
- `POST /labour/employees/{id}/deactivate`
- `GET /labour/settings`
- `PUT /labour/settings`

Labour Hours routes:

- `GET /labour/weeks`
- `GET /labour/weekly/{weekStartDate}`
- `PUT /labour/weekly`

Daily Labour Cost / Tip Pool routes:

- `GET /labour/daily/{workDate}`
- `PUT /labour/daily`

Notes:

- `weekStartDate` is the Monday canonical week start in `YYYY-MM-DD` format.
- `GET /labour/weeks` returns distinct saved Labour Hours week starts for the list-style Labour Hours screen.
- `PUT /labour/weekly` saves a full week payload in bulk rather than issuing one request per edited cell.
- Labour Hours data is stored by actual calendar date, not by relative "this week" columns.
- Daily Labour Cost and Tip Pool use `workDate` / `salesDate` in `YYYY-MM-DD` format and save daily sales plus employee daily entries in one request.
- `tip_pool_eligible` and `uniform_deduction_applicable` are returned in labour row payloads so Tip Pool and Tip Pool Breakdown can calculate from the same API-loaded labour data.
- The live AWS RDS Labour schema was applied on Sunday, September 6, 2026. Labour API route updates for Daily Labour Cost, Tip Pool, Tip Pool Breakdown, and Labour Hours saved-week listing were deployed on Monday, September 7, 2026.

---

# Production Tables

Production tables were introduced by Migration 6.

## production_stations

Stores kitchen/prep stations.

Important fields:

- `id`
- `name`
- `active`
- `prep_sheet`

Notes:

- `prep_sheet` controls which printable prep sheet a station belongs to.
- Default assignments:
  - `Pizza` and `Salad` -> `Pizza Salad`
  - Other stations -> `Main Line`

---

## production_items

Stores prep/production items.

Important fields:

- `id`
- `name`
- `unit`
- `station_id`
- `active`
- `shelf_life`
- `yield_factor`
- `permanent_override_par`

Notes:

- `shelf_life` is printed in the LIFE column on prep sheets.
- `yield_factor` adjusts generated quantities for prep yield. A value of `1.0` preserves existing behavior.
- `permanent_override_par`, when set, becomes the default override/final par for generated Weekly Production lines.
- Freezer Pull items use pull-style units.

---

## production_profiles

Stores production profile headers.

Important fields:

- `id`
- `name`
- `active`

Notes:

- A production profile is the recipe/build list for a sold POS item.

---

## production_profile_lines

Stores production item quantities per production profile.

Important fields:

- `id`
- `profile_id`
- `production_item_id`
- `quantity_per_sale`
- `unit`
- `active`

Notes:

- These lines determine how POS item sales convert into production quantities.
- Weekly Production uses `sales quantity * quantity_per_sale / production item yield factor` before applying the par multiplier.
- Freezer Pull line units can differ from normal prep units.

---

## pos_menu_items

Stores POS menu items and their assigned production profile.

Important fields:

- `id`
- `pos_sku`
- `name`
- `production_profile_id`
- `active`

Notes:

- `pos_sku` maps to the PLU/SKU in POS Usage Report files.
- Existing POS items should preserve profile assignment during import/update.

---

## production_item_product_mappings

Maps production items to inventory products.

Important fields:

- `id`
- `production_item_id`
- `product_id`
- conversion/quantity fields if present in current implementation
- `active`

Purpose:

- Future production variance reporting.
- Connect theoretical production usage to inventory product usage.

---

## production_weeks

Stores generated weekly production headers.

Important fields:

- `id`
- `week_start_date`
- `week_end_date`
- `par_multiplier`
- created/updated fields if present

Notes:

- Re-importing the same week replaces the existing generated week.

---

## production_week_days

Stores each day inside a generated production week.

Important fields:

- `id`
- `production_week_id`
- `day_name`
- `prep_date`

Notes:

- Weekly Production displays each day as a separate tab.

---

## production_week_lines

Stores generated and overridden production lines for each day.

Important fields:

- `id`
- `production_week_day_id`
- `production_item_id`
- `production_item_name`
- `station_name`
- `prep_sheet`
- `unit`
- `shelf_life`
- `previous_sales_quantity`
- `generated_par`
- `override_par`
- `final_par`

Notes:

- `previous_sales_quantity` stores the generated quantity from prior POS usage.
- `generated_par` is calculated from prior sales and multiplier.
- `override_par` is user-editable.
- `final_par` is override par when present, otherwise generated par.
- Weekly Production print uses `final_par`.

---

# Reporting Rules

## Inventory Cost

Formula:

```text
Usage = Opening Inventory + Purchases - Closing Inventory
Cost % = Usage / Sales
```

Rules:

- Food cost uses food sales.
- Alcohol categories use their own alcohol sales category.
- Supplies categories are costed against total revenue.
- Multi-week reports can use opening and closing counts with sales between dates.

---

# Department / Reporting Categories

Known reporting categories:

- `FOOD`
- `PAPER`
- `TAKE OUT`
- `CLEANING`
- `DISHWASHING`
- `GUEST SUPPLIES`
- `WINE`
- `BEER`
- `DRAUGHT`
- `IMPORT DRAUGHT`
- `LIQUOR`
- `OTHER`

Department filters:

Food:

```text
FOOD
```

Alcohol:

```text
WINE, BEER, DRAUGHT, IMPORT DRAUGHT, LIQUOR
```

Supplies:

```text
PAPER, TAKE OUT, CLEANING, DISHWASHING, GUEST SUPPLIES, OTHER
```

---

# Schema Change Rules

When adding schema:

1. Add a new `MigrationX` class.
2. Increment the current schema version in `DatabaseMigrationRunner`.
3. Make the migration safe to run once.
4. Use `CREATE TABLE IF NOT EXISTS` where practical.
5. Use repair logic only when needed.
6. Run:

```bash
mvn clean test
```

7. Manually launch the app and verify startup migration succeeds.

Do not silently reset or delete the runtime database.
