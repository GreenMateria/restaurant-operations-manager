param(
    [string]$StackName = "esm-operations-api",
    [string]$Region = "ca-central-1",
    [string]$AdminUser = "postgres_admin",
    [string]$LocationCode = "STORE",
    [string]$LocationName = "Existing Store",
    [string]$LocationUsername = "store",
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

function New-PasswordHash {
    param([string]$Password)

    $iterations = 600000
    $salt = New-Object byte[] 16
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($salt)
    } finally {
        $rng.Dispose()
    }

    $derive = [Security.Cryptography.Rfc2898DeriveBytes]::new(
        $Password,
        $salt,
        $iterations,
        [Security.Cryptography.HashAlgorithmName]::SHA256
    )
    try {
        $hash = $derive.GetBytes(32)
    } finally {
        $derive.Dispose()
    }

    [pscustomobject]@{
        Salt = [Convert]::ToBase64String($salt)
        Hash = [Convert]::ToBase64String($hash)
        Iterations = $iterations
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
$locationPassword = Convert-SecureStringToPlainText `
    (Read-Host "Initial password for location username '$LocationUsername'" -AsSecureString)
$password = New-PasswordHash $locationPassword

$sql = @"
BEGIN;

CREATE TABLE IF NOT EXISTS locations (
    id SERIAL PRIMARY KEY,
    code TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT,
    password_salt TEXT,
    password_iterations INTEGER NOT NULL DEFAULT 600000,
    active INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS location_sessions (
    id SERIAL PRIMARY KEY,
    location_id INTEGER NOT NULL REFERENCES locations(id),
    token_hash TEXT NOT NULL UNIQUE,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TEXT NOT NULL,
    revoked INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_location_sessions_location
ON location_sessions(location_id);

CREATE INDEX IF NOT EXISTS idx_location_sessions_token_active
ON location_sessions(token_hash, revoked, expires_at);

INSERT INTO locations (
    id,
    code,
    name,
    username,
    password_hash,
    password_salt,
    password_iterations,
    active
)
VALUES (
    1,
    $(SqlLiteral $LocationCode),
    $(SqlLiteral $LocationName),
    $(SqlLiteral $LocationUsername),
    $(SqlLiteral $password.Hash),
    $(SqlLiteral $password.Salt),
    $($password.Iterations),
    1
)
ON CONFLICT(id)
DO UPDATE SET
    code = EXCLUDED.code,
    name = EXCLUDED.name,
    username = EXCLUDED.username,
    password_hash = EXCLUDED.password_hash,
    password_salt = EXCLUDED.password_salt,
    password_iterations = EXCLUDED.password_iterations,
    active = 1;

SELECT setval(
    pg_get_serial_sequence('locations', 'id'),
    COALESCE((SELECT MAX(id) FROM locations), 1),
    true
);

DELETE FROM schema_version;
INSERT INTO schema_version (version) VALUES (18);

GRANT SELECT, INSERT, UPDATE, DELETE ON
    locations,
    location_sessions,
    schema_version
TO $appUser;

GRANT USAGE, SELECT, UPDATE ON
    locations_id_seq,
    location_sessions_id_seq
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

    & $PsqlPath `
        --host $hostName `
        --port $port `
        --username $AdminUser `
        --dbname $database `
        --set ON_ERROR_STOP=1 `
        --tuples-only `
        --no-align `
        --command "SELECT current_database(), to_regclass('public.locations'), to_regclass('public.location_sessions'), (SELECT version FROM schema_version LIMIT 1);"
} finally {
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:\PGSSLMODE -ErrorAction SilentlyContinue
}

Write-Host "Location authentication schema is applied. Location '$LocationUsername' is ready."
