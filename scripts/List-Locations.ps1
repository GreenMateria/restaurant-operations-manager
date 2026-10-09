param(
    [string]$StackName = "esm-operations-api",
    [string]$Region = "ca-central-1",
    [string]$PsqlPath = "C:\Program Files\PostgreSQL\18\bin\psql.exe",
    [string]$AwsPath = "C:\Program Files\Amazon\AWSCLIV2\aws.exe",
    [switch]$NoPause,
    [switch]$AsJson
)

$ErrorActionPreference = "Stop"

trap {
    Write-Host ""
    Write-Host "Location list failed:" -ForegroundColor Red
    Write-Host $_.Exception.Message
    if (-not $NoPause) {
        Read-Host "Press Enter to close"
    }
    exit 1
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
if ($LASTEXITCODE -ne 0) { throw "Unable to read the API stack. Check AWS sign-in and region." }
$resource = $resourceJson | ConvertFrom-Json
$functionName = $resource.StackResourceDetail.PhysicalResourceId

$configJson = & $AwsPath lambda get-function-configuration `
    --function-name $functionName `
    --region $Region
if ($LASTEXITCODE -ne 0) { throw "Unable to read the API database configuration." }
$config = $configJson | ConvertFrom-Json
$envs = $config.Environment.Variables

$dbUrl = $envs.FOOD_INVENTORY_API_DB_URL
$appUser = $envs.FOOD_INVENTORY_API_DB_USER
$appPassword = $envs.FOOD_INVENTORY_API_DB_PASSWORD

if ([string]::IsNullOrWhiteSpace($dbUrl)) {
    throw "API Lambda database URL was not found."
}

if ([string]::IsNullOrWhiteSpace($appUser) -or [string]::IsNullOrWhiteSpace($appPassword)) {
    throw "API Lambda app database credentials were not found."
}

$uri = [Uri]($dbUrl -replace '^jdbc:', '')
$hostName = $uri.Host
$port = if ($uri.Port -gt 0) { $uri.Port } else { 5432 }
$database = $uri.AbsolutePath.TrimStart('/')

if (-not $AsJson) {
    Write-Host "Target RDS host: $hostName"
    Write-Host "Target database: $database"
    Write-Host ""
}

$env:PGPASSWORD = $appPassword
$env:PGSSLMODE = "require"
try {
    if ($AsJson) {
        $sql = "SELECT COALESCE(json_agg(row_to_json(stores)), '[]'::json) FROM (SELECT id, code, name, username, active FROM locations ORDER BY name, code) stores;"
        & $PsqlPath --host $hostName --port $port --username $appUser --dbname $database --set ON_ERROR_STOP=1 --no-psqlrc --tuples-only --no-align --command $sql
    } else {
    & $PsqlPath `
        --host $hostName `
        --port $port `
        --username $appUser `
        --dbname $database `
        --set ON_ERROR_STOP=1 `
        --command "SELECT id, code, name, username, active, created_at FROM locations ORDER BY id;"
    }
    if ($LASTEXITCODE -ne 0) { throw "Unable to load stores from the database." }
} finally {
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:\PGSSLMODE -ErrorAction SilentlyContinue
}

if (-not $NoPause -and -not $AsJson) {
    Read-Host "Press Enter to close"
}
