# Infrastructure Baseline

_Created: Sunday, September 6, 2026_

_Current baseline verified and migrated: Sunday, October 4, 2026_

## Current IaC State

The existing application infrastructure has been adopted in place. The live database, API endpoint, and live/test stores were preserved. Current definitions are `api/template.yaml` and the templates under `infra/`; `infra/environments.json` records the account, region, and stacks.

- `esm-operations-api`: API Gateway, Lambda, execution role, invoke permissions, and log group.
- `esm-operations-network`: existing VPC, three subnets, database access security group, internet gateway, main route table, and internet route.
- `esm-operations-infrastructure`: existing RDS instance, database subnet group, Lambda security group, and RDS monitoring role. RDS retains automated backups for seven days and has deletion protection; a stack policy forbids database replacement/deletion.
- `esm-operations-secrets`: retained Secrets Manager secret with the existing API key and restricted database credentials. The API deployment passes only its ARN. Existing credential values were preserved.
- `esm-operations-monitoring`: database storage/CPU and unhandled Lambda error alarms. No notification subscription is enabled; alarms are visible in CloudWatch and the admin status view.
- `infra/budget.json`: declarative definition of the existing USD 30 monthly budget. The deployment tool updates its limit in place when needed, preserving existing notifications. The legacy Budget resource is not imported into CloudFormation.

Open **Admin Tools.cmd** for named Plan/Apply infrastructure actions and status. See [Infrastructure operations and recovery](docs/INFRASTRUCTURE_OPERATIONS.md) and [Admin Tools](docs/ADMIN_TOOLS.md). The original discovery notes below remain as historical context for earlier configuration.

This document records the current Infrastructure-as-Code state for StoreOps Manager and the next practical steps for making the cloud deployment easier to manage and restore.

## Current AWS Account

Current deployment account discovered with AWS CLI:

```text
Account: 863819358995
Deploy IAM user: arn:aws:iam::863819358995:user/esm-operations-deploy
Default region: ca-central-1
```

Do not commit AWS access keys, database passwords, API keys, or copied command output that includes secret values.

## Current Monthly Budget

AWS Budgets currently has a monthly cost budget:

```text
Budget name: My Monthly Cost Budget
Limit: 30.00 USD monthly
Status: HEALTHY
Actual spend when checked: 3.269 USD
Checked on: Sunday, September 6, 2026
```

Keep this budget in place before adding new AWS resources. The highest-risk cost items for this project are larger RDS instances, Multi-AZ RDS, NAT Gateway, RDS Proxy, long CloudWatch log retention, and duplicated per-store RDS instances.

## Managed By IaC Today

The API stack is managed by AWS SAM / CloudFormation:

```text
Template: api/template.yaml
Example deploy config: api/samconfig.example.toml
Stack name: esm-operations-api
Stack region: ca-central-1
Stack status: UPDATE_COMPLETE
API URL: https://rn0j30p2vf.execute-api.ca-central-1.amazonaws.com/prod
```

SAM-managed resources:

```text
AWS::ApiGatewayV2::Api       FoodInventoryHttpApi        rn0j30p2vf
AWS::ApiGatewayV2::Stage     FoodInventoryHttpApiStage   prod
AWS::Lambda::Function        FoodInventoryApiFunction    esm-operations-api-FoodInventoryApiFunction-HoikXKwCH2C0
AWS::Lambda::Permission      FoodInventoryApiFunctionApiProxyPermission
AWS::IAM::Role               FoodInventoryApiFunctionRole
```

The SAM template currently takes these deployment parameters:

```text
ApiStageName
RuntimeSecretArn
LambdaSubnetIds
LambdaSecurityGroupIds
ReservedConcurrency
LocationAuthRequired
```

Database credentials and the API key are now stored in Secrets Manager. The template resolves them through `RuntimeSecretArn`; normal SAM deployment configuration contains no raw database password or API key. The one-time seed used a `NoEcho` parameter and a temporary file restricted to the Windows operator.

## Current API Runtime

The deployed Lambda configuration:

```text
Function name: esm-operations-api-FoodInventoryApiFunction-HoikXKwCH2C0
Runtime: java25
Handler: ca.foodinventory.api.LambdaApiHandler::handleRequest
Memory: 512 MB
Timeout: 30 seconds
State: Active
```

Configured environment variable names:

```text
FOOD_INVENTORY_API_DB_URL
FOOD_INVENTORY_API_DB_USER
FOOD_INVENTORY_API_DB_PASSWORD
FOOD_INVENTORY_API_KEY
FOOD_INVENTORY_API_ERROR_DETAILS
FOOD_INVENTORY_LOCATION_AUTH_REQUIRED
```

Do not print full Lambda environment variables in shared notes because they include secrets.

Current Lambda log group:

```text
/aws/lambda/esm-operations-api-FoodInventoryApiFunction-HoikXKwCH2C0
Retention: 30 days
Stack resource: FoodInventoryApiFunctionLogGroup
```

The SAM template declares this log group with configurable retention:

```text
Parameter: LambdaLogRetentionDays
Default: 30
Resource: FoodInventoryApiFunctionLogGroup
```

Import status:

```text
Imported into stack: Sunday, September 6, 2026
Stack status after import/update: UPDATE_COMPLETE
Verified retention after import/update: 30 days
Drift status after import/update: IN_SYNC
API location auth after v4.0.0 deployment: required
Labour API routes deployed: Sunday, September 6, 2026
Labour API route updates for `/labour/weeks`, Daily Labour Cost, Tip Pool, and Tip Pool Breakdown support fields deployed: Monday, September 7, 2026
Protected-password API routes deployed: Thursday, September 24, 2026
```

## Current Database

Current AWS RDS instance:

```text
DB instance identifier: esm-operations-db
Engine: postgres
Engine version: 18.3
Instance class: db.t4g.micro
Status: available
Multi-AZ: false
Storage: 20 GB gp2
Endpoint: esm-operations-db.cdcok68as3cr.ca-central-1.rds.amazonaws.com
DB subnet group: default-vpc-0b41e3692165238ed
VPC security group: sg-056216eb5a750ccfa
```

This existing RDS instance was imported into `esm-operations-infrastructure` on October 4, 2026. The import did not recreate it. Current backup retention is seven days and deletion protection is enabled.

The live RDS application schema is currently at version 23. Labour Management, multi-location, location-aware uniqueness, protected-password, and labour pay-rate schema setup paths remain controlled database migrations, separate from infrastructure deployment. The restricted `operations_app` runtime user cannot create or alter schema-owned structures in the `public` schema. Use the secure prompt-based migration helpers with the schema-capable RDS admin user; do not store the admin password in repo files or Lambda environment variables.

For the current budget, keep the database small and Single-AZ unless the budget is intentionally changed.

## Current Network

Current VPC:

```text
VPC: vpc-0b41e3692165238ed
```

Lambda subnets used by the SAM stack:

```text
subnet-02d952055a4433d04  ca-central-1a  172.31.16.0/20
subnet-05226760382273fff  ca-central-1b  172.31.0.0/20
```

Current Lambda security group:

```text
Security group: sg-09e7c97add719b857
Name: esm-operations-api-lambda-sg
Ingress: none
Egress: tcp/5432 to RDS security group sg-056216eb5a750ccfa
```

Current RDS security group:

```text
Security group: sg-056216eb5a750ccfa
Name: default
Ingress:
  - tcp/5432 from Lambda security group sg-09e7c97add719b857
  - tcp/5432 from 72.38.209.198/32
  - all traffic from itself
Egress:
  - all traffic to 0.0.0.0/0
```

Recommended future hardening:

- Create a dedicated RDS security group instead of using the default security group.
- Keep Lambda-to-RDS PostgreSQL access explicit.
- Remove direct public/client PostgreSQL ingress when direct database fallback is no longer needed.
- Avoid NAT Gateway unless a specific Lambda outbound internet requirement appears.

## Release Automation

Desktop releases are scripted by:

```text
Release.ps1
```

This script handles:

- Maven project version update.
- Git commit, push, and tag.
- Release API config generation from local secret input.
- Maven package.
- `jpackage` Windows installer creation.
- GitHub Release publishing.

This is automation, not cloud IaC. Keep it separate from AWS infrastructure deployment.

## Existing Operational Scripts

The Windows entry point for API deployment and store administration is now **Admin Tools.cmd**. It opens a graphical launcher with named actions and store selection by name, saved non-secret settings, and retained activity history. See [Admin Tools instructions](docs/ADMIN_TOOLS.md). The existing scripts remain the implementation, and `Release.ps1` remains unchanged.

Current script:

```text
scripts/Create-AwsRdsAppUser.ps1
```

Purpose:

- Create or repair the restricted AWS RDS application database user.
- Grant normal table/sequence access.

This is useful operational scripting, but not complete declarative IaC.

## Historical Gaps Before October 4 Migration

The original discovery list was:

- RDS instance creation.
- RDS database creation.
- RDS subnet group and VPC baseline.
- Dedicated RDS security group.
- Secrets Manager or SSM SecureString values.
- Cost alarms beyond the existing monthly budget.
- Dev/test/prod environment separation.
- Store provisioning.
- Database backups/snapshot policy.
- API custom domain, DNS, and certificate.
- Per-user authentication beyond the current per-store Store Login model.

## Historical IaC Planning

The original rollout plan below is retained for context. Current infrastructure operations are described in the linked operations guide:

1. Define dev/test/prod and per-location environment boundaries.
2. Move API key and database password handling toward Secrets Manager or SSM SecureString.
3. Create a dedicated RDS security group and document the direct PostgreSQL fallback decision.
4. Decide whether to import existing RDS/network resources into CloudFormation or document them as external dependencies.
5. Add database backup/snapshot policy, cost alarms, API custom domain, DNS, certificate, and per-user authentication once rollout requirements are known.

Avoid adding a second RDS instance, NAT Gateway, Multi-AZ RDS, or RDS Proxy until there is a clear reason and the budget impact is accepted.
