param(
    [int]$LocationId = 1,

    [Parameter(Mandatory = $true)]
    [string]$NewLocationCode,

    [Parameter(Mandatory = $true)]
    [string]$NewLocationName,

    [Parameter(Mandatory = $true)]
    [string]$NewLocationUsername,

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
    Write-Host "Location rename failed:" -ForegroundColor Red
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

function SqlLiteral {
    param([string]$Value)
    return "'" + ($Value -replace "'", "''") + "'"
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
if ([string]::IsNullOrWhiteSpace($dbUrl)) {
    throw "API Lambda database URL was not found."
}

$uri = [Uri]($dbUrl -replace '^jdbc:', '')
$hostName = $uri.Host
$port = if ($uri.Port -gt 0) { $uri.Port } else { 5432 }
$database = $uri.AbsolutePath.TrimStart('/')

Write-Host "Target RDS host: $hostName"
Write-Host "Target database: $database"
Write-Host "Location id to rename: $LocationId"

$adminPassword = Convert-SecureStringToPlainText `
    (Read-Host "AWS RDS admin password for $AdminUser" -AsSecureString)

$sql = @"
BEGIN;

DO `$`$
BEGIN
    UPDATE locations
    SET
        code = $(SqlLiteral $NewLocationCode),
        name = $(SqlLiteral $NewLocationName),
        username = $(SqlLiteral $NewLocationUsername)
    WHERE id = $LocationId;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'No location found with id %', $LocationId;
    END IF;
END
`$`$;

COMMIT;

SELECT id, code, name, username, active
FROM locations
WHERE id = $LocationId;
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

Write-Host "Location $LocationId renamed to '$NewLocationCode' using username '$NewLocationUsername'."

if (-not $NoPause) {
    Read-Host "Press Enter to close"
}
