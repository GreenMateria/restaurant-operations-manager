param(
    [string]$HostName = "esm-operations-db.cdcok68as3cr.ca-central-1.rds.amazonaws.com",
    [string]$Port = "5432",
    [string]$Database = "postgres",
    [string]$AdminUser = "postgres_admin",
    [string]$AppUser = "operations_app",
    [string]$PsqlPath = "C:\Program Files\PostgreSQL\18\bin\psql.exe"
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

function Invoke-Psql {
    param(
        [string]$User,
        [string]$Password,
        [string]$Command
    )

    $env:PGPASSWORD = $Password
    $env:PGSSLMODE = "require"
    try {
        & $PsqlPath `
            --host $HostName `
            --port $Port `
            --username $User `
            --dbname $Database `
            --set ON_ERROR_STOP=1 `
            --command $Command
    } finally {
        Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
        Remove-Item Env:\PGSSLMODE -ErrorAction SilentlyContinue
    }
}

if (-not (Test-Path -LiteralPath $PsqlPath)) {
    throw "psql.exe was not found at: $PsqlPath"
}

$adminPassword = Convert-SecureStringToPlainText `
    (Read-Host "AWS admin password for $AdminUser" -AsSecureString)
$appPassword = Convert-SecureStringToPlainText `
    (Read-Host "Password to set for $AppUser" -AsSecureString)
$escapedAppPassword = $appPassword.Replace("'", "''")

$sql = @"
DO `$esm_setup`$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '$AppUser') THEN
        CREATE ROLE $AppUser LOGIN PASSWORD '$escapedAppPassword';
    ELSE
        ALTER ROLE $AppUser WITH PASSWORD '$escapedAppPassword';
    END IF;
END
`$esm_setup`$;

GRANT CONNECT ON DATABASE $Database TO $AppUser;
GRANT USAGE ON SCHEMA public TO $AppUser;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO $AppUser;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO $AppUser;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO $AppUser;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO $AppUser;
"@

Write-Host "Creating/updating $AppUser and applying grants..."
Invoke-Psql -User $AdminUser -Password $adminPassword -Command $sql

Write-Host "Verifying $AppUser can read application data..."
Invoke-Psql `
    -User $AppUser `
    -Password $appPassword `
    -Command "SELECT COUNT(*) AS products FROM products;"

Write-Host "Done. Update database.properties to use cloud.user=$AppUser after verification."
