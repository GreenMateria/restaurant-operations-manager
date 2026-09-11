# Infrastructure Baseline

_Created: Sunday, September 6, 2026_

This document records the current Infrastructure-as-Code state for ESM Operations Manager and the next practical steps for making the cloud deployment easier to manage and restore.

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
DbUrl
DbUser
DbPassword
ApiKey
LambdaSubnetIds
LambdaSecurityGroupIds
ReservedConcurrency
LocationAuthRequired
```

`DbPassword` and `ApiKey` are marked `NoEcho`, but they are still provided as deployment parameters. A future hardening step should move secret values into AWS Secrets Manager or SSM Parameter Store SecureString.

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

This RDS instance is not currently created by repo IaC. It was discovered as an existing manual/external resource.

The live RDS application schema is currently at version 17. Labour Management schema setup was applied outside SAM because the restricted `operations_app` runtime user intentionally cannot create tables in the `public` schema. Use `scripts/Apply-LabourSchemaMigration.ps1` with the schema-capable RDS admin user for this Labour schema setup path; do not store the admin password in repo files or Lambda environment variables.

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

Current script:

```text
scripts/Create-AwsRdsAppUser.ps1
```

Purpose:

- Create or repair the restricted AWS RDS application database user.
- Grant normal table/sequence access.

This is useful operational scripting, but not complete declarative IaC.

## Not Managed By IaC Yet

These are still manual or external today:

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

## Recommended Next IaC Steps

Do these after the first multi-location rollout has enough real operating feedback, so IaC reflects the target operating shape instead of guessing too early:

1. Define dev/test/prod and per-location environment boundaries.
2. Move API key and database password handling toward Secrets Manager or SSM SecureString.
3. Create a dedicated RDS security group and document the direct PostgreSQL fallback decision.
4. Decide whether to import existing RDS/network resources into CloudFormation or document them as external dependencies.
5. Add database backup/snapshot policy, cost alarms, API custom domain, DNS, certificate, and per-user authentication once rollout requirements are known.

Avoid adding a second RDS instance, NAT Gateway, Multi-AZ RDS, or RDS Proxy until there is a clear reason and the budget impact is accepted.
