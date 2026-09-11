# API Layer Plan

_Created: Tuesday, September 1, 2026_
_Last Updated: Friday, September 11, 2026_

This document plans the next architecture step for ESM Operations Manager: moving database credentials off client PCs by putting an authenticated API layer between the desktop application and AWS RDS PostgreSQL.

## Goal

Before the v3.1.1 API release, configured work PCs connected directly to AWS RDS PostgreSQL in Cloud PostgreSQL mode. That worked for daily operations, but required database connection details and an app database password on each client system.

The target architecture is:

```text
Desktop app -> HTTPS API -> AWS RDS PostgreSQL
```

Client PCs should store only API endpoint settings and client authentication material. PostgreSQL credentials should live only in AWS-side configuration such as Lambda environment variables, AWS Secrets Manager, or equivalent backend configuration.

## Recommended AWS Direction

Start with:

```text
Amazon API Gateway HTTP API
-> AWS Lambda
-> AWS RDS PostgreSQL
```

Reasons:

- It removes direct database credentials from client PCs.
- API Gateway and Lambda have free-tier-friendly usage for a small internal desktop app.
- It avoids running and patching a full server while the API surface is still evolving.
- It can sit in front of the existing AWS RDS PostgreSQL database.
- It gives a path to a future browser frontend without replacing the desktop app immediately.

Important cost note:

- This can be very low cost, but it should not be treated as guaranteed free forever.
- API Gateway free tier and AWS database free-tier eligibility depend on account age, plan, usage, and AWS free-tier rules.
- The existing RDS instance may continue to be the main paid component if the AWS account is outside free-tier eligibility.

## AWS Free / Low-Cost Findings

As of Tuesday, September 1, 2026, AWS documentation shows:

- Amazon API Gateway charges by API calls and data transfer, with no minimum fee. The API Gateway free tier includes monthly free API calls for eligible new accounts.
- AWS Lambda has a monthly free tier for requests and compute duration that should cover a small internal operations app if endpoints are efficient.
- Amazon RDS PostgreSQL free-tier availability depends on account eligibility and supported small instance classes.
- Aurora PostgreSQL serverless is available through the newer AWS Free Tier plan, but with tight free-plan limits such as low storage. It may be useful for testing but should not replace the current production RDS database without a separate migration decision.

Decision:

Keep the current AWS RDS PostgreSQL database for production data. Build the API so it can point at that database first. Revisit Aurora Serverless or another database option only after validating account eligibility, storage needs, and real monthly cost.

## Security Model

Backend:

- Store PostgreSQL credentials in AWS, not on clients.
- Use the existing lower-access `operations_app` database user or create a narrower API-specific database user.
- Require SSL to PostgreSQL.
- Keep database schema migrations as administrative actions, not normal API startup behavior.

Client:

- Store API base URL locally.
- Do not store PostgreSQL URL, username, or password.
- Use API authentication instead of database authentication.

Initial authentication options:

1. API Gateway API key with usage plan.
   - Simple first internal rollout.
   - Better than database credentials on PCs.
   - Not enough for strong per-user authorization by itself.

2. Cognito user pool or JWT authorizer.
   - Better long-term user identity.
   - More setup and UI work.
   - Preferred before expanding beyond a few controlled work PCs.

Recommended first step:

Use API Gateway HTTP API plus a simple authorizer/API-key-style rollout for a development proof. Design the backend so Cognito/JWT can replace or supplement it later.

## Application Migration Strategy

Do not try to make HTTP look like a JDBC `Connection`.

The current app has many DAO calls shaped around:

```java
DatabaseManager.getConnection()
```

An API layer should be introduced at service/module boundaries instead:

```text
JavaFX View
-> Client-side service
-> API client
-> HTTPS API
-> Backend service/DAO
-> PostgreSQL
```

SQLite remains available only as a local backup snapshot/development path:

```text
Cloud API mode             -> normal desktop workflows
Local SQLite snapshot      -> backup/recovery file and development fallback
```

Add a new configured mode later:

```text
api
```

Direct desktop PostgreSQL mode has been retired now that the API path covers normal daily workflows.

Migrate by department rather than by technical table when possible. This matches how managers use the application and gives each rollout a clear acceptance test: one department should be usable end-to-end before the next department starts.

Initial department target:

```text
Food Department
-> Products
-> Import Invoice
-> Manual Invoice
-> Count Templates
-> Inventory Counts
-> Order Guide
```

Food Department, Alcohol Department, Supplies Department, Production, Reporting/Sales, Labour Setup, Labour Hours, Daily Labour Cost, Tip Pool, Tip Pool Breakdown, GFS product guide import persistence, product purchase history, and administrative cloud snapshot workflows are now API-backed in the development build. CSV and Excel parsing remains in the desktop client; the API receives normalized records for persistence and reporting.

## First API Slice

Start with read-heavy, low-risk endpoints before writes:

1. Health and version
   - `GET /health`
   - `GET /version`

2. Reference data
   - `GET /products`
   - `GET /products/{id}`
   - `GET /suppliers` if needed by UI
   - `GET /pos-menu-items`

3. Food Department end-to-end API mode
   - Food products read/write
   - Food count templates, count lists, count entry, save quantities, and complete count
   - Food invoice import/save and manual invoice save
   - Food Order Guide generation and case-size save

Alcohol Sales Mappings was useful as a small first write proof. Food Department, Alcohol Department, Supplies Department, Production, Reporting/Sales, Labour Management, and the product support gaps now follow the API pattern. Direct desktop PostgreSQL mode is retired from normal client use; AWS RDS access should go through the API.

## Backend Shape

Recommended backend language:

Java is acceptable because the current project already contains the business rules and DAO knowledge.

Practical options:

1. Java Lambda handlers
   - Reuse model/DAO/service code where possible.
   - Larger cold starts than tiny JavaScript/Python functions, but acceptable for internal workflows if tested.

2. Small Java API service on AWS App Runner or ECS/Fargate
   - Easier connection pooling and conventional web app structure.
   - More likely to have a baseline monthly cost.

3. Node/Python Lambda
   - Fast to build small endpoints.
   - Duplicates SQL/business logic outside the Java codebase.

Recommendation:

Use Java for the first API proof unless cold start or packaging becomes a practical problem. Keep backend code in a separate module so desktop UI code is not pulled into Lambda packaging.

Suggested repo structure:

```text
api/
  pom.xml
  src/main/java/ca/foodinventory/api
shared/
  src/main/java/ca/foodinventory/model
  src/main/java/ca/foodinventory/service
desktop/
  existing JavaFX app over time
```

For the first proof, avoid a full repo restructure. Add the smallest backend module that can compile independently and talk to PostgreSQL.

## Data And Transaction Rules

The API must preserve current behavior:

- Completed counts and invoices remain historical records.
- Upload/download cloud sync remains administrative and is API-backed only as a migration/recovery tool, not as routine daily sync.
- Schema migrations stay controlled.
- Write endpoints must wrap multi-row saves in transactions.
- Endpoints must return useful errors for validation, duplicate records, and unavailable database.

Avoid chatty endpoints for cloud workflows. Batch where the desktop app currently batches:

- Inventory count save/complete
- Sales period import/save
- Weekly Production usage report import/generation

## Phased Implementation

### Phase 1: Cost And AWS Account Check

- Confirm the AWS account free-tier status and current RDS monthly cost.
- Confirm whether API Gateway and Lambda usage will fit the free tier for expected work-PC traffic.
- Create a monthly budget alert before deploying anything new.

### Phase 2: API Proof

- Add a minimal API module.
- Implement `/health`.
- Implement one read endpoint such as `/products`.
- Connect Lambda to the current AWS RDS PostgreSQL database using AWS-side credentials.
- Confirm no database credentials are needed on the client for that endpoint.

### Phase 3: Desktop API Client Proof

- Add local config keys:

```text
mode=api
api.url=<https endpoint>
api.key=<temporary internal key or token>
```

- Add an API client class.
- Wire one low-risk screen or diagnostic action to the API endpoint.
- Keep SQLite local fallback untouched during the first proof.

### Phase 4: First Real Workflow

- Move weekly inventory count list/template/start/open/read support to API mode.
- Move inventory quantity save and complete-count behavior to API mode.
- Preserve current batching and transaction behavior for count saves/completions.
- Add request/response logging on the backend without logging credentials or sensitive data.

### Phase 5: Broader Migration

Migration order:

Completed:

1. Shared Product and POS reference reads.
2. Food Department end-to-end workflow.
3. Alcohol Department end-to-end workflow.
4. Production prep-list workflow.
5. Supplies Department end-to-end workflow.

Remaining:

1. Multi-location data model, access rules, and rollout strategy.
2. Infrastructure as Code completion for cloud resources that support the selected rollout strategy.
3. Local backup/export polish.

## Open Decisions

- Whether the first backend should use Java Lambda or a small always-on Java service.
- Whether authentication starts with API keys or Cognito/JWT.
- Whether RDS should remain publicly reachable temporarily or move behind private networking once the API is working.
- Closed: direct desktop PostgreSQL mode should not remain a hidden administrator fallback after API mode becomes normal.

## Current Recommendation

Keep the Java Lambda/API Gateway API against the existing AWS RDS PostgreSQL database. As of the v4.0.0 release target, Food Department, Alcohol Department, Supplies Department, Production, Reporting/Sales, Labour Setup, Labour Hours, Daily Labour Cost, Tip Pool, Tip Pool Breakdown, GFS product guide import persistence, product purchase history, and administrative cloud snapshot download are API-backed for normal desktop use. Store Login scopes normal store-owned workflows to the signed-in location. Direct PostgreSQL mode is retired from desktop clients.

## Implementation Status

Started on Tuesday, September 1, 2026:

- Added a standalone `api/` Maven project.
- Added a local Java HTTP API server.
- Added `GET /health`.
- Added `GET /products`.
- Added backend-side PostgreSQL configuration through environment variables or JVM system properties.
- Added a simple reusable PostgreSQL connection holder with idle reconnect behavior.
- Confirmed `mvn -f api\pom.xml test` passes.
- Confirmed root desktop project `mvn clean test` still passes.
- Confirmed `GET /health` works from the packaged API jar.
- Added optional local config-file loading with `FOOD_INVENTORY_API_CONFIG_FILE`.
- Added optional local diagnostics with `FOOD_INVENTORY_API_ERROR_DETAILS=true`.
- Attempted `GET /products` against the configured AWS RDS PostgreSQL database. The API reached the PostgreSQL driver, but the TCP connection to port `5432` timed out from the local development environment.
- Added a Lambda handler for API Gateway HTTP API events.
- Added a SAM deployment template for API Gateway HTTP API, Lambda, VPC access to RDS, and reserved Lambda concurrency.
- Added Maven Lambda fat-jar packaging at `api\target\foodinventory-api-lambda.jar`.
- Deployed AWS proof stack `esm-operations-api` in `ca-central-1`.
- Deployment created API Gateway HTTP API, Lambda Java 25 function, Lambda IAM role, and Lambda VPC attachment.
- API URL: `https://rn0j30p2vf.execute-api.ca-central-1.amazonaws.com/prod`.
- First deploy with `ReservedConcurrency=5` failed because the account could not reduce unreserved Lambda concurrency below AWS minimums.
- Template now supports `ReservedConcurrency=0`, which leaves function-level reserved concurrency unset for small/free-tier accounts.
- Verified deployed `GET /health` returns `{"status":"ok","version":"3.1.2"}` after the v3.1.2 API version update on Monday, September 7, 2026.
- Verified deployed `GET /products` returns `401 Unauthorized` without `x-api-key`.
- Verified deployed `GET /products` returns `359` active products from AWS RDS when called with the configured `x-api-key`.
- Current API key was generated during deployment and temporarily written to `%TEMP%\esm-api-key.txt` for verification. Do not commit it.
- Added desktop configuration support for `mode=api`, `api.url`, and `api.key` in `%LOCALAPPDATA%\FoodInventory\database.properties`, matching JVM system properties and environment-variable overrides.
- Added desktop API health-check support from the System screen.
- Added a desktop Product API client and wired the Products screen to load active products through `GET /products` when the app starts in API mode.
- In API mode, Food, Alcohol, and Supplies product add/edit/deactivate are available. Alcohol product/profile maintenance is saved through the Product API. Product purchase-history lookup is API-backed, and GFS product guide CSV parsing remains client-side before normalized products are upserted through the API.
- Added `GET /pos-menu-items` to the API and deployed it to the AWS proof stack.
- Verified deployed `GET /pos-menu-items` returns `401 Unauthorized` without `x-api-key`.
- Verified deployed `GET /pos-menu-items` returns `642` POS menu items from AWS RDS when called with the configured `x-api-key`.
- Added a desktop POS Menu Items API client and wired the POS Menu Items screen to load through `GET /pos-menu-items` when the app starts in API mode.
- In API mode, POS Menu Item add/edit/deactivate/import/delete, KDS cleanup, and usage-report import are implemented through the Production API.
- Added `GET /alcohol-sales-mappings`, `POST /alcohol-sales-mappings`, `PUT /alcohol-sales-mappings/{id}`, and `POST /alcohol-sales-mappings/{id}/deactivate` to the API and deployed them to the AWS proof stack.
- Verified deployed `GET /alcohol-sales-mappings` returns `401 Unauthorized` without `x-api-key`.
- Verified deployed `GET /alcohol-sales-mappings` returns the current alcohol mapping data from AWS RDS when called with the configured `x-api-key`.
- Verified deployed `POST /alcohol-sales-mappings` returns `401 Unauthorized` without `x-api-key`.
- Added a desktop Alcohol Sales Mappings API client and wired the Alcohol Sales Mappings screen to load/save/deactivate through the API in API mode.
- In API mode, the Alcohol Sales Mapping dialog loads its POS item and alcohol product pickers from the already migrated API clients.
- Authenticated mapping write testing should be done manually through the desktop UI against intentional real setup changes, not with throwaway command-line records against production data.
- Food Department, Alcohol Department, Supplies Department, Production, Reporting/Sales, Labour Setup, Labour Hours, Daily Labour Cost, Tip Pool, Tip Pool Breakdown, Alcohol Variance, and product support gaps are now API-backed in the development build.

Current Food implementation status:

- Food Products: API-backed load, add/edit non-alcohol product fields, deactivate, product purchase history, and GFS product guide import persistence are implemented. GFS CSV parsing remains client-side.
- Food Import Invoice and Manual Invoice: duplicate check, overwrite delete, and save are implemented locally through the API.
- Food Count Templates: list, add, deactivate, duplicate, template-line load/add/edit/remove, and sort-order save are implemented locally through the API.
- Food Inventory Counts: list, start count, load lines, save quantities, complete, delete, and print loaded count sheets are implemented locally through the API.
- Food Order Guide: completed count list, guide generation, and case-size save are implemented locally through the API.

Current Alcohol implementation status:

- Alcohol Products: API-backed load, add/edit product fields, add/edit alcohol profile fields, and deactivate are implemented and deployed.
- Alcohol Manual Invoice: product loading, duplicate check, overwrite delete, and save are implemented through the API.
- Alcohol Count Templates: list, add, deactivate, duplicate, template-line load/add/edit/remove, and sort-order save are implemented through the API.
- Alcohol Inventory Counts: list, start count, load lines, save quantities, complete, delete, and print loaded count sheets are implemented through the API.
- Alcohol Order Guide: completed count list, guide generation, and case-size save are implemented through the API.
- Alcohol Product Profiles: active profile reads and product-editor maintenance are implemented through the API so weighted count entry and blank count sheets keep their existing bottle/keg behavior.
- Alcohol Variance Report is complete in the desktop app by reusing API-backed completed counts, order guide generation, inventory valuation, alcohol product profiles, Alcohol Sales Mappings, and client-side POS usage import.

Current Production implementation status:

- Production setup: Stations, Production Items, Production Profiles/profile lines, and Product Mappings are implemented through the API.
- POS Menu Items: load, add, edit, deactivate, setup import, usage-report import, and KDS cleanup are implemented through the API.
- Weekly Production: generation, loading, Refresh Week, override saves, and permanent override par saves are implemented through the API.
- Freezer Pull: manual quantity loading and saves are implemented through the API.
- Production API backing was deployed to `esm-operations-api` in `ca-central-1` on Friday, September 4, 2026.
- Read-only smoke checks passed for production setup, POS menu items, product mappings, weekly production, and Freezer Pull endpoints.
- Live desktop validation confirmed Production CSV/report import and Weekly Production generation complete in API mode.
- The desktop app now includes the missing Log4j runtime provider required by Apache POI, so production report imports no longer print a missing logging provider warning.

Current Supplies implementation status:

- Supplies Products: API-backed load, add/edit non-alcohol product fields, and deactivate are implemented.
- Supplies Manual Invoice: product loading, duplicate check, overwrite delete, and save are implemented through the API using the explicit `/supplies-invoices` route.
- Supplies Count Templates: list, add, deactivate, duplicate, template-line load/add/edit/remove, and sort-order save are implemented through the API.
- Supplies Inventory Counts: list, start count, load lines, save quantities, complete, delete, and print loaded count sheets are implemented through the API.
- Supplies Order Guide: completed count list, guide generation, and case-size save are implemented through the API.
- The SAM template includes the `/supplies-invoices` route, and the live API stack was deployed on Saturday, September 5, 2026.
- Read-only smoke checks passed for health, missing-key `401` on `/supplies-invoices`, Supplies templates, counts, completed counts, and order guide generation from completed count `31` to `35`.

Current Labour implementation status:

- Labour Setup: positions, employees, default uniform deduction settings, tip-pool eligibility, uniform-deduction applicability, and active/inactive state are implemented through the API.
- Labour Hours: saved-week listing, Monday-Sunday Shift 1 / Shift 2 employee-hour loading, and bulk save are implemented through the API.
- Daily Labour Cost: weekly start/end selection, editable daily net sales, BOH and FOH labour dollars, BOH and FOH labour percentages, total labour percentage, and total-row calculation are implemented through API-loaded daily labour records.
- Tip Pool: daily tip-out pool amounts, tip-pool eligible employee hours, calculated daily employee tip allocations, and weekly totals are implemented through the API.
- Tip Pool Breakdown: selected date range gross tip allocation, configured uniform deductions, nickel-rounded net payouts, and payout-report printing are implemented from saved Labour Hours and Tip Pool data.
- Labour Management was manager-tested and accepted as working as intended on Tuesday, September 8, 2026.
- Labour Hours stores wage, employee name, position name, labour group, tip-pool eligibility, and uniform-deduction context on daily entries so historical weeks do not recalculate from later setup changes.
- Labour routes were deployed to `esm-operations-api` in `ca-central-1` on Sunday, September 6, 2026, and updated on Monday, September 7, 2026 for `/labour/weeks`, Daily Labour Cost, Tip Pool, and Tip Pool Breakdown support fields.
- The live AWS RDS schema was advanced through schema version 21 using the Labour and multi-location migration scripts; the restricted `operations_app` runtime user remains unable to create tables by design.

Current multi-location implementation status:

- Store Login is implemented for Cloud API mode and enabled in release packaging with `location.login.required=true`.
- `POST /auth/login` issues location session tokens; normal store-owned API routes require the location token when `LocationAuthRequired=true`.
- Food, Alcohol, Supplies, Production, Reporting/Sales, Purchasing, and Labour API workflows scope reads and writes to the resolved `location_id`.
- PostgreSQL uniqueness for duplicate-sensitive store-owned records is location-aware, so each store can have its own SKUs, POS SKUs, setup names, and production weeks.
- Administrative scripts can create/rename stores, reset store passwords, apply schema migrations, apply location-aware uniqueness, and copy selected setup/master data between stores.
- The live API was deployed with location auth required; smoke checks confirmed `/products` rejects requests without a location token and a TEST store token returns isolated data.

Remaining API migration targets:

- Infrastructure-as-Code completion for the chosen API/database/security architecture.
- Future cross-location reporting and safer non-direct-RDS administration once real multi-store operating requirements are known.
- Keep CSV/Excel parsing client-side unless a future browser client or file-processing requirement needs server-side parsing.

Deployment status:

- The SAM template includes the new Food routes.
- The live API was updated on Tuesday, September 1, 2026.
- Read-only smoke checks passed for Food templates, counts, completed counts, count lines, order guide generation, and missing-key `401` behavior.
- Alcohol routes were deployed on Tuesday, September 1, 2026.
- Read-only smoke checks passed for Alcohol templates, counts, completed counts, count lines, order guide generation, alcohol product profiles, and missing-key `401` behavior.
- Product API smoke check passed for Alcohol profile loading: 85 Alcohol products returned and all 85 include active profile data.
- Production routes were deployed on Friday, September 4, 2026.
- Read-only smoke checks passed for health, missing-key `401`, stations, active production items, profiles, profile lines, active POS menu items, product mappings, production weeks, week days, week lines, and Freezer Pull lines.
- Supplies desktop/API changes are implemented, deployed, and read-only smoke checked.
- Reporting/Sales and product support routes were deployed on Saturday, September 5, 2026.
- The SAM template was consolidated to one `ANY /{proxy+}` route so Lambda permissions do not exceed AWS resource-policy size limits as the API grows.
- Live smoke checks passed for health version `3.0.6`, missing-key `401` on `/reporting/invoices` and `/products/import`, Sales Periods, Invoice History, invoice `106` lines/breakdown, Food valuation count `34`, Weekly Cost Report from count `32` to `34`, product purchase history for product `267`, and active product listing. `GET /health` was later updated and verified at version `3.1.2` on Monday, September 7, 2026.
- Administrative sync routes were deployed on Saturday, September 5, 2026.
- Admin sync smoke checks passed for missing-key `401` on `/admin/sync/download` and `/admin/sync/upload`; authenticated read-only download returned 24 tables, 11,417 rows, and a 1.85 MB cloud snapshot. Authenticated upload should only be tested through intentional desktop UI use because it replaces production cloud data.
- Labour Setup and Labour Hours routes were deployed on Sunday, September 6, 2026; Labour Hours initially returned 404 until the Lambda update was deployed, then required the Labour schema to be applied to AWS RDS. Daily Labour Cost, Tip Pool support fields, and `/labour/weeks` were deployed on Monday, September 7, 2026. Smoke checks confirmed `/labour/weeks` responds and `/labour/daily/{workDate}` returns tip-pool/uniform context.
- Labour Management was accepted as complete after manager workflow testing on Tuesday, September 8, 2026.
- Multi-location Store Login, location token enforcement, and TEST-store isolation were deployed and validated on Friday, September 11, 2026.
- Normal API workflows for Food, Alcohol, Supplies, Production, Reporting/Sales, product import, product purchase history, Labour, and store-scoped operation are confirmed working for the v4.0.0 release target.

Next implementation step:

- Continue Cloud API daily operation on configured work PCs.
- Keep direct Cloud PostgreSQL credentials out of normal desktop clients.
