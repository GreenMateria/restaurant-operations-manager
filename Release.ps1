$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Invoke-CommandStep {
    param(
        [string]$Name,
        [scriptblock]$Command
    )

    Write-Host ""
    Write-Host $Name -ForegroundColor Yellow

    & $Command

    if ($LASTEXITCODE -ne 0) {
        throw "$Name failed with exit code $LASTEXITCODE."
    }
}

$ProjectDir = "C:\Food Inventory"
$AppName = "ESM Operations Manager"
$MainJar = "FoodInventory.jar"
$MainClass = "ca.foodinventory.Launcher"
$Icon = "FoodInventory.ico"

try {
    Set-Location $ProjectDir

    Clear-Host
    Write-Host "===================================" -ForegroundColor Cyan
    Write-Host " ESM Operations Manager Release Tool" -ForegroundColor Cyan
    Write-Host "===================================" -ForegroundColor Cyan
    Write-Host ""

    $CurrentVersion = Read-Host "Current version, example 1.8.1"
    $NewVersion = Read-Host "New version, example 1.8.2"
    $ReleaseNotes = Read-Host "Release notes, example Added editable invoice import"

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

    Invoke-CommandStep "Checking Git status..." {
        git status
    }

    Invoke-CommandStep "Adding files to Git..." {
        git add .
    }

    Invoke-CommandStep "Creating Git commit..." {
        git commit -m "v$NewVersion - $ReleaseNotes"
    }

    Invoke-CommandStep "Pushing to GitHub..." {
        git push
    }

    Invoke-CommandStep "Cleaning Maven project..." {
        mvn clean
    }

    Invoke-CommandStep "Building Maven package..." {
        mvn package
    }

    Write-Host ""
    Write-Host "Removing previous installer folder..." -ForegroundColor Yellow

    if (Test-Path $InstallerDir) {
        Remove-Item -Recurse -Force $InstallerDir
    }

    Invoke-CommandStep "Creating Windows installer..." {
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

    Invoke-CommandStep "Creating Git tag..." {
        git tag "v$NewVersion"
    }

    Invoke-CommandStep "Pushing Git tag..." {
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
    Write-Host ""
}
catch {
    Write-Host ""
    Write-Host "===================================" -ForegroundColor Red
    Write-Host " Release Failed" -ForegroundColor Red
    Write-Host "===================================" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host ""
}
finally {
    Read-Host "Press ENTER to close"
}