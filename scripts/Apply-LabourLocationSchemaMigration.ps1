param(
    [string]$StackName = "esm-operations-api",
    [string]$Region = "ca-central-1",
    [string]$AdminUser = "postgres_admin",
    [string]$PsqlPath = "C:\Program Files\PostgreSQL\18\bin\psql.exe",
    [string]$AwsPath = "C:\Program Files\Amazon\AWSCLIV2\aws.exe",
    [switch]$NoPause
)

$ErrorActionPreference = "Stop"

trap {
    Write-Host ""
    Write-Host "Labour location schema migration failed:" -ForegroundColor Red
    Write-Host $_.Exception.Message
    if (-not $NoPause) {
        Read-Host "Press Enter to close"
    }
    exit 1
}

function Convert-SecureStringToPlainText {
    param([securestring]$SecureString)

    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureString)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}

if (-not (Test-Path -LiteralPath $PsqlPath)) {
    throw "psql.exe was not found at: $PsqlPath"
}

if (-not (Test-Path -LiteralPath $AwsPath)) {
    throw "aws.exe was not found at: $AwsPath"
}

$resourceJson = & $AwsPath cloudformation describe-stack-resource `
    --stack-name $StackName `
    --logical-resource-id FoodInventoryApiFunction `
    --region $Region
$resource = $resourceJson | ConvertFrom-Json
$functionName = $resource.StackResourceDetail.PhysicalResourceId

$configJson = & $AwsPath lambda get-function-configuration `
    --function-name $functionName `
    --region $Region
$config = $configJson | ConvertFrom-Json
$envs = $config.Environment.Variables

$dbUrl = $envs.FOOD_INVENTORY_API_DB_URL
$appUser = $envs.FOOD_INVENTORY_API_DB_USER

if ([string]::IsNullOrWhiteSpace($dbUrl)) {
    throw "API Lambda database URL was not found."
}

if ([string]::IsNullOrWhiteSpace($appUser)) {
    throw "API Lambda app database user was not found."
}

$uri = [Uri]($dbUrl -replace '^jdbc:', '')
$hostName = $uri.Host
$port = if ($uri.Port -gt 0) { $uri.Port } else { 5432 }
$database = $uri.AbsolutePath.TrimStart('/')

Write-Host "Target RDS host: $hostName"
Write-Host "Target database: $database"
Write-Host "API app user to grant: $appUser"

$adminPassword = Convert-SecureStringToPlainText `
    (Read-Host "AWS RDS admin password for $AdminUser" -AsSecureString)

$sql = @"
BEGIN;

ALTER TABLE labour_positions
ADD COLUMN IF NOT EXISTS location_id INTEGER NOT NULL DEFAULT 1 REFERENCES locations(id);

ALTER TABLE labour_employees
ADD COLUMN IF NOT EXISTS location_id INTEGER NOT NULL DEFAULT 1 REFERENCES locations(id);

ALTER TABLE labour_daily_sales
ADD COLUMN IF NOT EXISTS location_id INTEGER NOT NULL DEFAULT 1 REFERENCES locations(id);

ALTER TABLE labour_daily_entries
ADD COLUMN IF NOT EXISTS location_id INTEGER NOT NULL DEFAULT 1 REFERENCES locations(id);

DO `$`$
DECLARE
    constraint_record record;
BEGIN
    FOR constraint_record IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'labour_positions'::regclass
          AND contype = 'u'
          AND pg_get_constraintdef(oid) = 'UNIQUE (name)'
    LOOP
        EXECUTE format('ALTER TABLE labour_positions DROP CONSTRAINT %I', constraint_record.conname);
    END LOOP;

    FOR constraint_record IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'labour_daily_sales'::regclass
          AND contype = 'u'
          AND pg_get_constraintdef(oid) = 'UNIQUE (sales_date)'
    LOOP
        EXECUTE format('ALTER TABLE labour_daily_sales DROP CONSTRAINT %I', constraint_record.conname);
    END LOOP;

    FOR constraint_record IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'labour_daily_entries'::regclass
          AND contype = 'u'
          AND pg_get_constraintdef(oid) = 'UNIQUE (work_date, employee_id)'
    LOOP
        EXECUTE format('ALTER TABLE labour_daily_entries DROP CONSTRAINT %I', constraint_record.conname);
    END LOOP;
END
`$`$;

DO `$`$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'labour_positions_location_name_key'
          AND conrelid = 'labour_positions'::regclass
    ) THEN
        ALTER TABLE labour_positions
        ADD CONSTRAINT labour_positions_location_name_key UNIQUE (location_id, name);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'labour_daily_sales_location_date_key'
          AND conrelid = 'labour_daily_sales'::regclass
    ) THEN
        ALTER TABLE labour_daily_sales
        ADD CONSTRAINT labour_daily_sales_location_date_key UNIQUE (location_id, sales_date);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'labour_daily_entries_location_date_employee_key'
          AND conrelid = 'labour_daily_entries'::regclass
    ) THEN
        ALTER TABLE labour_daily_entries
        ADD CONSTRAINT labour_daily_entries_location_date_employee_key UNIQUE (location_id, work_date, employee_id);
    END IF;
END
`$`$;

CREATE INDEX IF NOT EXISTS idx_labour_positions_location_active
ON labour_positions(location_id, active);

CREATE INDEX IF NOT EXISTS idx_labour_employees_location_active
ON labour_employees(location_id, active);

CREATE INDEX IF NOT EXISTS idx_labour_daily_sales_location_date
ON labour_daily_sales(location_id, sales_date);

CREATE INDEX IF NOT EXISTS idx_labour_daily_entries_location_date
ON labour_daily_entries(location_id, work_date);

DELETE FROM schema_version;
INSERT INTO schema_version (version) VALUES (19);

GRANT SELECT, INSERT, UPDATE, DELETE ON
    labour_positions,
    labour_employees,
    labour_daily_sales,
    labour_daily_entries,
    schema_version
TO $appUser;

COMMIT;

SELECT
    (SELECT version FROM schema_version LIMIT 1) AS schema_version,
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'labour_positions' AND column_name = 'location_id'
    ) AS labour_positions_location,
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'labour_employees' AND column_name = 'location_id'
    ) AS labour_employees_location,
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'labour_daily_sales' AND column_name = 'location_id'
    ) AS labour_daily_sales_location,
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'labour_daily_entries' AND column_name = 'location_id'
    ) AS labour_daily_entries_location;
"@

$env:PGPASSWORD = $adminPassword
$env:PGSSLMODE = "require"
try {
    & $PsqlPath `
        --host $hostName `
        --port $port `
        --username $AdminUser `
        --dbname $database `
        --set ON_ERROR_STOP=1 `
        --command $sql
} finally {
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:\PGSSLMODE -ErrorAction SilentlyContinue
}

Write-Host "Labour location schema migration is applied."

if (-not $NoPause) {
    Read-Host "Press Enter to close"
}
