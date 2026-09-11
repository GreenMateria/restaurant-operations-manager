# ESM Operations Manager API Proof

This is the first local API proof for moving PostgreSQL credentials off desktop client PCs.

Current deployed proof:

```text
https://rn0j30p2vf.execute-api.ca-central-1.amazonaws.com/prod
```

Initially verified on Tuesday, September 1, 2026. For v4.0.0, the deployed API requires a valid store location session token on the main location-scoped business routes.

```text
GET /health -> 200 OK
GET /products without x-api-key -> 401 Unauthorized
GET /products with x-api-key but without x-location-token -> 401 location_unauthorized
GET /products with x-api-key and TEST store x-location-token -> [] during isolation smoke test
GET /pos-menu-items without x-api-key -> 401 Unauthorized
GET /pos-menu-items with x-api-key -> 642 POS menu items from AWS RDS
GET /alcohol-sales-mappings without x-api-key -> 401 Unauthorized
GET /alcohol-sales-mappings with x-api-key -> current alcohol mappings from AWS RDS
POST /alcohol-sales-mappings without x-api-key -> 401 Unauthorized
GET /departments/FOOD/inventory-count-templates with x-api-key -> 1 template
GET /departments/FOOD/inventory-counts with x-api-key -> 9 counts
GET /departments/FOOD/completed-inventory-counts with x-api-key -> 9 completed counts
GET /departments/ALCOHOL/inventory-count-templates with x-api-key -> 1 template
GET /departments/ALCOHOL/inventory-counts with x-api-key -> 8 counts
GET /departments/ALCOHOL/completed-inventory-counts with x-api-key -> 7 completed counts
GET /alcohol-product-profiles with x-api-key -> 85 active profiles
GET /order-guide/{openingCountId}/{closingCountId} with x-api-key -> 189 Food rows / 85 Alcohol rows in smoke checks
GET /production/stations without x-api-key -> 401 Unauthorized
GET /production/stations with x-api-key -> 20 stations
GET /production/items/active with x-api-key -> 77 active production items
GET /production/profiles with x-api-key -> 110 production profiles
GET /production/profiles/{id}/lines with x-api-key -> profile lines returned
GET /production/pos-menu-items/active with x-api-key -> 641 active POS menu items
GET /production/product-mappings with x-api-key -> 10 product mappings
GET /production/weeks with x-api-key -> 9 production weeks
GET /production/weeks/{id}/days with x-api-key -> 7 production week days
GET /production/weeks/{id}/lines with x-api-key -> production week lines returned
GET /production/freezer-pull/lines with x-api-key -> 12 Freezer Pull lines
GET /labour/weekly/{weekStartDate} with x-api-key -> weekly labour rows returned after Labour schema deployment
```

## Endpoints

```text
GET /health
POST /auth/login
GET /products
POST /products
PUT /products/{id}
POST /products/{id}/deactivate
GET /pos-menu-items
POST /pos-menu-items
POST /invoices/exists
POST /invoices/delete
POST /food-invoices
POST /alcohol-invoices
POST /supplies-invoices
GET /departments/{department}/inventory-count-templates
POST /departments/{department}/inventory-count-templates
POST /inventory-count-templates/{id}/deactivate
POST /inventory-count-templates/{id}/duplicate
GET /inventory-count-templates/{id}/lines
POST /inventory-count-templates/{id}/lines
PUT /inventory-count-templates/{id}/line-sort-orders
PUT /inventory-count-template-lines/{id}
POST /inventory-count-template-lines/{id}/deactivate
PUT /inventory-count-template-lines/{id}/order-guide-case-size
GET /departments/{department}/inventory-counts
POST /departments/{department}/inventory-counts
GET /departments/{department}/completed-inventory-counts
GET /inventory-counts/{id}/lines
PUT /inventory-counts/{id}/lines
POST /inventory-counts/{id}/complete
DELETE /inventory-counts/{id}
GET /order-guide/{openingCountId}/{closingCountId}
GET /alcohol-product-profiles
GET /alcohol-sales-mappings
POST /alcohol-sales-mappings
PUT /alcohol-sales-mappings/{id}
POST /alcohol-sales-mappings/{id}/deactivate
GET /production/stations
GET /production/stations/active
POST /production/stations
PUT /production/stations/{id}
POST /production/stations/{id}/deactivate
GET /production/items
GET /production/items/active
POST /production/items
PUT /production/items/{id}
POST /production/items/{id}/deactivate
PUT /production/items/{id}/permanent-override-par
GET /production/profiles
GET /production/profiles/active
POST /production/profiles
PUT /production/profiles/{id}
POST /production/profiles/{id}/deactivate
GET /production/profiles/{id}/lines
PUT /production/profiles/{id}/lines
GET /production/pos-menu-items
GET /production/pos-menu-items/active
POST /production/pos-menu-items
PUT /production/pos-menu-items/{id}
POST /production/pos-menu-items/{id}/deactivate
POST /production/pos-menu-items/import
POST /production/pos-menu-items/delete-by-skus
GET /production/product-mappings
POST /production/product-mappings
PUT /production/product-mappings/{id}
POST /production/product-mappings/{id}/deactivate
GET /production/freezer-pull/lines
PUT /production/freezer-pull/par
GET /production/weeks
POST /production/weeks/generate
GET /production/weeks/{id}/days
GET /production/weeks/{id}/lines
PUT /production/weeks/{id}/refresh
PUT /production/week-lines/overrides
GET /labour/positions
GET /labour/positions/active
POST /labour/positions
PUT /labour/positions/{id}
POST /labour/positions/{id}/deactivate
GET /labour/employees
POST /labour/employees
PUT /labour/employees/{id}
POST /labour/employees/{id}/deactivate
GET /labour/settings
PUT /labour/settings
GET /labour/weekly/{weekStartDate}
PUT /labour/weekly
GET /labour/weeks
GET /labour/daily/{workDate}
PUT /labour/daily
```

Current desktop API-mode coverage:

- Food Department: Products, Import Invoice, Manual Invoice, Count Templates, Inventory Counts, and Order Guide.
- Alcohol Department: Products with alcohol profile maintenance, Manual Invoice, Count Templates, Inventory Counts, Order Guide, Product Profiles for weighted counts, and Sales Mappings.
- Supplies Department: Products, Manual Invoice, Count Templates, Inventory Counts, and Order Guide. The `/supplies-invoices` route was deployed on Saturday, September 5, 2026.
- Production: Stations, Production Items, Production Profiles and lines, POS Menu Item maintenance/import/KDS cleanup, Product Mappings, Weekly Production generation/loading/refresh/override saves, and Freezer Pull manual quantities.
- Reporting/Sales: Invoice History, invoice lines and breakdowns, invoice delete, Sales Period list/save, POS sales Excel import persistence, Inventory Valuation, and Weekly Cost Report generation.
- Labour Management: Labour Setup positions/employees/settings, Labour Hours saved-week listing and pop-out Monday-Sunday Shift 1 / Shift 2 employee-hour entry, Daily Labour Cost weekly net-sales/labour percentage entry, Tip Pool allocation, and Tip Pool Breakdown payout reporting with nickel-rounded net payouts and printing.
- Multi-location: `POST /auth/login` returns a store session token, desktop clients send `x-location-token`, and store-owned reads/writes are scoped to the resolved location.
- Product support: GFS product guide CSV parsing stays in the desktop client, normalized products are upserted through `POST /products/import`, and product purchase history loads through `GET /products/{id}/purchase-history`.
- Admin sync: `GET /admin/sync/download` returns a PostgreSQL table snapshot, and `POST /admin/sync/upload` replaces cloud data from a desktop-generated SQLite table snapshot. These remain administrator migration/recovery tools, not routine daily sync.
- Production API routes were deployed to `esm-operations-api` in `ca-central-1` on Friday, September 4, 2026, and read-only smoke checks passed.
- Live desktop validation confirmed Production CSV/report import and Weekly Production generation complete in API mode.
- Reporting/Sales and product support routes were deployed to `esm-operations-api` in `ca-central-1` on Saturday, September 5, 2026, and read-only smoke checks passed.
- Admin sync routes were deployed to `esm-operations-api` in `ca-central-1` on Saturday, September 5, 2026; missing-key checks passed and authenticated read-only download returned 24 tables and 11,417 rows.
- Labour Setup and Labour Hours routes were deployed to `esm-operations-api` in `ca-central-1` on Sunday, September 6, 2026. Daily Labour Cost, Tip Pool support fields, and Labour Hours saved-week listing were deployed on Monday, September 7, 2026. Smoke checks confirmed `/labour/weeks` responds and `/labour/daily/{workDate}` returns tip-pool/uniform context. The live AWS RDS Labour schema was advanced to version 17 with the secure prompt-based `scripts/Apply-LabourSchemaMigration.ps1` helper.
- Labour Management was manager-tested and accepted as working as intended on Tuesday, September 8, 2026.
- Multi-location API scoping and `LocationAuthRequired=true` were deployed for v4.0.0. Smoke checks confirmed `/products` rejects requests without a location token and a TEST store token returns isolated data.

Intentional remaining gaps:

- Variance reporting is intentionally deferred.
- Local SQLite backup/restore remains local file copy behavior by design.

`/health` does not require database settings.

All other endpoints require backend-side PostgreSQL settings:

```powershell
$env:FOOD_INVENTORY_API_DB_URL = "jdbc:postgresql://<host>:5432/postgres?sslmode=require"
$env:FOOD_INVENTORY_API_DB_USER = "<api-db-user>"
$env:FOOD_INVENTORY_API_DB_PASSWORD = "<api-db-password>"
```

For a local proof only, the API can also load the existing Java properties file:

```powershell
$env:FOOD_INVENTORY_API_CONFIG_FILE = "$env:LOCALAPPDATA\FoodInventory\database.properties"
```

Optional port setting:

```powershell
$env:FOOD_INVENTORY_API_PORT = "8080"
```

Optional local diagnostics:

```powershell
$env:FOOD_INVENTORY_API_ERROR_DETAILS = "true"
```

Protected endpoints require:

```powershell
$env:FOOD_INVENTORY_API_KEY = "<shared-api-key>"
```

## Build

```powershell
mvn -f api\pom.xml test
```

Lambda deployment artifact:

```powershell
mvn -f api\pom.xml package
```

The Lambda fat jar is:

```text
api\target\foodinventory-api-lambda.jar
```

## Run Locally

Compile first:

```powershell
mvn -f api\pom.xml package
```

Run:

```powershell
java -jar api\target\foodinventory-api.jar
```

Then test:

```powershell
Invoke-RestMethod http://localhost:8080/health
Invoke-RestMethod http://localhost:8080/products -Headers @{"x-api-key" = $env:FOOD_INVENTORY_API_KEY}
```

Do not commit database credentials. Local environment variables are acceptable for development. AWS-side deployment should use Lambda environment variables, SSM Parameter Store `SecureString`, or AWS Secrets Manager.

The current proof deployment uses Lambda environment variables supplied through CloudFormation parameters. Treat `DbPassword` and `ApiKey` as secrets. Do not commit `samconfig.toml`, command transcripts containing parameter values, or local key files.

## Deploy To AWS Lambda

The first AWS proof uses:

```text
API Gateway HTTP API
-> Lambda
-> direct JDBC to AWS RDS PostgreSQL
```

Use [template.yaml](template.yaml) with AWS SAM. The template is the source of truth for the API Gateway/Lambda stack.

```powershell
cd api
mvn package
sam deploy --guided
```

For repeat local deploys, copy [samconfig.example.toml](samconfig.example.toml) to `samconfig.toml` and fill values locally. Do not commit `samconfig.toml`.

Template parameters:

```text
DbUrl
DbUser
DbPassword
ApiKey
LocationAuthRequired
LambdaSubnetIds
LambdaSecurityGroupIds
ReservedConcurrency
```

Secret parameters:

```text
DbPassword
ApiKey
```

Keep these in local environment variables, a local untracked `samconfig.toml`, AWS Secrets Manager/SSM for a future hardening pass, or your release machine's secure notes. Do not commit command transcripts or files containing these values.

Infrastructure currently managed by SAM:

```text
API Gateway HTTP API
Lambda function
Lambda VPC attachment
Lambda runtime configuration
API stage name
Lambda invoke permission
API URL output
```

Infrastructure intentionally not recreated by this release:

```text
AWS RDS instance
VPC/subnets/security groups
Database schema migrations
GitHub release secrets
Per-device authentication
```

Recommended initial `ReservedConcurrency` on small/free-tier accounts:

```text
0
```

Use `0` to leave function-level reserved concurrency unset. On small AWS accounts, setting function-level reserved concurrency can fail if it would reduce unreserved account concurrency below AWS minimums. The account-level Lambda concurrency limit still prevents unlimited scale during the proof.

RDS security group rule:

```text
Inbound PostgreSQL 5432
Source: Lambda security group
```

Lambda security group rule:

```text
Outbound PostgreSQL 5432
Destination: RDS security group
```

Avoid adding a NAT Gateway for this first proof. If Lambda only needs to reach RDS and uses environment variables for database settings, NAT is not needed for normal endpoint execution.
