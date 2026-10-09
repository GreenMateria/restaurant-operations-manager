param([string]$Region='ca-central-1')
$ErrorActionPreference='Stop'
$aws='C:\Program Files\Amazon\AWSCLIV2\aws.exe'
function AwsJson([string[]]$Arguments) {
    $result=& $aws @Arguments --output json --no-cli-pager
    if ($LASTEXITCODE -ne 0) { throw "AWS $($Arguments[0]) $($Arguments[1]) failed." }
    $result | ConvertFrom-Json
}
if ((AwsJson @('sts','get-caller-identity')).Account -ne '863819358995') { throw 'Wrong AWS account.' }
$resource=AwsJson @('cloudformation','describe-stack-resource','--stack-name','esm-operations-api','--logical-resource-id','FoodInventoryApiFunction','--region',$Region)
$config=AwsJson @('lambda','get-function-configuration','--function-name',$resource.StackResourceDetail.PhysicalResourceId,'--region',$Region)
$values=$config.Environment.Variables
$secret=@{dbUrl=$values.FOOD_INVENTORY_API_DB_URL; dbUser=$values.FOOD_INVENTORY_API_DB_USER; dbPassword=$values.FOOD_INVENTORY_API_DB_PASSWORD; apiKey=$values.FOOD_INVENTORY_API_KEY}
foreach ($value in $secret.Values) { if ([string]::IsNullOrWhiteSpace($value)) { throw 'Current API configuration is incomplete. No credentials changed.' } }
$request=@{
    StackName='esm-operations-secrets'; ChangeSetName=('seed-existing-'+(Get-Date -Format 'yyyyMMddHHmmss'))
    ChangeSetType='CREATE'; TemplateBody=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'secrets.template.json'))
    Parameters=@(@{ParameterKey='ApiCredentialsJson'; ParameterValue=($secret | ConvertTo-Json -Compress)})
}
$temporary=Join-Path ([IO.Path]::GetTempPath()) ('storeops-seed-'+[Guid]::NewGuid().ToString()+'.json')
try {
    # Protect the temporary file before placing credentials in it.
    [IO.File]::WriteAllText($temporary, '')
    $acl=Get-Acl -LiteralPath $temporary
    $acl.SetAccessRuleProtection($true,$false)
    $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule($sid,'FullControl','Allow')))
    Set-Acl -LiteralPath $temporary -AclObject $acl
    [IO.File]::WriteAllText($temporary,($request | ConvertTo-Json -Depth 20),(New-Object Text.UTF8Encoding($false)))
    AwsJson @('cloudformation','create-change-set','--cli-input-json',('file://'+$temporary),'--region',$Region) | Out-Null
    Write-Output $request.ChangeSetName
} finally {
    Remove-Item -LiteralPath $temporary -Force -ErrorAction SilentlyContinue
}
