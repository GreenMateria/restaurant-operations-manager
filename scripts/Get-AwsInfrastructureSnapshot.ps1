param(
    [string]$Region = "ca-central-1",
    [string]$StackName = "esm-operations-api",
    [string]$AccountId = "863819358995",
    [string]$LambdaSecurityGroupId = "sg-09e7c97add719b857",
    [string]$RdsSecurityGroupId = "sg-056216eb5a750ccfa",
    [string[]]$LambdaSubnetIds = @("subnet-02d952055a4433d04", "subnet-05226760382273fff")
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$Aws = "C:\Program Files\Amazon\AWSCLIV2\aws.exe"

if (!(Test-Path $Aws)) {
    $Aws = "aws"
}

function Invoke-AwsJson {
    param([string[]]$Arguments)

    & $Aws @Arguments

    if ($LASTEXITCODE -ne 0) {
        throw "AWS CLI command failed: aws $($Arguments -join ' ')"
    }
}

Write-Host "ESM Operations Manager AWS infrastructure snapshot" -ForegroundColor Cyan
Write-Host "Region: $Region"
Write-Host "Stack:  $StackName"
Write-Host ""

Write-Host "Account identity" -ForegroundColor Yellow
Invoke-AwsJson @("sts", "get-caller-identity")

Write-Host ""
Write-Host "CloudFormation stack summary" -ForegroundColor Yellow
Invoke-AwsJson @(
    "cloudformation", "describe-stacks",
    "--stack-name", $StackName,
    "--region", $Region,
    "--query", "Stacks[].{StackName:StackName,StackStatus:StackStatus,CreationTime:CreationTime,LastUpdatedTime:LastUpdatedTime,Outputs:Outputs,Parameters:Parameters[].{ParameterKey:ParameterKey,ParameterValue:ParameterValue}}"
)

Write-Host ""
Write-Host "CloudFormation stack resources" -ForegroundColor Yellow
Invoke-AwsJson @(
    "cloudformation", "list-stack-resources",
    "--stack-name", $StackName,
    "--region", $Region,
    "--query", "StackResourceSummaries[].{LogicalResourceId:LogicalResourceId,PhysicalResourceId:PhysicalResourceId,ResourceType:ResourceType,ResourceStatus:ResourceStatus}"
)

Write-Host ""
Write-Host "RDS instances" -ForegroundColor Yellow
Invoke-AwsJson @(
    "rds", "describe-db-instances",
    "--region", $Region,
    "--query", "DBInstances[].{DBInstanceIdentifier:DBInstanceIdentifier,DBInstanceClass:DBInstanceClass,Engine:Engine,EngineVersion:EngineVersion,DBInstanceStatus:DBInstanceStatus,MultiAZ:MultiAZ,StorageType:StorageType,AllocatedStorage:AllocatedStorage,VpcSecurityGroups:VpcSecurityGroups[].VpcSecurityGroupId,DBSubnetGroup:DBSubnetGroup.DBSubnetGroupName,Endpoint:Endpoint.Address}"
)

Write-Host ""
Write-Host "Lambda security group" -ForegroundColor Yellow
Invoke-AwsJson @(
    "ec2", "describe-security-groups",
    "--region", $Region,
    "--group-ids", $LambdaSecurityGroupId,
    "--query", "SecurityGroups[].{GroupId:GroupId,GroupName:GroupName,VpcId:VpcId,Description:Description,Ingress:IpPermissions,Egress:IpPermissionsEgress}"
)

Write-Host ""
Write-Host "RDS security group" -ForegroundColor Yellow
Invoke-AwsJson @(
    "ec2", "describe-security-groups",
    "--region", $Region,
    "--group-ids", $RdsSecurityGroupId,
    "--query", "SecurityGroups[].{GroupId:GroupId,GroupName:GroupName,VpcId:VpcId,Description:Description,Ingress:IpPermissions,Egress:IpPermissionsEgress}"
)

Write-Host ""
Write-Host "Lambda subnets" -ForegroundColor Yellow
$subnetArgs = @(
    "ec2", "describe-subnets",
    "--region", $Region,
    "--subnet-ids"
) + $LambdaSubnetIds + @(
    "--query", "Subnets[].{SubnetId:SubnetId,VpcId:VpcId,AvailabilityZone:AvailabilityZone,CidrBlock:CidrBlock,MapPublicIpOnLaunch:MapPublicIpOnLaunch,State:State}"
)
Invoke-AwsJson $subnetArgs

Write-Host ""
Write-Host "Budgets" -ForegroundColor Yellow
Invoke-AwsJson @(
    "budgets", "describe-budgets",
    "--account-id", $AccountId,
    "--region", "us-east-1",
    "--query", "Budgets[].{BudgetName:BudgetName,BudgetLimit:BudgetLimit,TimeUnit:TimeUnit,CalculatedSpend:CalculatedSpend,HealthStatus:HealthStatus}"
)
