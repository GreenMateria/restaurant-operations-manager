param(
    [string]$StackName = "esm-operations-api",
    [string]$Region = "ca-central-1",
    [string]$AdminUser = "postgres_admin",
    [string]$PsqlPath = "C:\Program Files\PostgreSQL\18\bin\psql.exe",
    [string]$AwsPath = "C:\Program Files\Amazon\AWSCLIV2\aws.exe"
)

$ErrorActionPreference = "Stop"

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

$adminPassword = Convert-SecureStringToPlainText `
    (Read-Host "AWS RDS admin password for $AdminUser" -AsSecureString)

$sql = @"
BEGIN;

CREATE TABLE IF NOT EXISTS labour_positions (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    labour_group TEXT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    target_labour_percentage NUMERIC,
    active INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS labour_employees (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    position_id INTEGER NOT NULL REFERENCES labour_positions(id),
    hourly_wage NUMERIC NOT NULL DEFAULT 0,
    tip_pool_eligible INTEGER NOT NULL DEFAULT 0,
    uniform_deduction_applicable INTEGER NOT NULL DEFAULT 0,
    active INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS labour_daily_sales (
    id SERIAL PRIMARY KEY,
    sales_date TEXT NOT NULL UNIQUE,
    net_sales NUMERIC NOT NULL DEFAULT 0,
    tip_out_pool NUMERIC NOT NULL DEFAULT 0,
    finalized INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS labour_daily_entries (
    id SERIAL PRIMARY KEY,
    work_date TEXT NOT NULL,
    employee_id INTEGER NOT NULL REFERENCES labour_employees(id),
    position_id INTEGER NOT NULL REFERENCES labour_positions(id),
    hourly_wage NUMERIC NOT NULL DEFAULT 0,
    shift_1_hours NUMERIC NOT NULL DEFAULT 0,
    shift_2_hours NUMERIC NOT NULL DEFAULT 0,
    employee_name_snapshot TEXT,
    position_name_snapshot TEXT,
    labour_group_snapshot TEXT,
    finalized INTEGER NOT NULL DEFAULT 0,
    UNIQUE(work_date, employee_id)
);

ALTER TABLE labour_daily_entries
ADD COLUMN IF NOT EXISTS employee_name_snapshot TEXT;

ALTER TABLE labour_daily_entries
ADD COLUMN IF NOT EXISTS position_name_snapshot TEXT;

ALTER TABLE labour_daily_entries
ADD COLUMN IF NOT EXISTS labour_group_snapshot TEXT;

CREATE INDEX IF NOT EXISTS idx_labour_positions_group_active
ON labour_positions(labour_group, active);

CREATE INDEX IF NOT EXISTS idx_labour_employees_position_active
ON labour_employees(position_id, active);

CREATE INDEX IF NOT EXISTS idx_labour_daily_entries_date
ON labour_daily_entries(work_date);

INSERT INTO settings (setting_key, setting_value)
VALUES ('labour.default_uniform_deduction', '0.00')
ON CONFLICT(setting_key)
DO UPDATE SET setting_value = settings.setting_value;

DELETE FROM schema_version;
INSERT INTO schema_version (version) VALUES (17);

GRANT SELECT, INSERT, UPDATE, DELETE ON
    labour_positions,
    labour_employees,
    labour_daily_sales,
    labour_daily_entries,
    settings,
    schema_version
TO $appUser;

GRANT USAGE, SELECT, UPDATE ON
    labour_positions_id_seq,
    labour_employees_id_seq,
    labour_daily_sales_id_seq,
    labour_daily_entries_id_seq
TO $appUser;

COMMIT;
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

Write-Host "Labour schema migration applied and app-user grants refreshed."
