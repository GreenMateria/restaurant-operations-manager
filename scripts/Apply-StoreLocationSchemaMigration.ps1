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
    Write-Host "Store location schema migration failed:" -ForegroundColor Red
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

ALTER TABLE products ADD COLUMN IF NOT EXISTS location_id INTEGER NOT NULL DEFAULT 1 REFERENCES locations(id);
ALTER TABLE product_sku_aliases ADD COLUMN IF NOT EXISTS location_id INTEGER NOT NULL DEFAULT 1 REFERENCES locations(id);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS location_id INTEGER NOT NULL DEFAULT 1 REFERENCES locations(id);
ALTER TABLE invoice_lines ADD COLUMN IF NOT EXISTS location_id INTEGER NOT NULL DEFAULT 1 REFERENCES locations(id);
ALTER TABLE invoice_adjustments ADD COLUMN IF NOT EXISTS location_id INTEGER NOT NULL DEFAULT 1 REFERENCES locations(id);
ALTER TABLE inventory_count_templates ADD COLUMN IF NOT EXISTS location_id INTEGER NOT NULL DEFAULT 1 REFERENCES locations(id);
ALTER TABLE inventory_count_template_lines ADD COLUMN IF NOT EXISTS location_id INTEGER NOT NULL DEFAULT 1 REFERENCES locations(id);
ALTER TABLE inventory_counts ADD COLUMN IF NOT EXISTS location_id INTEGER NOT NULL DEFAULT 1 REFERENCES locations(id);
ALTER TABLE inventory_count_lines ADD COLUMN IF NOT EXISTS location_id INTEGER NOT NULL DEFAULT 1 REFERENCES locations(id);
ALTER TABLE sales_periods ADD COLUMN IF NOT EXISTS location_id INTEGER NOT NULL DEFAULT 1 REFERENCES locations(id);
ALTER TABLE alcohol_product_profiles ADD COLUMN IF NOT EXISTS location_id INTEGER NOT NULL DEFAULT 1 REFERENCES locations(id);
ALTER TABLE alcohol_sales_mappings ADD COLUMN IF NOT EXISTS location_id INTEGER NOT NULL DEFAULT 1 REFERENCES locations(id);
ALTER TABLE production_stations ADD COLUMN IF NOT EXISTS location_id INTEGER NOT NULL DEFAULT 1 REFERENCES locations(id);
ALTER TABLE production_items ADD COLUMN IF NOT EXISTS location_id INTEGER NOT NULL DEFAULT 1 REFERENCES locations(id);
ALTER TABLE production_profiles ADD COLUMN IF NOT EXISTS location_id INTEGER NOT NULL DEFAULT 1 REFERENCES locations(id);
ALTER TABLE production_profile_lines ADD COLUMN IF NOT EXISTS location_id INTEGER NOT NULL DEFAULT 1 REFERENCES locations(id);
ALTER TABLE pos_menu_items ADD COLUMN IF NOT EXISTS location_id INTEGER NOT NULL DEFAULT 1 REFERENCES locations(id);
ALTER TABLE production_item_product_mappings ADD COLUMN IF NOT EXISTS location_id INTEGER NOT NULL DEFAULT 1 REFERENCES locations(id);
ALTER TABLE production_weeks ADD COLUMN IF NOT EXISTS location_id INTEGER NOT NULL DEFAULT 1 REFERENCES locations(id);
ALTER TABLE production_week_days ADD COLUMN IF NOT EXISTS location_id INTEGER NOT NULL DEFAULT 1 REFERENCES locations(id);
ALTER TABLE production_week_lines ADD COLUMN IF NOT EXISTS location_id INTEGER NOT NULL DEFAULT 1 REFERENCES locations(id);

CREATE INDEX IF NOT EXISTS idx_products_location_id ON products(location_id);
CREATE INDEX IF NOT EXISTS idx_product_sku_aliases_location_id ON product_sku_aliases(location_id);
CREATE INDEX IF NOT EXISTS idx_invoices_location_id ON invoices(location_id);
CREATE INDEX IF NOT EXISTS idx_invoice_lines_location_id ON invoice_lines(location_id);
CREATE INDEX IF NOT EXISTS idx_invoice_adjustments_location_id ON invoice_adjustments(location_id);
CREATE INDEX IF NOT EXISTS idx_inventory_count_templates_location_id ON inventory_count_templates(location_id);
CREATE INDEX IF NOT EXISTS idx_inventory_count_template_lines_location_id ON inventory_count_template_lines(location_id);
CREATE INDEX IF NOT EXISTS idx_inventory_counts_location_id ON inventory_counts(location_id);
CREATE INDEX IF NOT EXISTS idx_inventory_count_lines_location_id ON inventory_count_lines(location_id);
CREATE INDEX IF NOT EXISTS idx_sales_periods_location_id ON sales_periods(location_id);
CREATE INDEX IF NOT EXISTS idx_alcohol_product_profiles_location_id ON alcohol_product_profiles(location_id);
CREATE INDEX IF NOT EXISTS idx_alcohol_sales_mappings_location_id ON alcohol_sales_mappings(location_id);
CREATE INDEX IF NOT EXISTS idx_production_stations_location_id ON production_stations(location_id);
CREATE INDEX IF NOT EXISTS idx_production_items_location_id ON production_items(location_id);
CREATE INDEX IF NOT EXISTS idx_production_profiles_location_id ON production_profiles(location_id);
CREATE INDEX IF NOT EXISTS idx_production_profile_lines_location_id ON production_profile_lines(location_id);
CREATE INDEX IF NOT EXISTS idx_pos_menu_items_location_id ON pos_menu_items(location_id);
CREATE INDEX IF NOT EXISTS idx_production_item_product_mappings_location_id ON production_item_product_mappings(location_id);
CREATE INDEX IF NOT EXISTS idx_production_weeks_location_id ON production_weeks(location_id);
CREATE INDEX IF NOT EXISTS idx_production_week_days_location_id ON production_week_days(location_id);
CREATE INDEX IF NOT EXISTS idx_production_week_lines_location_id ON production_week_lines(location_id);

DELETE FROM schema_version;
INSERT INTO schema_version (version) VALUES (20);

GRANT SELECT, INSERT, UPDATE, DELETE ON
    products,
    product_sku_aliases,
    invoices,
    invoice_lines,
    invoice_adjustments,
    inventory_count_templates,
    inventory_count_template_lines,
    inventory_counts,
    inventory_count_lines,
    sales_periods,
    alcohol_product_profiles,
    alcohol_sales_mappings,
    production_stations,
    production_items,
    production_profiles,
    production_profile_lines,
    pos_menu_items,
    production_item_product_mappings,
    production_weeks,
    production_week_days,
    production_week_lines,
    schema_version
TO $appUser;

COMMIT;

SELECT
    (SELECT version FROM schema_version LIMIT 1) AS schema_version,
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'products' AND column_name = 'location_id'
    ) AS products_location,
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'production_weeks' AND column_name = 'location_id'
    ) AS production_weeks_location,
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'sales_periods' AND column_name = 'location_id'
    ) AS sales_periods_location;
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

Write-Host "Store location schema migration is applied."

if (-not $NoPause) {
    Read-Host "Press Enter to close"
}
