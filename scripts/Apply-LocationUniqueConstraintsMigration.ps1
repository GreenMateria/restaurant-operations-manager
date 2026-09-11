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
    Write-Host "Location unique-constraint migration failed:" -ForegroundColor Red
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

DO `$`$
DECLARE
    missing_columns text;
BEGIN
    SELECT string_agg(table_name, ', ' ORDER BY table_name)
    INTO missing_columns
    FROM (VALUES
        ('products'),
        ('product_sku_aliases'),
        ('sales_periods'),
        ('alcohol_sales_mappings'),
        ('production_stations'),
        ('production_items'),
        ('production_profiles'),
        ('pos_menu_items'),
        ('production_weeks')
    ) AS required_tables(table_name)
    WHERE NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = required_tables.table_name
          AND column_name = 'location_id'
    );

    IF missing_columns IS NOT NULL THEN
        RAISE EXCEPTION
            'Missing location_id on tables: %. Run Apply-StoreLocationSchemaMigration.ps1, or option 5 in Manage-Locations.ps1, before this migration.',
            missing_columns;
    END IF;
END
`$`$;

DO `$`$
DECLARE
    constraint_record record;
    constraint_target record;
BEGIN
    FOR constraint_target IN
        SELECT *
        FROM (VALUES
            ('products', 'UNIQUE (sku)'),
            ('product_sku_aliases', 'UNIQUE (sku)'),
            ('sales_periods', 'UNIQUE (period_start_date, period_end_date)'),
            ('alcohol_sales_mappings', 'UNIQUE (pos_sku)'),
            ('production_stations', 'UNIQUE (name)'),
            ('production_items', 'UNIQUE (name)'),
            ('production_profiles', 'UNIQUE (name)'),
            ('pos_menu_items', 'UNIQUE (pos_sku)'),
            ('production_weeks', 'UNIQUE (week_start_date, week_end_date)')
        ) AS targets(table_name, constraint_definition)
    LOOP
        FOR constraint_record IN
            EXECUTE format(
                'SELECT conname
                 FROM pg_constraint
                 WHERE conrelid = %L::regclass
                   AND contype = ''u''
                   AND pg_get_constraintdef(oid) = %L',
                constraint_target.table_name,
                constraint_target.constraint_definition
            )
        LOOP
            EXECUTE format(
                'ALTER TABLE %I DROP CONSTRAINT %I',
                constraint_target.table_name,
                constraint_record.conname
            );
        END LOOP;
    END LOOP;
END
`$`$;

DO `$`$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'products_location_sku_key'
          AND conrelid = 'products'::regclass
    ) THEN
        ALTER TABLE products
        ADD CONSTRAINT products_location_sku_key UNIQUE (location_id, sku);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'product_sku_aliases_location_sku_key'
          AND conrelid = 'product_sku_aliases'::regclass
    ) THEN
        ALTER TABLE product_sku_aliases
        ADD CONSTRAINT product_sku_aliases_location_sku_key UNIQUE (location_id, sku);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'sales_periods_location_dates_key'
          AND conrelid = 'sales_periods'::regclass
    ) THEN
        ALTER TABLE sales_periods
        ADD CONSTRAINT sales_periods_location_dates_key UNIQUE (location_id, period_start_date, period_end_date);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'alcohol_sales_mappings_location_pos_sku_key'
          AND conrelid = 'alcohol_sales_mappings'::regclass
    ) THEN
        ALTER TABLE alcohol_sales_mappings
        ADD CONSTRAINT alcohol_sales_mappings_location_pos_sku_key UNIQUE (location_id, pos_sku);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'production_stations_location_name_key'
          AND conrelid = 'production_stations'::regclass
    ) THEN
        ALTER TABLE production_stations
        ADD CONSTRAINT production_stations_location_name_key UNIQUE (location_id, name);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'production_items_location_name_key'
          AND conrelid = 'production_items'::regclass
    ) THEN
        ALTER TABLE production_items
        ADD CONSTRAINT production_items_location_name_key UNIQUE (location_id, name);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'production_profiles_location_name_key'
          AND conrelid = 'production_profiles'::regclass
    ) THEN
        ALTER TABLE production_profiles
        ADD CONSTRAINT production_profiles_location_name_key UNIQUE (location_id, name);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'pos_menu_items_location_pos_sku_key'
          AND conrelid = 'pos_menu_items'::regclass
    ) THEN
        ALTER TABLE pos_menu_items
        ADD CONSTRAINT pos_menu_items_location_pos_sku_key UNIQUE (location_id, pos_sku);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'production_weeks_location_dates_key'
          AND conrelid = 'production_weeks'::regclass
    ) THEN
        ALTER TABLE production_weeks
        ADD CONSTRAINT production_weeks_location_dates_key UNIQUE (location_id, week_start_date, week_end_date);
    END IF;
END
`$`$;

DELETE FROM schema_version;
INSERT INTO schema_version (version) VALUES (21);

GRANT SELECT, INSERT, UPDATE, DELETE ON
    products,
    product_sku_aliases,
    sales_periods,
    alcohol_sales_mappings,
    production_stations,
    production_items,
    production_profiles,
    pos_menu_items,
    production_weeks,
    schema_version
TO $appUser;

COMMIT;

SELECT
    (SELECT version FROM schema_version LIMIT 1) AS schema_version,
    EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'products_location_sku_key'
          AND conrelid = 'products'::regclass
    ) AS products_location_sku,
    EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'sales_periods_location_dates_key'
          AND conrelid = 'sales_periods'::regclass
    ) AS sales_periods_location_dates,
    EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'pos_menu_items_location_pos_sku_key'
          AND conrelid = 'pos_menu_items'::regclass
    ) AS pos_menu_items_location_pos_sku;
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

Write-Host ""
Write-Host "Location unique-constraint migration complete." -ForegroundColor Green

if (-not $NoPause) {
    Read-Host "Press Enter to close"
}
