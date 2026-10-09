# Infrastructure operations

Verified in AWS account `863819358995`, region `ca-central-1`, on October 4, 2026.

## What is managed

| Stack or definition | Scope |
| --- | --- |
| `esm-operations-api` / `api/template.yaml` | API Gateway, Lambda, IAM execution role, permissions, runtime settings, and log retention |
| `esm-operations-network` / `infra/network.template.json` | Existing VPC, three subnets, database access group, internet gateway, main route table, and internet route |
| `esm-operations-infrastructure` / `infra/infrastructure.template.json` | Existing RDS instance, subnet group, Lambda security group, and enhanced monitoring role |
| `esm-operations-secrets` / `infra/secrets.template.json` | Existing application credentials and API key, retained in Secrets Manager |
| `esm-operations-monitoring` / `infra/monitoring.template.json` | Database CPU/storage and unhandled Lambda error alarms |
| `infra/budget.json` | Existing USD 30 monthly budget; limit updated in place without replacing its notifications |

`infra/environments.json` records account, region, stacks, and intentional external dependencies. The live and TEST stores remain separate locations within one deployment/database. No separate dev/test AWS environment was created.

The migration imported existing resources; it preserved physical resource IDs, database endpoint, API URL, credential values, public developer access rules, and store records. It then increased RDS retention from one to seven days and enabled deletion protection. No schema changes or store provisioning were run as part of infrastructure adoption.

## Everyday use

1. Open **Admin Tools.cmd**.
2. For ordinary API code changes, use **Deploy API (usual update)**. No infrastructure Plan/Apply is required. The saved SAM configuration references `RuntimeSecretArn`, not raw credentials.
3. For AWS configuration changes, first edit/review the relevant template.
4. Choose **Plan infrastructure changes** in the maintenance picker and run it. Review additions/modifications in the task window.
5. Close the task window. Choose **Apply reviewed infrastructure changes**, run it, and type APPLY after review.
6. Use **AWS infrastructure status** to check stacks and alarms.

Plans expire after 24 hours and are refused if templates change after planning. Each plan is retained under ignored `.admin/` files. The tool refuses resource removals and replacements. Positive-to-positive RDS backup retention changes have a narrow reviewed exception; an independent CloudFormation stack policy forbids replacing/deleting the Database resource.

The database uses `DeletionPolicy: Retain` and `UpdateReplacePolicy: Retain`, RDS deletion protection, and stack-policy protection. Managed infrastructure stacks have termination protection. These prevent accidental destructive deployment; intentional retirement needs a separate explicit procedure.

The migration helpers under `infra/Import-*`, `Seed-ApiSecret.ps1`, and `Enable-ManagedApiSecrets.ps1` are one-time adoption tools, not everyday deployment steps. Do not rerun them against the adopted resources. Use the launcher.

## Credentials and monitoring

The retained secret `storeops/production/api-credentials` contains the existing restricted database username/password, JDBC URL, and API key. Initial seeding used a `NoEcho` parameter and a temporary file restricted to the Windows operator; the temporary file was removed afterward. No credentials were generated or rotated. CloudFormation resolves dynamic references when updating Lambda configuration; the Java service still receives the values as environment variables. This is not per-request secret retrieval or automatic password rotation.

Future credential rotation must update the database/secret consistently, explicitly refresh Lambda environment configuration, and update desktop packaging if the API key changes. A code-only deployment is not a substitute for deliberately refreshing secret-bearing environment configuration. Do not change the seed parameter during routine infrastructure deployment; it uses its previous value.

The previous SAM configuration was preserved as a Windows-user-encrypted `.admin/samconfig-*.encrypted` backup. Do not restore the old credential-parameter format against the new API template. Passwords for RDS administrator and store logins are still prompted in store-management tasks. AWS operator credentials remain managed by AWS CLI, outside the application templates.

Three CloudWatch alarms were added. Their optional `AlarmTopicArn` is empty, so they do not send notifications. CPU/storage alarms cover RDS; Lambda Errors counts unhandled invocation errors, not every handled HTTP error response. Secrets Manager and alarms are billable resources; the existing USD 30 budget remains unchanged.

## Recovery and scope limits

RDS automated backups retain seven days and report a latest restorable time. No restore drill or new recovery instance was created during this migration. Templates preserve infrastructure configuration; backups preserve actual database contents.

For recovery:

1. Check RDS automated backups and the available recovery window in AWS.
2. Restore into a **new, separate instance**. Keep the current instance and its protection settings.
3. Verify schema, data, location isolation, and affected workflows on the restored instance.
4. Prepare a reviewed cutover for database endpoint/secret changes and Lambda environment refresh.
5. Adopt the restored instance with revised identifiers after successful validation; do not replace the live Database resource through the routine deployment tool.

The templates are an adopted baseline for this existing account, not an unattended new-account installer. AWS-managed KMS keys, default parameter/option groups, operator credentials, SAM's managed packaging stack, and implicit default-VPC relationships remain AWS/account dependencies. CloudFormation cannot import the existing VPC gateway attachment or implicit main-route associations; these are recorded dependencies. The named default security group and physical IDs must be reviewed for any fresh environment rather than copied blindly.

Database schema migrations, restricted database role grants, and store records remain versioned application/admin operations. They are not deployed as CloudFormation resources, and ordinary infrastructure deployment does not create/reset stores. Custom domains, new authentication models, cross-region recovery, and a separate test environment are future features, not existing resources left unmigrated.

## Validation

- All infrastructure templates were validated by CloudFormation; the API template passed SAM lint validation.
- All five application stacks passed final drift detection with `IN_SYNC`; a fresh infrastructure plan found no remaining changes.
- API package tests passed; the ordinary SAM deployment path was exercised using managed-secret configuration.
- Offline admin launcher and infrastructure safety checks passed.
- Post-change checks verified RDS availability, seven-day retention/deletion protection, API health, store-token enforcement, schema version 23, and both live/TEST store entries.

Use `scripts/Test-Infrastructure.ps1` and `scripts/Test-AdminLauncher.ps1` for offline checks. `Release.ps1` remains unchanged.
