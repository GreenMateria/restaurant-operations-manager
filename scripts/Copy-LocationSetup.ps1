param(
    [int]$SourceLocationId,
    [int]$TargetLocationId,
    [string]$StackName = "esm-operations-api",
    [string]$Region = "ca-central-1",
    [string]$AdminUser = "postgres_admin",
    [string]$PsqlPath = "C:\Program Files\PostgreSQL\18\bin\psql.exe",
    [string]$AwsPath = "C:\Program Files\Amazon\AWSCLIV2\aws.exe",
    [switch]$NoPause,
    [switch]$RequireEmptyTarget
)

$ErrorActionPreference = "Stop"

trap {
    Write-Host ""
    Write-Host "Location setup copy failed:" -ForegroundColor Red
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

function Read-RequiredInt {
    param([string]$Prompt)

    do {
        $value = Read-Host $Prompt
        $result = 0
        if ([int]::TryParse($value, [ref]$result) -and $result -gt 0) {
            return $result
        }
        Write-Host "Enter a positive number." -ForegroundColor Yellow
    } while ($true)
}

if ($SourceLocationId -le 0) {
    $SourceLocationId = Read-RequiredInt "Source location id"
}

if ($TargetLocationId -le 0) {
    $TargetLocationId = Read-RequiredInt "Target location id"
}

if ($SourceLocationId -eq $TargetLocationId) {
    throw "Source and target location ids must be different."
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
Write-Host "Copy source location: $SourceLocationId"
Write-Host "Copy target location: $TargetLocationId"
Write-Host ""
Write-Host "This copies setup/master data only. It does not copy invoices, counts, sales history, labour history, or generated production weeks." -ForegroundColor Yellow
$confirm = Read-Host "Type COPY to continue"
if ($confirm -ne "COPY") {
    Write-Host "Copy cancelled." -ForegroundColor Yellow
    exit 0
}

$adminPassword = Convert-SecureStringToPlainText `
    (Read-Host "AWS RDS admin password for $AdminUser" -AsSecureString)

$sql = @"
BEGIN;

DO `$`$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM locations WHERE id = $SourceLocationId) THEN
        RAISE EXCEPTION 'Source location % does not exist.', $SourceLocationId;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM locations WHERE id = $TargetLocationId) THEN
        RAISE EXCEPTION 'Target location % does not exist.', $TargetLocationId;
    END IF;

    IF $(if ($RequireEmptyTarget) { 'true' } else { 'false' }) AND (
        EXISTS (SELECT 1 FROM products WHERE location_id = $TargetLocationId)
        OR EXISTS (SELECT 1 FROM inventory_count_templates WHERE location_id = $TargetLocationId)
        OR EXISTS (SELECT 1 FROM production_items WHERE location_id = $TargetLocationId)
        OR EXISTS (SELECT 1 FROM production_profiles WHERE location_id = $TargetLocationId)
        OR EXISTS (SELECT 1 FROM production_stations WHERE location_id = $TargetLocationId)
        OR EXISTS (SELECT 1 FROM pos_menu_items WHERE location_id = $TargetLocationId)
        OR EXISTS (SELECT 1 FROM labour_positions WHERE location_id = $TargetLocationId)
        OR EXISTS (SELECT 1 FROM labour_employees WHERE location_id = $TargetLocationId)
        OR EXISTS (SELECT 1 FROM alcohol_sales_mappings WHERE location_id = $TargetLocationId)
    ) THEN
        RAISE EXCEPTION 'Target store already has setup data. No entries were changed. Use an empty target store.';
    END IF;
END
`$`$;

-- Products and aliases.
INSERT INTO products (
    location_id, sku, description, category, reporting_category, unit,
    conversion_factor, pack_size, pack_count, last_case_cost,
    last_purchased_date, active
)
SELECT
    $TargetLocationId, sku, description, category, reporting_category, unit,
    conversion_factor, pack_size, pack_count, last_case_cost,
    last_purchased_date, active
FROM products
WHERE location_id = $SourceLocationId
ON CONFLICT(location_id, sku) DO UPDATE SET
    description = excluded.description,
    category = excluded.category,
    reporting_category = excluded.reporting_category,
    unit = excluded.unit,
    conversion_factor = excluded.conversion_factor,
    pack_size = excluded.pack_size,
    pack_count = excluded.pack_count,
    last_case_cost = excluded.last_case_cost,
    last_purchased_date = excluded.last_purchased_date,
    active = excluded.active;

INSERT INTO product_sku_aliases (
    location_id, product_id, supplier, sku, description, pack_size, active
)
SELECT
    $TargetLocationId,
    target_product.id,
    source_alias.supplier,
    source_alias.sku,
    source_alias.description,
    source_alias.pack_size,
    source_alias.active
FROM product_sku_aliases source_alias
JOIN products source_product
  ON source_product.id = source_alias.product_id
 AND source_product.location_id = source_alias.location_id
JOIN products target_product
  ON target_product.location_id = $TargetLocationId
 AND target_product.sku = source_product.sku
WHERE source_alias.location_id = $SourceLocationId
ON CONFLICT(location_id, sku) DO UPDATE SET
    product_id = excluded.product_id,
    supplier = excluded.supplier,
    description = excluded.description,
    pack_size = excluded.pack_size,
    active = excluded.active;

-- Inventory count templates.
INSERT INTO inventory_count_templates (location_id, name, active)
SELECT $TargetLocationId, source_template.name, source_template.active
FROM inventory_count_templates source_template
WHERE source_template.location_id = $SourceLocationId
  AND NOT EXISTS (
      SELECT 1
      FROM inventory_count_templates target_template
      WHERE target_template.location_id = $TargetLocationId
        AND lower(target_template.name) = lower(source_template.name)
  );

DELETE FROM inventory_count_template_lines target_line
USING inventory_count_templates target_template
WHERE target_line.template_id = target_template.id
  AND target_line.location_id = $TargetLocationId
  AND target_template.location_id = $TargetLocationId
  AND EXISTS (
      SELECT 1
      FROM inventory_count_templates source_template
      WHERE source_template.location_id = $SourceLocationId
        AND lower(source_template.name) = lower(target_template.name)
  );

INSERT INTO inventory_count_template_lines (
    location_id, template_id, product_id, section_name, sort_order, count_unit,
    conversion_factor_to_base, display_name, order_guide_case_size, active
)
SELECT
    $TargetLocationId,
    target_template.id,
    target_product.id,
    source_line.section_name,
    source_line.sort_order,
    source_line.count_unit,
    source_line.conversion_factor_to_base,
    source_line.display_name,
    source_line.order_guide_case_size,
    source_line.active
FROM inventory_count_template_lines source_line
JOIN inventory_count_templates source_template
  ON source_template.id = source_line.template_id
 AND source_template.location_id = source_line.location_id
JOIN inventory_count_templates target_template
  ON target_template.location_id = $TargetLocationId
 AND lower(target_template.name) = lower(source_template.name)
JOIN products source_product
  ON source_product.id = source_line.product_id
 AND source_product.location_id = source_line.location_id
JOIN products target_product
  ON target_product.location_id = $TargetLocationId
 AND target_product.sku = source_product.sku
WHERE source_line.location_id = $SourceLocationId;

-- Alcohol setup.
INSERT INTO alcohol_product_profiles (
    location_id, product_id, count_method, container_type, measurement_unit,
    tare_weight, full_content_weight, active
)
SELECT
    $TargetLocationId,
    target_product.id,
    source_profile.count_method,
    source_profile.container_type,
    source_profile.measurement_unit,
    source_profile.tare_weight,
    source_profile.full_content_weight,
    source_profile.active
FROM alcohol_product_profiles source_profile
JOIN products source_product
  ON source_product.id = source_profile.product_id
 AND source_product.location_id = source_profile.location_id
JOIN products target_product
  ON target_product.location_id = $TargetLocationId
 AND target_product.sku = source_product.sku
WHERE source_profile.location_id = $SourceLocationId
ON CONFLICT(product_id) DO UPDATE SET
    count_method = excluded.count_method,
    container_type = excluded.container_type,
    measurement_unit = excluded.measurement_unit,
    tare_weight = excluded.tare_weight,
    full_content_weight = excluded.full_content_weight,
    active = excluded.active;

INSERT INTO alcohol_sales_mappings (
    location_id, pos_sku, pos_item_name, reporting_category, product_id,
    quantity_per_sale, unit, active
)
SELECT
    $TargetLocationId,
    source_mapping.pos_sku,
    source_mapping.pos_item_name,
    source_mapping.reporting_category,
    target_product.id,
    source_mapping.quantity_per_sale,
    source_mapping.unit,
    source_mapping.active
FROM alcohol_sales_mappings source_mapping
JOIN products source_product
  ON source_product.id = source_mapping.product_id
 AND source_product.location_id = source_mapping.location_id
JOIN products target_product
  ON target_product.location_id = $TargetLocationId
 AND target_product.sku = source_product.sku
WHERE source_mapping.location_id = $SourceLocationId
ON CONFLICT(location_id, pos_sku) DO UPDATE SET
    pos_item_name = excluded.pos_item_name,
    reporting_category = excluded.reporting_category,
    product_id = excluded.product_id,
    quantity_per_sale = excluded.quantity_per_sale,
    unit = excluded.unit,
    active = excluded.active;

-- Production setup.
INSERT INTO production_stations (location_id, name, prep_sheet, sort_order, active)
SELECT $TargetLocationId, name, prep_sheet, sort_order, active
FROM production_stations
WHERE location_id = $SourceLocationId
ON CONFLICT(location_id, name) DO UPDATE SET
    prep_sheet = excluded.prep_sheet,
    sort_order = excluded.sort_order,
    active = excluded.active;

INSERT INTO production_items (
    location_id, name, unit, shelf_life, yield_factor, station_id,
    print_order, permanent_override_par, active
)
SELECT
    $TargetLocationId,
    source_item.name,
    source_item.unit,
    source_item.shelf_life,
    source_item.yield_factor,
    target_station.id,
    source_item.print_order,
    source_item.permanent_override_par,
    source_item.active
FROM production_items source_item
LEFT JOIN production_stations source_station
  ON source_station.id = source_item.station_id
 AND source_station.location_id = source_item.location_id
LEFT JOIN production_stations target_station
  ON target_station.location_id = $TargetLocationId
 AND lower(target_station.name) = lower(source_station.name)
WHERE source_item.location_id = $SourceLocationId
ON CONFLICT(location_id, name) DO UPDATE SET
    unit = excluded.unit,
    shelf_life = excluded.shelf_life,
    yield_factor = excluded.yield_factor,
    station_id = excluded.station_id,
    print_order = excluded.print_order,
    permanent_override_par = excluded.permanent_override_par,
    active = excluded.active;

INSERT INTO production_profiles (location_id, name, category, active)
SELECT $TargetLocationId, name, category, active
FROM production_profiles
WHERE location_id = $SourceLocationId
ON CONFLICT(location_id, name) DO UPDATE SET
    category = excluded.category,
    active = excluded.active;

DELETE FROM production_profile_lines target_line
USING production_profiles target_profile
WHERE target_line.profile_id = target_profile.id
  AND target_line.location_id = $TargetLocationId
  AND target_profile.location_id = $TargetLocationId
  AND EXISTS (
      SELECT 1
      FROM production_profiles source_profile
      WHERE source_profile.location_id = $SourceLocationId
        AND lower(source_profile.name) = lower(target_profile.name)
  );

INSERT INTO production_profile_lines (
    location_id, profile_id, production_item_id, quantity_per_sale,
    unit, sort_order, active, yield_factor
)
SELECT
    $TargetLocationId,
    target_profile.id,
    target_item.id,
    source_line.quantity_per_sale,
    source_line.unit,
    source_line.sort_order,
    source_line.active,
    source_line.yield_factor
FROM production_profile_lines source_line
JOIN production_profiles source_profile
  ON source_profile.id = source_line.profile_id
 AND source_profile.location_id = source_line.location_id
JOIN production_profiles target_profile
  ON target_profile.location_id = $TargetLocationId
 AND lower(target_profile.name) = lower(source_profile.name)
JOIN production_items source_item
  ON source_item.id = source_line.production_item_id
 AND source_item.location_id = source_line.location_id
JOIN production_items target_item
  ON target_item.location_id = $TargetLocationId
 AND lower(target_item.name) = lower(source_item.name)
WHERE source_line.location_id = $SourceLocationId;

INSERT INTO pos_menu_items (
    location_id, pos_sku, name, category, production_profile_id, active
)
SELECT
    $TargetLocationId,
    source_item.pos_sku,
    source_item.name,
    source_item.category,
    target_profile.id,
    source_item.active
FROM pos_menu_items source_item
LEFT JOIN production_profiles source_profile
  ON source_profile.id = source_item.production_profile_id
 AND source_profile.location_id = source_item.location_id
LEFT JOIN production_profiles target_profile
  ON target_profile.location_id = $TargetLocationId
 AND lower(target_profile.name) = lower(source_profile.name)
WHERE source_item.location_id = $SourceLocationId
ON CONFLICT(location_id, pos_sku) DO UPDATE SET
    name = excluded.name,
    category = excluded.category,
    production_profile_id = excluded.production_profile_id,
    active = excluded.active;

DELETE FROM production_item_product_mappings target_mapping
USING production_items target_item
WHERE target_mapping.production_item_id = target_item.id
  AND target_mapping.location_id = $TargetLocationId
  AND target_item.location_id = $TargetLocationId
  AND EXISTS (
      SELECT 1
      FROM production_items source_item
      WHERE source_item.location_id = $SourceLocationId
        AND lower(source_item.name) = lower(target_item.name)
  );

INSERT INTO production_item_product_mappings (
    location_id, production_item_id, product_id, quantity_per_unit, unit, active
)
SELECT
    $TargetLocationId,
    target_item.id,
    target_product.id,
    source_mapping.quantity_per_unit,
    source_mapping.unit,
    source_mapping.active
FROM production_item_product_mappings source_mapping
JOIN production_items source_item
  ON source_item.id = source_mapping.production_item_id
 AND source_item.location_id = source_mapping.location_id
JOIN production_items target_item
  ON target_item.location_id = $TargetLocationId
 AND lower(target_item.name) = lower(source_item.name)
JOIN products source_product
  ON source_product.id = source_mapping.product_id
 AND source_product.location_id = source_mapping.location_id
JOIN products target_product
  ON target_product.location_id = $TargetLocationId
 AND target_product.sku = source_product.sku
WHERE source_mapping.location_id = $SourceLocationId;

COMMIT;

SELECT
    (SELECT count(*) FROM products WHERE location_id = $TargetLocationId) AS target_products,
    (SELECT count(*) FROM inventory_count_templates WHERE location_id = $TargetLocationId) AS target_templates,
    (SELECT count(*) FROM production_items WHERE location_id = $TargetLocationId) AS target_production_items,
    (SELECT count(*) FROM pos_menu_items WHERE location_id = $TargetLocationId) AS target_pos_items,
    (SELECT count(*) FROM alcohol_sales_mappings WHERE location_id = $TargetLocationId) AS target_alcohol_mappings;
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
    if ($LASTEXITCODE -ne 0) { throw 'Setup copy failed. Check the database error above.' }
} finally {
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:\PGSSLMODE -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host "Location setup copy complete." -ForegroundColor Green

if (-not $NoPause) {
    Read-Host "Press Enter to close"
}
