$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$ProjectDir = "C:\Food Inventory"
$AppName = "ESM Operations Manager"
$MainJar = "FoodInventory.jar"
$MainClass = "ca.foodinventory.Launcher"
$Icon = "FoodInventory.ico"
$PomPath = "$ProjectDir\pom.xml"

function Invoke-Step {
    param([string]$Name, [scriptblock]$Command)

    Write-Host ""
    Write-Host "=== $Name ===" -ForegroundColor Yellow

    & $Command

    if ($LASTEXITCODE -ne 0) {
        throw "$Name failed with exit code $LASTEXITCODE."
    }
}

function Remove-FolderSafe {
    param([string]$Path)

    if (Test-Path $Path) {
        Write-Host "Removing: $Path" -ForegroundColor DarkYellow
        Remove-Item -Recurse -Force $Path
    }
}

function Get-PomVersion {
    [xml]$pom = Get-Content $PomPath

    $versionNode = $pom.SelectSingleNode("/*[local-name()='project']/*[local-name()='version']")

    if ($null -eq $versionNode) {
        throw "Could not find project version in pom.xml."
    }

    return $versionNode.InnerText
}

function Set-PomVersion {
    param([string]$Version)

    [xml]$pom = Get-Content $PomPath

    $versionNode = $pom.SelectSingleNode("/*[local-name()='project']/*[local-name()='version']")

    if ($null -eq $versionNode) {
        throw "Could not find project version in pom.xml."
    }

    $versionNode.InnerText = $Version
    $pom.Save($PomPath)
}


function Assert-GitHubCli {
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
        throw "GitHub CLI ('gh') is not installed or is not available in PATH. Install it from https://cli.github.com and reopen PowerShell."
    }

    & gh auth status *> $null

    if ($LASTEXITCODE -ne 0) {
        throw "GitHub CLI is installed but not authenticated. Run: gh auth login"
    }
}

function Test-GitHubReleaseExists {
    param([string]$Tag)

    $previousErrorActionPreference = $ErrorActionPreference
    $nativePreferenceVariableExists =
        Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue

    if ($null -ne $nativePreferenceVariableExists) {
        $previousNativePreference = $PSNativeCommandUseErrorActionPreference
        $PSNativeCommandUseErrorActionPreference = $false
    }

    try {
        $ErrorActionPreference = "Continue"
        & gh release view $Tag --json tagName 1>$null 2>$null
        return $LASTEXITCODE -eq 0
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference

        if ($null -ne $nativePreferenceVariableExists) {
            $PSNativeCommandUseErrorActionPreference = $previousNativePreference
        }
    }
}

try {
    Set-Location $ProjectDir
    Clear-Host

    Write-Host "===================================" -ForegroundColor Cyan
    Write-Host " ESM Operations Manager Release Tool" -ForegroundColor Cyan
    Write-Host "===================================" -ForegroundColor Cyan
    Write-Host ""

    Assert-GitHubCli

    $CurrentVersion = Get-PomVersion

    Write-Host "Current version detected from pom.xml: $CurrentVersion" -ForegroundColor Green
    Write-Host ""

    $NewVersion = Read-Host "New version, example 1.8.3"
    $ReleaseNotes = Read-Host "Release notes, example Added invoice category breakdown"

    $TargetInstallerDir = "$ProjectDir\target\installer"
    $InstallerDir = "$ProjectDir\installer-$NewVersion"
    $ReleaseDir = "$ProjectDir\Releases"
    $ReleaseHistoryPath = "$ReleaseDir\ReleaseHistory.md"
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

    Write-Host ""
    Write-Host "Updating pom.xml version..." -ForegroundColor Yellow
    Set-PomVersion $NewVersion

    Write-Host ""
    Write-Host "Preparing release history..." -ForegroundColor Yellow

    if (!(Test-Path $ReleaseDir)) {
        New-Item -ItemType Directory -Path $ReleaseDir | Out-Null
    }

    if (!(Test-Path $ReleaseHistoryPath)) {
        Set-Content -Path $ReleaseHistoryPath -Value "# Release History`n"
    }

    $ReleaseDate = Get-Date -Format "yyyy-MM-dd HH:mm"

    Add-Content -Path $ReleaseHistoryPath -Value ""
    Add-Content -Path $ReleaseHistoryPath -Value "## v$NewVersion"
    Add-Content -Path $ReleaseHistoryPath -Value "Date: $ReleaseDate"
    Add-Content -Path $ReleaseHistoryPath -Value ""
    Add-Content -Path $ReleaseHistoryPath -Value "- $ReleaseNotes"

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

    $Tag = "v$NewVersion"

    Write-Host ""
    Write-Host "Publishing installer to GitHub Releases..." -ForegroundColor Yellow

    if (Test-GitHubReleaseExists $Tag) {
        Invoke-Step "Upload installer to existing GitHub Release" {
            gh release upload $Tag "$FinalInstallerPath" --clobber
        }

        Invoke-Step "Update GitHub Release details" {
            gh release edit $Tag `
                --title "$AppName $Tag" `
                --notes "$ReleaseNotes"
        }
    }
    else {
        Invoke-Step "Create GitHub Release and upload installer" {
            gh release create $Tag `
                "$FinalInstallerPath" `
                --title "$AppName $Tag" `
                --notes "$ReleaseNotes" `
                --verify-tag
        }
    }

    Invoke-Step "Verify GitHub Release" {
        gh release view $Tag
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
    Write-Host "Release history updated:"
    Write-Host $ReleaseHistoryPath -ForegroundColor Cyan
    Write-Host ""
    Write-Host "GitHub Release published:"
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