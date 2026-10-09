param(
    [Parameter(Mandatory = $true)]
    [string]$LocationCode,

    [Parameter(Mandatory = $true)]
    [string]$LocationName,

    [Parameter(Mandatory = $true)]
    [string]$LocationUsername,

    [string]$StackName = "esm-operations-api",
    [string]$Region = "ca-central-1",
    [string]$AdminUser = "postgres_admin",
    [string]$PsqlPath = "C:\Program Files\PostgreSQL\18\bin\psql.exe",
    [string]$AwsPath = "C:\Program Files\Amazon\AWSCLIV2\aws.exe",
    [switch]$NoPause,
    [switch]$CreateOnly
)

$ErrorActionPreference = "Stop"

trap {
    Write-Host ""
    Write-Host "Location credential update failed:" -ForegroundColor Red
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
if ($LASTEXITCODE -ne 0) { throw 'Could not read the API stack.' }
$resource = $resourceJson | ConvertFrom-Json
$functionName = $resource.StackResourceDetail.PhysicalResourceId

$configJson = & $AwsPath lambda get-function-configuration `
    --function-name $functionName `
    --region $Region
if ($LASTEXITCODE -ne 0) { throw 'Could not read the API configuration.' }
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
$locationPassword = Convert-SecureStringToPlainText `
    (Read-Host "Password for location username '$LocationUsername'" -AsSecureString)
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
    admin_password_hash TEXT,
    admin_password_salt TEXT,
    admin_password_iterations INTEGER NOT NULL DEFAULT 600000,
    labour_setup_password_hash TEXT,
    labour_setup_password_salt TEXT,
    labour_setup_password_iterations INTEGER NOT NULL DEFAULT 600000,
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
    code,
    name,
    username,
    password_hash,
    password_salt,
    password_iterations,
    active
)
VALUES (
    $(SqlLiteral $LocationCode),
    $(SqlLiteral $LocationName),
    $(SqlLiteral $LocationUsername),
    $(SqlLiteral $password.Hash),
    $(SqlLiteral $password.Salt),
    $($password.Iterations),
    1
)
ON CONFLICT(code)
DO UPDATE SET
    name = EXCLUDED.name,
    username = EXCLUDED.username,
    password_hash = EXCLUDED.password_hash,
    password_salt = EXCLUDED.password_salt,
    password_iterations = EXCLUDED.password_iterations,
    active = 1;

GRANT SELECT, INSERT, UPDATE, DELETE ON
    locations,
    location_sessions
TO $appUser;

GRANT USAGE, SELECT, UPDATE ON
    locations_id_seq,
    location_sessions_id_seq
TO $appUser;

COMMIT;
"@

if ($CreateOnly) {
    # New-store actions must not reset an existing store through the legacy upsert.
    $sql = [regex]::Replace($sql, '(?s)ON CONFLICT\(code\)\s+DO UPDATE SET.*?active = 1;', ';')
    if ($sql -match 'ON CONFLICT\(code\)') { throw 'Could not enable create-only mode. No database changes made.' }
}

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
    if ($LASTEXITCODE -ne 0) { throw 'Store credentials were not saved. Check the database error above.' }
} finally {
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:\PGSSLMODE -ErrorAction SilentlyContinue
}

Write-Host "Location credentials saved for '$LocationCode' using username '$LocationUsername'."

if (-not $NoPause) {
    Read-Host "Press Enter to close"
}
