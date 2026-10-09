param([string]$Region='ca-central-1')
$ErrorActionPreference='Stop'
$aws='C:\Program Files\Amazon\AWSCLIV2\aws.exe'
function AwsJson([string[]]$Arguments) {
    $result=& $aws @Arguments --output json --no-cli-pager
    if ($LASTEXITCODE -ne 0) { throw "AWS $($Arguments[0]) $($Arguments[1]) failed." }
    $result | ConvertFrom-Json
}
$stack=AwsJson @('cloudformation','describe-stacks','--stack-name','esm-operations-api','--region',$Region)
$secretStack=AwsJson @('cloudformation','describe-stacks','--stack-name','esm-operations-secrets','--region',$Region)
$secretArn=($secretStack.Stacks[0].Outputs | Where-Object OutputKey -eq 'RuntimeSecretArn').OutputValue
if (-not $secretArn) { throw 'Managed secret is not ready.' }
$deployed=AwsJson @('cloudformation','get-template','--stack-name','esm-operations-api','--template-stage','Processed','--region',$Region)
$template=$deployed.TemplateBody
if ($template -is [string]) { $template=$template | ConvertFrom-Json }
foreach ($key in @('DbUrl','DbUser','DbPassword','ApiKey')) { $template.Parameters.PSObject.Properties.Remove($key) }
$template.Parameters | Add-Member -MemberType NoteProperty -Name RuntimeSecretArn -Value @{Type='String'; Description='Managed StoreOps runtime secret ARN'} -Force
$environment=$template.Resources.FoodInventoryApiFunction.Properties.Environment.Variables
$fields=@{FOOD_INVENTORY_API_DB_URL='dbUrl'; FOOD_INVENTORY_API_DB_USER='dbUser'; FOOD_INVENTORY_API_DB_PASSWORD='dbPassword'; FOOD_INVENTORY_API_KEY='apiKey'}
foreach ($key in $fields.Keys) {
    $environment.$key=@{'Fn::Sub'=('{{resolve:secretsmanager:${RuntimeSecretArn}:SecretString:'+$fields[$key]+'}}')}
}
$parameters=@($stack.Stacks[0].Parameters | Where-Object { $_.ParameterKey -notin @('DbUrl','DbUser','DbPassword','ApiKey','RuntimeSecretArn') } | ForEach-Object { @{ParameterKey=$_.ParameterKey; UsePreviousValue=$true} })
$parameters+=@{ParameterKey='RuntimeSecretArn'; ParameterValue=$secretArn}
$directory=Join-Path (Split-Path -Parent $PSScriptRoot) '.admin'
[IO.Directory]::CreateDirectory($directory) | Out-Null
$templatePath=Join-Path $directory 'api-secret-migration.template.json'
$parameterPath=Join-Path $directory 'api-secret-migration.parameters.json'
$encoding=New-Object Text.UTF8Encoding($false)
[IO.File]::WriteAllText($templatePath,($template | ConvertTo-Json -Depth 60),$encoding)
[IO.File]::WriteAllText($parameterPath,($parameters | ConvertTo-Json -Depth 10),$encoding)
$name='managed-secrets-'+(Get-Date -Format 'yyyyMMddHHmmss')
AwsJson @('cloudformation','create-change-set','--stack-name','esm-operations-api','--change-set-name',$name,
    '--change-set-type','UPDATE','--template-body',('file://'+$templatePath),'--parameters',('file://'+$parameterPath),
    '--capabilities','CAPABILITY_IAM','CAPABILITY_AUTO_EXPAND','--region',$Region) | Out-Null
Write-Output $name
