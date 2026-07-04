$ErrorActionPreference = "Stop"

$ProjectDir = "C:\Food Inventory"
$AppName = "ESM Operations Manager"
$MainJar = "FoodInventory.jar"
$MainClass = "ca.foodinventory.Launcher"
$Icon = "FoodInventory.ico"

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

Write-Host ""
Write-Host "Checking Git status..." -ForegroundColor Yellow
git status

Write-Host ""
Write-Host "Adding files to Git..." -ForegroundColor Yellow
git add .

Write-Host ""
Write-Host "Creating Git commit..." -ForegroundColor Yellow
git commit -m "v$NewVersion - $ReleaseNotes"

Write-Host ""
Write-Host "Pushing to GitHub..." -ForegroundColor Yellow
git push

Write-Host ""
Write-Host "Cleaning Maven project..." -ForegroundColor Yellow
mvn clean

Write-Host ""
Write-Host "Building Maven package..." -ForegroundColor Yellow
mvn package

Write-Host ""
Write-Host "Removing previous installer folder..." -ForegroundColor Yellow
if (Test-Path $InstallerDir) {
    Remove-Item -Recurse -Force $InstallerDir
}

Write-Host ""
Write-Host "Creating Windows installer..." -ForegroundColor Yellow
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

Write-Host ""
Write-Host "Creating Git tag..." -ForegroundColor Yellow
git tag "v$NewVersion"

Write-Host ""
Write-Host "Pushing Git tag..." -ForegroundColor Yellow
git push origin "v$NewVersion"

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

Read-Host "Press ENTER to close"