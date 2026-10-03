param(
    [string]$ApiDirectory = "api",
    [switch]$Guided,
    [switch]$SkipTests,
    [switch]$NoPause
)

$ErrorActionPreference = "Stop"

trap {
    Write-Host ""
    Write-Host "API deployment failed:" -ForegroundColor Red
    Write-Host $_.Exception.Message
    if (-not $NoPause) {
        Read-Host "Press Enter to close"
    }
    exit 1
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$apiPath = Join-Path $repoRoot $ApiDirectory
$templatePath = Join-Path $apiPath "template.yaml"
$samConfigPath = Join-Path $apiPath "samconfig.toml"

if (-not (Test-Path -LiteralPath $apiPath)) {
    throw "API directory was not found: $apiPath"
}

if (-not (Test-Path -LiteralPath $templatePath)) {
    throw "SAM template was not found: $templatePath"
}

Write-Host "Building API package..." -ForegroundColor Cyan
$mavenArgs = @("-f", (Join-Path $apiPath "pom.xml"))
if ($SkipTests) {
    $mavenArgs += "-DskipTests"
}
$mavenArgs += "package"

& mvn @mavenArgs
if ($LASTEXITCODE -ne 0) {
    throw "Maven package failed with exit code $LASTEXITCODE."
}

Push-Location $apiPath
try {
    if ($Guided -or -not (Test-Path -LiteralPath $samConfigPath)) {
        Write-Host "Deploying API with SAM guided setup..." -ForegroundColor Cyan
        & sam deploy --guided
    } else {
        Write-Host "Deploying API with existing SAM config..." -ForegroundColor Cyan
        & sam deploy
    }

    if ($LASTEXITCODE -ne 0) {
        throw "SAM deploy failed with exit code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}

Write-Host "API deployment complete." -ForegroundColor Green

if (-not $NoPause) {
    Read-Host "Press Enter to close"
}
