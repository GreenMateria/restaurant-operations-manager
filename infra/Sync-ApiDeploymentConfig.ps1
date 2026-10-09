param([string]$Region='ca-central-1')
$ErrorActionPreference='Stop'
$repo=Split-Path -Parent $PSScriptRoot
$aws='C:\Program Files\Amazon\AWSCLIV2\aws.exe'
$output=& $aws cloudformation describe-stacks --stack-name esm-operations-api --region $Region --output json --no-cli-pager
if ($LASTEXITCODE -ne 0) { throw 'Could not read deployed API settings.' }
$stack=($output | ConvertFrom-Json).Stacks[0]
if ($stack.Parameters.ParameterKey -contains 'DbPassword' -or $stack.Parameters.ParameterKey -notcontains 'RuntimeSecretArn') { throw 'Complete managed-secret migration before syncing SAM configuration.' }
$configPath=Join-Path $repo 'api\samconfig.toml'
$state=Join-Path $repo '.admin'
[IO.Directory]::CreateDirectory($state) | Out-Null
if (Test-Path -LiteralPath $configPath) {
    # Preserve the previous config encrypted to this Windows user because it
    # can contain legacy plaintext credentials. Never print its contents.
    $encrypted=ConvertFrom-SecureString (ConvertTo-SecureString ([IO.File]::ReadAllText($configPath)) -AsPlainText -Force)
    $backup=Join-Path $state ('samconfig-'+(Get-Date -Format 'yyyyMMddHHmmss')+'.encrypted')
    [IO.File]::WriteAllText($backup,$encrypted)
}
$lines=@('version = 0.1','','[default.deploy.parameters]',
    'stack_name = "esm-operations-api"',('region = "'+$Region+'"'),
    'capabilities = "CAPABILITY_IAM"','resolve_s3 = true','confirm_changeset = true',
    'fail_on_empty_changeset = false','parameter_overrides = [')
foreach ($parameter in $stack.Parameters) {
    $value=([string]$parameter.ParameterValue).Replace('\','\\').Replace('"','\"')
    $lines+='  "'+$parameter.ParameterKey+'='+$value+'",'
}
$lines+=']'
[IO.File]::WriteAllText($configPath,($lines -join "`r`n"),(New-Object Text.UTF8Encoding($false)))
Write-Host 'SAM configuration now references the managed secret. Previous configuration retained as an encrypted backup.'
