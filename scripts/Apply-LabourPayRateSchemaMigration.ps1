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
    Write-Host "Labour pay-rate migration failed:" -ForegroundColor Red
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

$adminPassword = Convert-SecureStringToPlainText `
    (Read-Host "AWS RDS admin password for $AdminUser" -AsSecureString)

$sql = @"
BEGIN;

CREATE TABLE IF NOT EXISTS labour_employee_pay_rates (
    id SERIAL PRIMARY KEY,
    location_id INTEGER NOT NULL DEFAULT 1 REFERENCES locations(id),
    employee_id INTEGER NOT NULL REFERENCES labour_employees(id),
    hourly_wage NUMERIC NOT NULL DEFAULT 0,
    effective_date TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(location_id, employee_id, effective_date)
);

CREATE INDEX IF NOT EXISTS idx_labour_employee_pay_rates_lookup
ON labour_employee_pay_rates(location_id, employee_id, effective_date);

INSERT INTO labour_employee_pay_rates (
    location_id, employee_id, hourly_wage, effective_date
)
SELECT location_id, id, hourly_wage, '1900-01-01'
FROM labour_employees
ON CONFLICT(location_id, employee_id, effective_date) DO NOTHING;

UPDATE schema_version
SET version = GREATEST(version, 23);

GRANT SELECT, INSERT, UPDATE, DELETE ON labour_employee_pay_rates TO $appUser;
GRANT USAGE, SELECT, UPDATE ON labour_employee_pay_rates_id_seq TO $appUser;

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

Write-Host "Labour pay-rate migration complete." -ForegroundColor Green

if (-not $NoPause) {
    Read-Host "Press Enter to close"
}
