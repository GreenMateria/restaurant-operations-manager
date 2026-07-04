$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$ProjectDir = "C:\Food Inventory"
$AppName = "ESM Operations Manager"
$MainJar = "FoodInventory.jar"
$MainClass = "ca.foodinventory.Launcher"
$Icon = "FoodInventory.ico"

function Invoke-Step {
    param(
        [string]$Name,
        [scriptblock]$Command
    )

    Write-Host ""
    Write-Host "=== $Name ===" -ForegroundColor Yellow

    & $Command

    if ($LASTEXITCODE -ne 0) {
        throw "$Name failed with exit code $LASTEXITCODE."
    }
}

function Remove-FolderSafe {
    param(
        [string]$Path
    )

    if (Test-Path $Path) {
        Write-Host "Removing: $Path" -ForegroundColor DarkYellow
        Remove-Item -Recurse -Force $Path
    }
}

try {
    Set-Location $ProjectDir
    Clear-Host

    Write-Host "===================================" -ForegroundColor Cyan
    Write-Host " ESM Operations Manager Release Tool" -ForegroundColor Cyan
    Write-Host "===================================" -ForegroundColor Cyan
    Write-Host ""

    $CurrentVersion = Read-Host "Current version, example 1.8.1"
    $NewVersion = Read-Host "New version, example 1.8.2"
    $ReleaseNotes = Read-Host "Release notes, example Added invoice category breakdown"

    $TargetInstallerDir = "$ProjectDir\target\installer"
    $InstallerDir = "$ProjectDir\installer-$NewVersion"
    $ReleaseDir = "$ProjectDir\Releases"
    $FinalInstallerPath = "$ReleaseDir\$AppName-$NewVersion.exe"

    Write-Host ""
    Write-Host "Release Summary" -ForegroundColor Yellow
    Write-Host "Current Version: $CurrentVersion"
    Write-Host "New Version:     $NewVersion"
    Write-Host "Notes:           $ReleaseNotes"
    Write-Host ""

    $Confirm = Read-Host "Type YES to continue"

    if ($Confirm -ne "YES") {
        Write-Host "Release cancelled." -ForegroundColor Red
        exit
    }

    Write-Host ""
    Write-Host "Closing possible locked app/installer processes..." -ForegroundColor Yellow

    Get-Process | Where-Object {
        $_.ProcessName -like "ESM Operations Manager*" -or
        $_.ProcessName -eq "FoodInventory" -or
        $_.ProcessName -eq "java" -or
        $_.ProcessName -eq "javaw"
    } | ForEach-Object {
        Write-Host "Stopping process: $($_.ProcessName) [$($_.Id)]"
        Stop-Process -Id $_.Id -Force
    }

    Start-Sleep -Seconds 2

    Invoke-Step "Git status" {
        git status
    }

    Invoke-Step "Git add" {
        git add .
    }

    Invoke-Step "Git commit" {
        git commit -m "v$NewVersion - $ReleaseNotes"
    }

    Invoke-Step "Git push" {
        git push
    }

    Write-Host ""
    Write-Host "Cleaning old installer folders before Maven..." -ForegroundColor Yellow
    Remove-FolderSafe $TargetInstallerDir
    Remove-FolderSafe $InstallerDir

    Invoke-Step "Maven clean" {
        mvn clean
    }

    Invoke-Step "Maven package" {
        mvn package
    }

    Invoke-Step "Create Windows installer" {
        jpackage `
            --type exe `
            --name "$AppName" `
            --app-version "$NewVersion" `
            --vendor "ESM" `
            --input "$ProjectDir\target" `
            --main-jar "$MainJar" `
            --main-class "$MainClass" `
            --icon "$ProjectDir\$Icon" `
            --dest "$InstallerDir" `
            --win-dir-chooser `
            --win-menu `
            --win-shortcut
    }

    Write-Host ""
    Write-Host "Copying installer to Releases folder..." -ForegroundColor Yellow

    if (!(Test-Path $ReleaseDir)) {
        New-Item -ItemType Directory -Path $ReleaseDir | Out-Null
    }

    $InstallerFile = Get-ChildItem $InstallerDir -Filter "*.exe" | Select-Object -First 1

    if ($null -eq $InstallerFile) {
        throw "Installer EXE was not created."
    }

    Copy-Item $InstallerFile.FullName $FinalInstallerPath -Force

    Invoke-Step "Git tag" {
        git tag "v$NewVersion"
    }

    Invoke-Step "Push Git tag" {
        git push origin "v$NewVersion"
    }

    Write-Host ""
    Write-Host "===================================" -ForegroundColor Green
    Write-Host " Release Successful!" -ForegroundColor Green
    Write-Host "===================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Installer created:"
    Write-Host $FinalInstallerPath -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Git tag created:"
    Write-Host "v$NewVersion" -ForegroundColor Cyan
}
catch {
    Write-Host ""
    Write-Host "===================================" -ForegroundColor Red
    Write-Host " Release Failed" -ForegroundColor Red
    Write-Host "===================================" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
}
finally {
    Write-Host ""
    Read-Host "Press ENTER to close"
}