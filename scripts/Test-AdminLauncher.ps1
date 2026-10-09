# Offline checks: all AWS and PostgreSQL calls are replaced with local test doubles.
$ErrorActionPreference = 'Stop'
$testDirectory = Join-Path ([IO.Path]::GetTempPath()) ('storeops-admin-test-' + [Guid]::NewGuid().ToString())
[IO.Directory]::CreateDirectory($testDirectory) | Out-Null
$fakeAws = Join-Path $testDirectory 'aws.ps1'
$fakePsql = Join-Path $testDirectory 'psql.ps1'
$env:STOREOPS_TEST_SQL = Join-Path $testDirectory 'sql.txt'
@'
$global:LASTEXITCODE = 0
if ($args[0] -eq 'cloudformation') {
    '{"StackResourceDetail":{"PhysicalResourceId":"test-function"}}'
} else {
    '{"Environment":{"Variables":{"FOOD_INVENTORY_API_DB_URL":"jdbc:postgresql://localhost:5432/test","FOOD_INVENTORY_API_DB_USER":"test_app","FOOD_INVENTORY_API_DB_PASSWORD":"test-only"}}}'
}
'@ | Set-Content -LiteralPath $fakeAws
@'
$global:LASTEXITCODE = 0
$index = [array]::IndexOf($args, '--command')
$sql = [string]$args[$index + 1]
[IO.File]::WriteAllText($env:STOREOPS_TEST_SQL, $sql)
if ($sql -match 'json_agg') { '[{"id":7,"code":"TEST","name":"Test Store","username":"test","active":1}]' }
'@ | Set-Content -LiteralPath $fakePsql
function Read-Host {
    param([string]$Prompt, [switch]$AsSecureString)
    if ($AsSecureString) { return ConvertTo-SecureString 'test-only-password' -AsPlainText -Force }
    return 'COPY'
}
try {
    . (Join-Path $PSScriptRoot 'Admin-Launcher.ps1') -CheckOnly
    foreach ($case in @(
        @{ Json='[]'; Expected=0 },
        @{ Json='[{"id":1,"name":"Live Store"}]'; Expected=1 },
        @{ Json='[{"id":1,"name":"Live Store"},{"id":2,"name":"Test Store"}]'; Expected=2 }
    )) {
        $parsed = @(Convert-StoreList $case.Json)
        if ($parsed.Count -ne $case.Expected) { throw 'Store list enumeration failed.' }
        if ($case.Expected -eq 2 -and ($parsed[0].name -ne 'Live Store' -or $parsed[1].name -ne 'Test Store')) {
            throw 'Live and test stores were not separate entries.'
        }
    }
    & (Join-Path $PSScriptRoot 'Admin-Launcher.ps1') -SmokeTest
    $json = & (Join-Path $PSScriptRoot 'List-Locations.ps1') -AwsPath $fakeAws -PsqlPath $fakePsql -AsJson -NoPause
    $stores = @($json | ConvertFrom-Json)
    if ($stores.Count -ne 1 -or $stores[0].name -ne 'Test Store') { throw 'Store JSON did not round-trip.' }
    $values = @{AwsPath=$fakeAws; PsqlPath=$fakePsql; LocationCode="O'Brien"; LocationName='Test Store'; LocationUsername='test'; NoPause=$true}
    & (Join-Path $PSScriptRoot 'Set-LocationCredentials.ps1') @values -CreateOnly
    $sql = [IO.File]::ReadAllText($env:STOREOPS_TEST_SQL)
    if ($sql -match 'ON CONFLICT\(code\)' -or $sql -notmatch "O''Brien") { throw 'Create-only mode or SQL escaping failed.' }
    & (Join-Path $PSScriptRoot 'Set-LocationCredentials.ps1') @values
    if ([IO.File]::ReadAllText($env:STOREOPS_TEST_SQL) -notmatch 'ON CONFLICT\(code\)') { throw 'Legacy password-reset behavior changed.' }
    & (Join-Path $PSScriptRoot 'Copy-LocationSetup.ps1') -SourceLocationId 1 -TargetLocationId 7 -AwsPath $fakeAws -PsqlPath $fakePsql -RequireEmptyTarget -NoPause
    $sql = [IO.File]::ReadAllText($env:STOREOPS_TEST_SQL)
    if ($sql -notmatch 'IF true AND' -or $sql -notmatch 'Target store already has setup data') { throw 'Empty-target protection missing.' }
    Write-Output 'PASS: GUI construction, store selection data, create-only SQL, legacy resets, and copy protection. No AWS or database connection used.'
} finally {
    Remove-Item Env:\STOREOPS_TEST_SQL -ErrorAction SilentlyContinue
    # Keep test artifacts for inspection; they contain only synthetic test credentials.
}
