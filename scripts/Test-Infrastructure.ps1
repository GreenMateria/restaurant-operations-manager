$ErrorActionPreference='Stop'
$infra=Join-Path (Split-Path -Parent $PSScriptRoot) 'infra'
$region='ca-central-1'
Get-ChildItem -LiteralPath $infra -Filter '*.json' | ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw | ConvertFrom-Json | Out-Null }
$tokens=$null; $errors=$null
$ast=[Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Manage-Infrastructure.ps1'),[ref]$tokens,[ref]$errors)
if ($errors) { throw 'Infrastructure script parse errors.' }
$function=$ast.Find({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Assert-SafeChangeSet'},$true)
. ([scriptblock]::Create($function.Extent.Text))
function AwsJson($Arguments) { @{DBInstances=@(@{BackupRetentionPeriod=$script:liveRetention})} }
$script:liveRetention=1
function Change([string]$action,[string]$replacement,[string]$property='') {
    @{Changes=@(@{ResourceChange=@{
        Action=$action; Replacement=$replacement; ResourceType='AWS::RDS::DBInstance'
        LogicalResourceId='Database'; PhysicalResourceId='esm-operations-db'
        Details=@(@{Target=@{Name=$property; RequiresRecreation='Conditionally'}; Evaluation='Static'})
    }})}
}
function Must-Reject($change) {
    $rejected=$false
    try { Assert-SafeChangeSet $change } catch { $rejected=$true }
    if (-not $rejected) { throw 'Unsafe change was accepted.' }
}
Must-Reject (Change 'Remove' 'False')
Must-Reject (Change 'Modify' 'True')
Must-Reject (Change 'Modify' 'Conditional' 'Engine')
Assert-SafeChangeSet (Change 'Modify' 'False' 'DeletionProtection')
Assert-SafeChangeSet (Change 'Modify' 'Conditional' 'BackupRetentionPeriod')
$script:liveRetention=0
Must-Reject (Change 'Modify' 'Conditional' 'BackupRetentionPeriod')
$script:liveRetention=1
$otherDatabase=Change 'Modify' 'Conditional' 'BackupRetentionPeriod'
$otherDatabase.Changes[0].ResourceChange.PhysicalResourceId='some-other-database'
Must-Reject $otherDatabase
$policy=Get-Content -LiteralPath (Join-Path $infra 'database-stack-policy.json') -Raw | ConvertFrom-Json
if (-not ($policy.Statement | Where-Object { $_.Effect -eq 'Deny' -and $_.Resource -eq 'LogicalResourceId/Database' -and $_.Action -contains 'Update:Replace' -and $_.Action -contains 'Update:Delete' })) { throw 'Database stack policy protection missing.' }
Write-Output 'PASS: JSON templates, removal/replacement protection, safe retention change, zero-retention rejection, database identity checks, and independent stack policy.'
