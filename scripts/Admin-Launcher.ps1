param([switch]$CheckOnly, [switch]$SmokeTest)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
$repoRoot = Split-Path -Parent $PSScriptRoot
$stateDirectory = Join-Path $repoRoot '.admin'
$settingsPath = Join-Path $stateDirectory 'settings.json'
$historyPath = Join-Path $stateDirectory 'activity.txt'
$settings = @{
    StackName = 'esm-operations-api'; Region = 'ca-central-1'; AdminUser = 'postgres_admin'
    PsqlPath = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'
    AwsPath = 'C:\Program Files\Amazon\AWSCLIV2\aws.exe'
}
if (Test-Path -LiteralPath $settingsPath) {
    $saved = Get-Content -LiteralPath $settingsPath -Raw | ConvertFrom-Json
    foreach ($key in @($settings.Keys)) {
        if ($saved.PSObject.Properties.Name -contains $key) { $settings[$key] = [string]$saved.$key }
    }
}

function Quote-Literal([string]$value) { return "'" + $value.Replace("'", "''") + "'" }

function Convert-StoreList([string]$json) {
    # Windows PowerShell 5.1 emits a JSON array as one pipeline object.
    # Enumerate it explicitly so each dropdown entry represents one store.
    ConvertFrom-Json -InputObject $json | ForEach-Object { $_ }
}

function Get-ScriptCommand([string]$scriptName, [hashtable]$values) {
    $scriptPath = Join-Path $PSScriptRoot $scriptName
    if (-not (Test-Path -LiteralPath $scriptPath)) { throw "Script not found: $scriptName" }
    $command = '& ' + (Quote-Literal $scriptPath)
    foreach ($key in $values.Keys) {
        if ($key -notmatch '^[A-Za-z][A-Za-z0-9]*$') { throw 'Invalid parameter name.' }
        if ($values[$key] -is [bool]) {
            if ($values[$key]) { $command += " -$key" }
        } else { $command += " -$key " + (Quote-Literal ([string]$values[$key])) }
    }
    return $command
}

function Write-Activity([string]$message) {
    [IO.Directory]::CreateDirectory($stateDirectory) | Out-Null
    $line = (Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + '  ' + $message
    Add-Content -LiteralPath $historyPath -Value $line -Encoding UTF8
    $activity.AppendText($line + [Environment]::NewLine)
}

function Show-Error($failure) {
    [Windows.Forms.MessageBox]::Show([string]$failure, 'StoreOps Admin Tools', 'OK', 'Error') | Out-Null
}

function New-GuideDialog {
    $guidePath = Join-Path $repoRoot 'docs\ADMIN_QUICK_GUIDE.txt'
    if (-not (Test-Path -LiteralPath $guidePath)) { throw "Guide file not found: $guidePath" }
    $dialog = New-Object Windows.Forms.Form
    $dialog.Text = 'StoreOps Admin Guide'
    $dialog.Size = New-Object Drawing.Size(820, 720)
    $dialog.MinimumSize = New-Object Drawing.Size(620, 450)
    $dialog.StartPosition = 'CenterParent'
    $text = New-Object Windows.Forms.TextBox
    $text.Multiline = $true; $text.ReadOnly = $true; $text.ScrollBars = 'Vertical'
    $text.WordWrap = $true; $text.Dock = 'Fill'
    $text.Font = New-Object Drawing.Font('Segoe UI', 11)
    $text.BackColor = [Drawing.Color]::White
    $text.Text = (Get-Content -LiteralPath $guidePath -Raw) -replace '\r?\n', "`r`n"
    $text.SelectionStart = 0; $text.SelectionLength = 0
    $close = New-Object Windows.Forms.Button
    $close.Text = 'Close guide'; $close.Dock = 'Bottom'; $close.Height = 40
    $close.DialogResult = 'Cancel'; $dialog.CancelButton = $close
    $dialog.Controls.Add($text); $dialog.Controls.Add($close)
    return $dialog
}

function Edit-Fields([string]$title, [System.Collections.IDictionary]$fields) {
    $dialog = New-Object Windows.Forms.Form
    $dialog.Text = $title; $dialog.Size = New-Object Drawing.Size(620, (140 + 55 * $fields.Count))
    $dialog.StartPosition = 'CenterParent'; $dialog.FormBorderStyle = 'FixedDialog'
    $dialog.MaximizeBox = $false; $dialog.MinimizeBox = $false
    $inputs = @{}; $y = 18
    foreach ($label in $fields.Keys) {
        $caption = New-Object Windows.Forms.Label
        $caption.Text = $label; $caption.SetBounds(16, $y, 570, 20)
        $input = New-Object Windows.Forms.TextBox
        $input.Text = [string]$fields[$label]; $input.SetBounds(16, ($y + 21), 570, 25)
        $inputs[$label] = $input; $dialog.Controls.Add($caption); $dialog.Controls.Add($input)
        $y += 55
    }
    $ok = New-Object Windows.Forms.Button
    $ok.Text = 'Continue'; $ok.SetBounds(385, ($y + 10), 95, 30); $ok.DialogResult = 'OK'
    $cancel = New-Object Windows.Forms.Button
    $cancel.Text = 'Cancel'; $cancel.SetBounds(490, ($y + 10), 95, 30); $cancel.DialogResult = 'Cancel'
    $dialog.Controls.Add($ok); $dialog.Controls.Add($cancel)
    $dialog.AcceptButton = $ok; $dialog.CancelButton = $cancel
    try {
        if ($dialog.ShowDialog($form) -ne 'OK') { return $null }
        $result = @{}
        foreach ($label in $fields.Keys) {
            $value = $inputs[$label].Text.Trim()
            if ([string]::IsNullOrWhiteSpace($value)) { throw "$label is required." }
            $result[$label] = $value
        }
        return $result
    } finally { $dialog.Dispose() }
}

function Start-AdminTask([string]$label, [string]$scriptName, [hashtable]$values) {
    if ($script:taskProcess -and -not $script:taskProcess.HasExited) {
        throw 'Finish and close the current task window before starting another task.'
    }
    $command = Get-ScriptCommand $scriptName $values
    # Run scripts in an isolated interactive console: password prompts stay there,
    # and script exit/trap statements cannot close the launcher.
    $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($command))
    $runner = @"
& powershell.exe -NoProfile -EncodedCommand $encoded
`$taskExitCode = `$LASTEXITCODE
Write-Host ''
Write-Host "Task ended (exit code `$taskExitCode). Review the output above."
Read-Host 'Press Enter to close this task window'
exit `$taskExitCode
"@
    $start = New-Object Diagnostics.ProcessStartInfo
    $start.FileName = Join-Path $PSHOME 'powershell.exe'
    $start.Arguments = '-NoProfile -EncodedCommand ' + [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($runner))
    $start.WorkingDirectory = $repoRoot; $start.UseShellExecute = $true
    $script:taskProcess = [Diagnostics.Process]::Start($start)
    Write-Activity "Opened: $label. Check the task window for its result; close it when finished."
}

function Get-StoreArguments {
    return @{ StackName=$settings.StackName; Region=$settings.Region; AdminUser=$settings.AdminUser
        PsqlPath=$settings.PsqlPath; AwsPath=$settings.AwsPath; NoPause=$true }
}

function Get-SelectedStore {
    if ($storeBox.SelectedIndex -lt 0) { throw 'Click Refresh stores, then select a store by name.' }
    return $script:stores[$storeBox.SelectedIndex]
}

function Refresh-Stores {
    if ($script:refreshProcess -and -not $script:refreshProcess.HasExited) { return }
    $values = Get-StoreArguments; $values.Remove('AdminUser'); $values.AsJson = $true
    $command = Get-ScriptCommand 'List-Locations.ps1' $values
    $start = New-Object Diagnostics.ProcessStartInfo
    $start.FileName = Join-Path $PSHOME 'powershell.exe'
    $start.Arguments = '-NoProfile -EncodedCommand ' + [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($command))
    $start.UseShellExecute = $false; $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true
    $script:refreshProcess = [Diagnostics.Process]::Start($start)
    $script:refreshOutput = $script:refreshProcess.StandardOutput.ReadToEndAsync()
    $script:refreshError = $script:refreshProcess.StandardError.ReadToEndAsync()
    $refreshButton.Enabled = $false; $status.Text = 'Loading stores from AWS...'
}

if ($CheckOnly) {
    foreach ($name in @('Deploy-Api.ps1', 'List-Locations.ps1', 'Set-LocationCredentials.ps1', 'Rename-Location.ps1', 'Copy-LocationSetup.ps1')) {
        Get-ScriptCommand $name @{} | Out-Null
    }
    Write-Output 'Launcher dependencies and command construction checked. No AWS operations performed.'
    return
}

$script:stores = @(); $script:storesLoaded = $false; $script:taskProcess = $null; $script:refreshProcess = $null
$form = New-Object Windows.Forms.Form
$form.Text = 'StoreOps Admin Tools'; $form.Size = New-Object Drawing.Size(880, 720)
$form.MinimumSize = $form.Size; $form.StartPosition = 'CenterScreen'
$form.Font = New-Object Drawing.Font('Segoe UI', 10)

function Add-Button([string]$text, [int]$x, [int]$y, [scriptblock]$action) {
    $button = New-Object Windows.Forms.Button
    $button.Text = $text; $button.SetBounds($x, $y, 250, 38)
    $button.Tag = $action
    $button.Add_Click({ try { & $this.Tag } catch { Show-Error $_.Exception.Message } })
    $form.Controls.Add($button); return $button
}

$title = New-Object Windows.Forms.Label
$title.Text = 'Deploy the API and manage stores'; $title.SetBounds(20, 15, 800, 30)
$form.Controls.Add($title)
Add-Button 'Deploy API (usual update)' 20 55 {
    if ([Windows.Forms.MessageBox]::Show('Build, test, and deploy the API using the saved SAM configuration?', 'Deploy API', 'OKCancel') -eq 'OK') {
        Start-AdminTask 'Deploy API' 'Deploy-Api.ps1' @{NoPause=$true}
    }
} | Out-Null
Add-Button 'API guided configuration' 290 55 {
    Start-AdminTask 'Guided API deployment' 'Deploy-Api.ps1' @{Guided=$true; NoPause=$true}
} | Out-Null
Add-Button 'Settings' 560 55 {
    $fields = [ordered]@{}
    foreach ($key in @('StackName','Region','AdminUser','PsqlPath','AwsPath')) { $fields[$key] = $settings[$key] }
    $edited = Edit-Fields 'Admin settings (no passwords stored)' $fields
    if ($null -ne $edited) {
        [IO.Directory]::CreateDirectory($stateDirectory) | Out-Null
        if (Test-Path -LiteralPath $settingsPath) {
            Copy-Item -LiteralPath $settingsPath -Destination (Join-Path $stateDirectory ('settings-' + [Guid]::NewGuid().ToString() + '.json'))
        }
        foreach ($key in @($settings.Keys)) { $settings[$key] = $edited[$key] }
        $settings | ConvertTo-Json | Set-Content -LiteralPath $settingsPath -Encoding UTF8
        $script:stores = @(); $script:storesLoaded=$false; $storeBox.Items.Clear()
        Write-Activity 'Saved settings; previous settings retained. Refresh stores to use the new connection.'
    }
} | Out-Null

$storeBox = New-Object Windows.Forms.ComboBox
$storeBox.DropDownStyle = 'DropDownList'; $storeBox.SetBounds(20, 120, 520, 30)
$form.Controls.Add($storeBox)
$refreshButton = Add-Button 'Refresh stores' 560 115 { Refresh-Stores }
$status = New-Object Windows.Forms.Label
$status.Text = 'Refresh stores, then choose a store by name.'; $status.SetBounds(20, 158, 800, 25)
$form.Controls.Add($status)

Add-Button 'Add a new store' 20 195 {
    if (-not $script:storesLoaded) { throw 'Refresh stores before adding a store so existing store codes can be checked.' }
    $values = Edit-Fields 'Add a new store' ([ordered]@{'Store code'=''; 'Store name'=''; 'Login username'=''})
    if ($null -eq $values) { return }
    if (@($script:stores | Where-Object { $_.code -eq $values['Store code'] -or $_.username -eq $values['Login username'] }).Count -gt 0) {
        throw 'That code or username already belongs to a store. Select it and use Edit or Reset password.'
    }
    $args = Get-StoreArguments
    $args.LocationCode=$values['Store code']; $args.LocationName=$values['Store name']; $args.LocationUsername=$values['Login username']
    $args.CreateOnly=$true
    Start-AdminTask 'Add store' 'Set-LocationCredentials.ps1' $args
} | Out-Null
Add-Button 'Edit selected store' 290 195 {
    $store = Get-SelectedStore
    $values = Edit-Fields 'Edit store details' ([ordered]@{'Store code'=$store.code; 'Store name'=$store.name; 'Login username'=$store.username})
    if ($null -eq $values) { return }
    $args = Get-StoreArguments; $args.LocationId=$store.id
    $args.NewLocationCode=$values['Store code']; $args.NewLocationName=$values['Store name']; $args.NewLocationUsername=$values['Login username']
    Start-AdminTask 'Edit store' 'Rename-Location.ps1' $args
} | Out-Null
Add-Button 'Reset selected store password' 560 195 {
    $store = Get-SelectedStore
    if ([Windows.Forms.MessageBox]::Show("Reset the login password for $($store.name)?", 'Reset store password', 'OKCancel') -ne 'OK') { return }
    $args = Get-StoreArguments; $args.LocationCode=$store.code; $args.LocationName=$store.name; $args.LocationUsername=$store.username
    Start-AdminTask 'Reset store login password' 'Set-LocationCredentials.ps1' $args
} | Out-Null
Add-Button 'Copy setup to selected store' 20 245 {
    $target = Get-SelectedStore
    $picker = New-Object Windows.Forms.Form
    $picker.Text = "Copy setup to $($target.name)"; $picker.Size = New-Object Drawing.Size(650, 230)
    $picker.StartPosition = 'CenterParent'
    $note = New-Object Windows.Forms.Label
    $note.Text = 'Copies setup to an empty store only. Existing setup and history are preserved. Choose source:'
    $note.SetBounds(15,15,600,45); $picker.Controls.Add($note)
    $sourceBox = New-Object Windows.Forms.ComboBox; $sourceBox.DropDownStyle='DropDownList'; $sourceBox.SetBounds(15,65,600,30)
    $sources = @($script:stores | Where-Object { $_.id -ne $target.id })
    foreach ($source in $sources) { $sourceBox.Items.Add("$($source.name) [$($source.code)]") | Out-Null }
    $picker.Controls.Add($sourceBox)
    $ok = New-Object Windows.Forms.Button; $ok.Text='Continue'; $ok.SetBounds(510,115,105,35); $ok.DialogResult='OK'; $picker.Controls.Add($ok)
    try {
        if ($picker.ShowDialog($form) -ne 'OK') { return }
        if ($sourceBox.SelectedIndex -lt 0) { throw 'Select a source store.' }
        $args = Get-StoreArguments; $args.SourceLocationId=$sources[$sourceBox.SelectedIndex].id; $args.TargetLocationId=$target.id
        $args.RequireEmptyTarget=$true
        Start-AdminTask 'Copy store setup (explicit COPY confirmation required)' 'Copy-LocationSetup.ps1' $args
    } finally { $picker.Dispose() }
} | Out-Null
Add-Button 'AWS infrastructure status' 290 245 {
    Start-AdminTask 'Infrastructure status' 'Manage-Infrastructure.ps1' @{Action='Status'; NoPause=$true}
} | Out-Null
Add-Button 'Guide: what to do and when' 560 245 {
    $guide = New-GuideDialog
    try { $guide.ShowDialog($form) | Out-Null } finally { $guide.Dispose() }
} | Out-Null

$maintenanceBox = New-Object Windows.Forms.ComboBox
$maintenanceBox.DropDownStyle='DropDownList'; $maintenanceBox.SetBounds(20, 310, 520, 30)
$maintenance = [ordered]@{
    'Plan infrastructure changes'='Manage-Infrastructure.ps1'
    'Apply reviewed infrastructure changes'='Manage-Infrastructure.ps1'
    'Labour schema foundation'='Apply-LabourSchemaMigration.ps1'
    'Store login schema foundation'='Apply-LocationAuthSchemaMigration.ps1'
    'Labour store separation'='Apply-LabourLocationSchemaMigration.ps1'
    'Operational store separation'='Apply-StoreLocationSchemaMigration.ps1'
    'Store uniqueness constraints'='Apply-LocationUniqueConstraintsMigration.ps1'
    'Store protected passwords'='Apply-LocationProtectedPasswordsMigration.ps1'
    'Labour pay rate history'='Apply-LabourPayRateSchemaMigration.ps1'
    'Create or repair RDS application user'='Create-AwsRdsAppUser.ps1'
    'Local SQLite admin password (legacy)'='Reset-AdminPassword.ps1'
    'Local SQLite labour password (legacy)'='Reset-LabourSetupPassword.ps1'
}
foreach ($label in $maintenance.Keys) { $maintenanceBox.Items.Add($label) | Out-Null }
$form.Controls.Add($maintenanceBox)
Add-Button 'Run advanced maintenance' 560 305 {
    if ($maintenanceBox.SelectedIndex -lt 0) { throw 'Select a maintenance task first.' }
    $label = [string]$maintenanceBox.SelectedItem; $name = $maintenance[$label]
    if ([Windows.Forms.MessageBox]::Show("Run '$label'? These are occasional maintenance tasks, not required for each deployment.", 'Advanced maintenance', 'OKCancel') -ne 'OK') { return }
    $commandInfo = Get-Command (Join-Path $PSScriptRoot $name)
    $args = Get-StoreArguments
    foreach ($key in @($args.Keys)) { if (-not $commandInfo.Parameters.ContainsKey($key)) { $args.Remove($key) } }
    if ($name -eq 'Manage-Infrastructure.ps1') {
        $args.Action=if ($label -eq 'Plan infrastructure changes') { 'Plan' } else { 'Apply' }
    }
    if ($name -eq 'Apply-LocationAuthSchemaMigration.ps1') {
        $store = Get-SelectedStore
        $args.LocationCode=$store.code; $args.LocationName=$store.name; $args.LocationUsername=$store.username
    }
    Start-AdminTask $label $name $args
} | Out-Null

$activity = New-Object Windows.Forms.TextBox
$activity.Multiline=$true; $activity.ReadOnly=$true; $activity.ScrollBars='Vertical'
$activity.SetBounds(20,370,790,260); $activity.Anchor='Top,Bottom,Left,Right'
if (Test-Path -LiteralPath $historyPath) { $activity.Text = (Get-Content -LiteralPath $historyPath -Tail 100) -join [Environment]::NewLine }
$form.Controls.Add($activity)
$timer = New-Object Windows.Forms.Timer; $timer.Interval=400
$timer.Add_Tick({
    if ($script:refreshProcess -and $script:refreshProcess.HasExited -and $script:refreshOutput.IsCompleted -and $script:refreshError.IsCompleted) {
        try {
            $output = $script:refreshOutput.Result; $failure = $script:refreshError.Result
            if ($script:refreshProcess.ExitCode -ne 0) { throw "Could not load stores. $failure $output" }
            $script:stores = @(Convert-StoreList $output)
            $script:storesLoaded=$true
            $selectedId = if ($storeBox.SelectedIndex -ge 0) { $storeBox.SelectedItem } else { $null }
            $storeBox.Items.Clear()
            foreach ($store in $script:stores) { $storeBox.Items.Add("$($store.name) [$($store.code)] - $($store.username)") | Out-Null }
            if ($null -ne $selectedId) { $storeBox.SelectedIndex = $storeBox.Items.IndexOf($selectedId) }
            $status.Text = "Loaded $($script:stores.Count) stores. Select a store by name."
            Write-Activity 'Refreshed store list.'
        } catch { $status.Text='Store refresh failed. Check AWS access and settings.'; Show-Error $_.Exception.Message }
        finally { $script:refreshProcess.Dispose(); $script:refreshProcess=$null; $refreshButton.Enabled=$true }
    }
})
$timer.Start()
$form.Add_FormClosed({ $timer.Stop(); $timer.Dispose() })
try {
    if ($SmokeTest) {
        $guide = New-GuideDialog
        $guide.Dispose()
        Write-Output "Created admin launcher with $($form.Controls.Count) controls; no tasks or AWS operations started."
    } else { [Windows.Forms.Application]::Run($form) }
} finally { $timer.Stop(); $timer.Dispose(); $form.Dispose() }
