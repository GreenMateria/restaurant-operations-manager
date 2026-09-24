param(
    [string]$StackName = "esm-operations-api",
    [string]$Region = "ca-central-1",
    [string]$AdminUser = "postgres_admin",
    [string]$PsqlPath = "C:\Program Files\PostgreSQL\18\bin\psql.exe",
    [string]$AwsPath = "C:\Program Files\Amazon\AWSCLIV2\aws.exe"
)

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

function Read-RequiredValue {
    param([string]$Prompt)

    do {
        $value = Read-Host $Prompt
        if (-not [string]::IsNullOrWhiteSpace($value)) {
            return $value.Trim()
        }
        Write-Host "Value is required." -ForegroundColor Yellow
    } while ($true)
}

function Read-LocationId {
    $value = Read-Host "Location id [1]"
    if ([string]::IsNullOrWhiteSpace($value)) {
        return 1
    }

    $locationId = 0
    if (-not [int]::TryParse($value, [ref]$locationId) -or $locationId -lt 1) {
        throw "Location id must be a positive number."
    }

    return $locationId
}

function Invoke-LocationScript {
    param(
        [string]$ScriptName,
        [hashtable]$Arguments
    )

    $path = Join-Path $scriptRoot $ScriptName
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Script was not found: $path"
    }

    & $path @Arguments `
        -StackName $StackName `
        -Region $Region `
        -PsqlPath $PsqlPath `
        -AwsPath $AwsPath `
        -NoPause
}

function Show-Menu {
    Clear-Host
    Write-Host "StoreOps Location Manager"
    Write-Host ""
    Write-Host "1. List stores"
    Write-Host "2. Add store or reset store password"
    Write-Host "3. Rename store"
    Write-Host "4. One-time location login schema setup"
    Write-Host "5. Apply store-owned location_id schema"
    Write-Host "6. Apply location-aware unique constraints"
    Write-Host "7. Copy setup data between stores"
    Write-Host "8. Apply store-scoped protected-password columns"
    Write-Host "9. Exit"
    Write-Host ""
}

do {
    Show-Menu
    $choice = Read-Host "Choose an option"
    if ([string]::IsNullOrWhiteSpace($choice) -and [Console]::IsInputRedirected) {
        break
    }

    try {
        switch ($choice) {
            "1" {
                Invoke-LocationScript `
                    -ScriptName "List-Locations.ps1" `
                    -Arguments @{}
            }
            "2" {
                $code = Read-RequiredValue "Location code"
                $name = Read-RequiredValue "Location name"
                $username = Read-RequiredValue "Location username"

                Invoke-LocationScript `
                    -ScriptName "Set-LocationCredentials.ps1" `
                    -Arguments @{
                        LocationCode = $code
                        LocationName = $name
                        LocationUsername = $username
                        AdminUser = $AdminUser
                    }
            }
            "3" {
                $locationId = Read-LocationId
                $code = Read-RequiredValue "New location code"
                $name = Read-RequiredValue "New location name"
                $username = Read-RequiredValue "New location username"

                Invoke-LocationScript `
                    -ScriptName "Rename-Location.ps1" `
                    -Arguments @{
                        LocationId = $locationId
                        NewLocationCode = $code
                        NewLocationName = $name
                        NewLocationUsername = $username
                        AdminUser = $AdminUser
                    }
            }
            "4" {
                $code = Read-RequiredValue "Initial location code"
                $name = Read-RequiredValue "Initial location name"
                $username = Read-RequiredValue "Initial location username"

                & (Join-Path $scriptRoot "Apply-LocationAuthSchemaMigration.ps1") `
                    -LocationCode $code `
                    -LocationName $name `
                    -LocationUsername $username `
                    -StackName $StackName `
                    -Region $Region `
                    -AdminUser $AdminUser `
                    -PsqlPath $PsqlPath `
                    -AwsPath $AwsPath
            }
            "5" {
                Invoke-LocationScript `
                    -ScriptName "Apply-StoreLocationSchemaMigration.ps1" `
                    -Arguments @{
                        AdminUser = $AdminUser
                    }
            }
            "6" {
                Invoke-LocationScript `
                    -ScriptName "Apply-LocationUniqueConstraintsMigration.ps1" `
                    -Arguments @{
                        AdminUser = $AdminUser
                    }
            }
            "7" {
                $sourceLocationId = Read-RequiredValue "Source location id"
                $targetLocationId = Read-RequiredValue "Target location id"

                Invoke-LocationScript `
                    -ScriptName "Copy-LocationSetup.ps1" `
                    -Arguments @{
                        SourceLocationId = [int]$sourceLocationId
                        TargetLocationId = [int]$targetLocationId
                        AdminUser = $AdminUser
                    }
            }
            "8" {
                Invoke-LocationScript `
                    -ScriptName "Apply-LocationProtectedPasswordsMigration.ps1" `
                    -Arguments @{
                        AdminUser = $AdminUser
                    }
            }
            "9" {
                break
            }
            default {
                Write-Host "Choose 1, 2, 3, 4, 5, 6, 7, 8, or 9." -ForegroundColor Yellow
            }
        }
    } catch {
        Write-Host ""
        Write-Host "Operation failed:" -ForegroundColor Red
        Write-Host $_.Exception.Message
    }

    if ($choice -ne "9") {
        Write-Host ""
        Read-Host "Press Enter to return to the menu"
    }
} while ($choice -ne "9")
