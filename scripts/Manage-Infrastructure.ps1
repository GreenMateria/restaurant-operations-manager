param(
    [ValidateSet('Plan','Apply','Status')][string]$Action='Status',
    [switch]$ApproveCurrentPlan,
    [switch]$NoPause
)
$ErrorActionPreference='Stop'
$repo=Split-Path -Parent $PSScriptRoot
$infra=Join-Path $repo 'infra'
$state=Join-Path $repo '.admin'
$manifest=Get-Content -LiteralPath (Join-Path $infra 'environments.json') -Raw | ConvertFrom-Json
$aws='C:\Program Files\Amazon\AWSCLIV2\aws.exe'
$region=$manifest.Region
$planPointer=Join-Path $state 'latest-infrastructure-plan.json'
function AwsJson([string[]]$Arguments) {
    $output=& $aws @Arguments --output json --no-cli-pager
    if ($LASTEXITCODE -ne 0) { throw "AWS $($Arguments[0]) $($Arguments[1]) failed. Review the error above." }
    if ($output) { return ($output | ConvertFrom-Json) }
}
function Assert-SafeChangeSet($changeSet) {
    foreach ($change in $changeSet.Changes) {
        $resource=$change.ResourceChange
        $safeRetentionChange=$false
        if ($resource.Replacement -eq 'Conditional' -and $resource.ResourceType -eq 'AWS::RDS::DBInstance' -and $resource.LogicalResourceId -eq 'Database') {
            $targets=@($resource.Details | Where-Object { $_.Target.RequiresRecreation -ne 'Never' })
            if ($targets.Count -gt 0 -and @($targets | Where-Object { $_.Target.Name -ne 'BackupRetentionPeriod' -or $_.Evaluation -ne 'Static' }).Count -eq 0) {
                $live=AwsJson @('rds','describe-db-instances','--db-instance-identifier',$resource.PhysicalResourceId,'--region',$region)
                $desired=Get-Content -LiteralPath (Join-Path $infra 'infrastructure.template.json') -Raw | ConvertFrom-Json
                # Positive-to-positive retention changes are in-place. The stack
                # policy also independently forbids replacing/deleting Database.
                $safeRetentionChange=($resource.PhysicalResourceId -eq $desired.Resources.Database.Properties.DBInstanceIdentifier -and $live.DBInstances[0].BackupRetentionPeriod -gt 0 -and $desired.Resources.Database.Properties.BackupRetentionPeriod -gt 0)
            }
        }
        if ($resource.Action -eq 'Remove' -or $resource.Replacement -eq 'True' -or ($resource.Replacement -eq 'Conditional' -and -not $safeRetentionChange)) {
            throw "Automatic deployment refused: $($resource.LogicalResourceId) could be removed or replaced. Review its template explicitly."
        }
    }
}
function Wait-ChangeSet([string]$stack,[string]$name) {
    for ($attempt=0; $attempt -lt 90; $attempt++) {
        $changeSet=AwsJson @('cloudformation','describe-change-set','--stack-name',$stack,'--change-set-name',$name,'--region',$region)
        if ($changeSet.Status -eq 'CREATE_COMPLETE') { return $changeSet }
        if ($changeSet.Status -eq 'FAILED') {
            if ($changeSet.StatusReason -match "didn't contain changes|No updates are to be performed") { return $null }
            throw "Plan failed for ${stack}: $($changeSet.StatusReason)"
        }
        Start-Sleep -Seconds 2
    }
    throw "Timed out waiting for the plan for $stack."
}
try {
    if ((AwsJson @('sts','get-caller-identity')).Account -ne $manifest.AccountId) { throw 'AWS account does not match the environment manifest.' }
    if ($Action -eq 'Status') {
        $names=@($manifest.Stacks | ForEach-Object { $_.Name }) + @($manifest.ApiStack)
        foreach ($name in $names) {
            AwsJson @('cloudformation','describe-stacks','--stack-name',$name,'--region',$region,'--query','Stacks[].{Name:StackName,Status:StackStatus,TerminationProtection:EnableTerminationProtection}') | Format-Table
        }
        AwsJson @('cloudwatch','describe-alarms','--alarm-name-prefix','storeops-','--region',$region,'--query','MetricAlarms[].{Name:AlarmName,State:StateValue}') | Format-Table
    } elseif ($Action -eq 'Plan') {
        [IO.Directory]::CreateDirectory($state) | Out-Null
        $stamp=Get-Date -Format 'yyyyMMddHHmmss'
        $plans=New-Object Collections.Generic.List[object]
        $existing=AwsJson @('cloudformation','list-stacks','--region',$region)
        foreach ($entry in $manifest.Stacks) {
            $templatePath=Join-Path $infra $entry.Template
            AwsJson @('cloudformation','validate-template','--template-body',('file://'+$templatePath),'--region',$region) | Out-Null
            $summary=$existing.StackSummaries | Where-Object { $_.StackName -eq $entry.Name -and $_.StackStatus -ne 'DELETE_COMPLETE' }
            if ($summary -and $summary.StackStatus -notin @('CREATE_COMPLETE','UPDATE_COMPLETE','IMPORT_COMPLETE','UPDATE_ROLLBACK_COMPLETE')) { throw "$($entry.Name) is not ready for an update." }
            $type=if ($summary) { 'UPDATE' } else { 'CREATE' }
            $args=@('cloudformation','create-change-set','--stack-name',$entry.Name,'--change-set-name',"infra-$stamp",
                '--change-set-type',$type,'--template-body',('file://'+$templatePath),'--capabilities','CAPABILITY_NAMED_IAM','--region',$region)
            if ($summary) {
                $current=AwsJson @('cloudformation','describe-stacks','--stack-name',$entry.Name,'--region',$region)
                $parameters=@($current.Stacks[0].Parameters | Where-Object { $_ -and $_.ParameterKey } | ForEach-Object { @{ParameterKey=$_.ParameterKey; UsePreviousValue=$true} })
                if ($parameters.Count -gt 0) {
                    $paramPath=Join-Path $state ($entry.Name+'-'+$stamp+'.parameters.json')
                    [IO.File]::WriteAllText($paramPath,(ConvertTo-Json -InputObject $parameters -Depth 10),(New-Object Text.UTF8Encoding($false)))
                    $args+=@('--parameters',('file://'+$paramPath))
                }
            }
            AwsJson $args | Out-Null
            $changes=Wait-ChangeSet $entry.Name "infra-$stamp"
            if ($null -eq $changes) { Write-Host "$($entry.Name): already matches its template."; continue }
            Assert-SafeChangeSet $changes
            Write-Host "$($entry.Name):" -ForegroundColor Cyan
            $changes.Changes | ForEach-Object { $_.ResourceChange } | Select-Object Action,LogicalResourceId,Replacement | Format-Table
            $plans.Add(@{Stack=$entry.Name; ChangeSet="infra-$stamp"; Type=$type; Template=$entry.Template; Hash=(Get-FileHash -LiteralPath $templatePath -Algorithm SHA256).Hash})
        }
        $budgetPath=Join-Path $infra 'budget.json'
        $desired=Get-Content -LiteralPath $budgetPath -Raw | ConvertFrom-Json
        $current=AwsJson @('budgets','describe-budget','--account-id',$manifest.AccountId,'--budget-name',$desired.Budget.BudgetName,'--region','us-east-1')
        $budgetChanged=([decimal]$current.Budget.BudgetLimit.Amount -ne [decimal]$desired.Budget.BudgetLimit.Amount -or $current.Budget.BudgetLimit.Unit -ne $desired.Budget.BudgetLimit.Unit)
        Write-Host "Monthly budget: $($desired.Budget.BudgetLimit.Amount) $($desired.Budget.BudgetLimit.Unit); limit change needed: $budgetChanged. Existing notifications are preserved."
        $plan=@{Created=(Get-Date -Format o); Account=$manifest.AccountId; Region=$region; Stacks=$plans.ToArray(); BudgetChanged=$budgetChanged; BudgetHash=(Get-FileHash -LiteralPath $budgetPath -Algorithm SHA256).Hash}
        $planPath=Join-Path $state ("infrastructure-plan-$stamp.json")
        [IO.File]::WriteAllText($planPath,($plan | ConvertTo-Json -Depth 20),(New-Object Text.UTF8Encoding($false)))
        Copy-Item -LiteralPath $planPath -Destination $planPointer
        Write-Host 'Plan saved. Use Apply reviewed infrastructure changes to apply it.' -ForegroundColor Green
    } else {
        if (-not (Test-Path -LiteralPath $planPointer)) { throw 'Plan infrastructure changes first.' }
        $plan=Get-Content -LiteralPath $planPointer -Raw | ConvertFrom-Json
        if ($plan.Account -ne $manifest.AccountId -or $plan.Region -ne $region) { throw 'Plan environment differs from current configuration. Generate a new plan.' }
        if (((Get-Date)-[DateTime]$plan.Created).TotalHours -gt 24) { throw 'Plan is older than 24 hours. Generate a fresh plan.' }
        foreach ($entry in $plan.Stacks) {
            if ((Get-FileHash -LiteralPath (Join-Path $infra $entry.Template) -Algorithm SHA256).Hash -ne $entry.Hash) { throw 'A template changed after planning. Generate a fresh plan.' }
            $changes=AwsJson @('cloudformation','describe-change-set','--stack-name',$entry.Stack,'--change-set-name',$entry.ChangeSet,'--region',$region)
            if ($changes.Status -ne 'CREATE_COMPLETE' -or $changes.ExecutionStatus -ne 'AVAILABLE') { throw 'Plan is no longer available. Generate a new plan.' }
            Assert-SafeChangeSet $changes
        }
        if ((Get-FileHash -LiteralPath (Join-Path $infra 'budget.json') -Algorithm SHA256).Hash -ne $plan.BudgetHash) { throw 'Budget changed after planning. Generate a new plan.' }
        if (-not $ApproveCurrentPlan -and (Read-Host 'Type APPLY to apply the reviewed infrastructure plan') -ne 'APPLY') { Write-Host 'Cancelled.'; return }
        foreach ($entry in $plan.Stacks) {
            Write-Host "Applying $($entry.Stack)..." -ForegroundColor Cyan
            AwsJson @('cloudformation','execute-change-set','--stack-name',$entry.Stack,'--change-set-name',$entry.ChangeSet,'--region',$region) | Out-Null
            $wait=if ($entry.Type -eq 'CREATE') { 'stack-create-complete' } else { 'stack-update-complete' }
            AwsJson @('cloudformation','wait',$wait,'--stack-name',$entry.Stack,'--region',$region) | Out-Null
            AwsJson @('cloudformation','update-termination-protection','--stack-name',$entry.Stack,'--enable-termination-protection','--region',$region) | Out-Null
            Write-Host "$($entry.Stack): complete." -ForegroundColor Green
        }
        if ($plan.BudgetChanged) {
            AwsJson @('budgets','update-budget','--cli-input-json',('file://'+(Join-Path $infra 'budget.json')),'--region','us-east-1') | Out-Null
        }
        foreach ($entry in $manifest.Stacks) {
            AwsJson @('cloudformation','update-termination-protection','--stack-name',$entry.Name,'--enable-termination-protection','--region',$region) | Out-Null
        }
        AwsJson @('cloudformation','update-termination-protection','--stack-name',$manifest.ApiStack,'--enable-termination-protection','--region',$region) | Out-Null
        AwsJson @('cloudformation','set-stack-policy','--stack-name','esm-operations-infrastructure','--stack-policy-body',('file://'+(Join-Path $infra 'database-stack-policy.json')),'--region',$region) | Out-Null
        Write-Host 'Infrastructure changes complete. Existing plans and history remain saved.' -ForegroundColor Green
    }
} catch { Write-Host $_.Exception.Message -ForegroundColor Red; exit 1 }
finally { if (-not $NoPause) { Read-Host 'Press Enter to close' } }
