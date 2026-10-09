param(
    [string]$Region = 'ca-central-1',
    [string]$StackName = 'esm-operations-infrastructure',
    [string]$DatabaseId = 'esm-operations-db',
    [string]$LambdaSecurityGroupId = 'sg-09e7c97add719b857',
    [switch]$Execute
)
$ErrorActionPreference = 'Stop'
$aws = 'C:\Program Files\Amazon\AWSCLIV2\aws.exe'
function Invoke-Aws([string[]]$Arguments) {
    $result = & $aws @Arguments --output json --no-cli-pager
    if ($LASTEXITCODE -ne 0) { throw "AWS operation failed: $($Arguments[0]) $($Arguments[1])" }
    return ($result | ConvertFrom-Json)
}
function Resource([string]$type, [hashtable]$properties) {
    return @{ Type=$type; DeletionPolicy='Retain'; UpdateReplacePolicy='Retain'; Properties=$properties }
}
$identity = Invoke-Aws @('sts','get-caller-identity')
if ($identity.Account -ne '863819358995') { throw 'This import is for account 863819358995 only.' }
$stacks=Invoke-Aws @('cloudformation','list-stacks','--region',$Region)
if ($stacks.StackSummaries | Where-Object { $_.StackName -eq $StackName -and $_.StackStatus -notin @('DELETE_COMPLETE','REVIEW_IN_PROGRESS') }) {
    throw 'Infrastructure is already adopted. Use the admin launcher Plan/Apply actions; existing templates were not overwritten.'
}
$db = (Invoke-Aws @('rds','describe-db-instances','--db-instance-identifier',$DatabaseId,'--region',$Region)).DBInstances[0]
$subnet = $db.DBSubnetGroup
$group = (Invoke-Aws @('ec2','describe-security-groups','--group-ids',$LambdaSecurityGroupId,'--region',$Region)).SecurityGroups[0]
$roleName = ($db.MonitoringRoleArn -split '/')[-1]
$role = (Invoke-Aws @('iam','get-role','--role-name',$roleName)).Role
$rolePolicies = (Invoke-Aws @('iam','list-attached-role-policies','--role-name',$roleName)).AttachedPolicies
$rules = @($group.IpPermissionsEgress | ForEach-Object {
    $rule = @{IpProtocol=$_.IpProtocol; FromPort=$_.FromPort; ToPort=$_.ToPort}
    if ($_.UserIdGroupPairs.Count -ne 1) { throw 'Review Lambda egress rules before importing this environment.' }
    $rule.DestinationSecurityGroupId=$_.UserIdGroupPairs[0].GroupId
    $rule
})
$resources = [ordered]@{
    DatabaseMonitoringRole = Resource 'AWS::IAM::Role' @{
        RoleName=$role.RoleName; Path=$role.Path; MaxSessionDuration=$role.MaxSessionDuration
        AssumeRolePolicyDocument=$role.AssumeRolePolicyDocument
        ManagedPolicyArns=@($rolePolicies | ForEach-Object { $_.PolicyArn })
    }
    DatabaseSubnetGroup = Resource 'AWS::RDS::DBSubnetGroup' @{
        DBSubnetGroupName=$subnet.DBSubnetGroupName; DBSubnetGroupDescription=$subnet.DBSubnetGroupDescription
        SubnetIds=@($subnet.Subnets | ForEach-Object { $_.SubnetIdentifier })
    }
    ApiSecurityGroup = Resource 'AWS::EC2::SecurityGroup' @{
        GroupName=$group.GroupName; GroupDescription=$group.Description; VpcId=$group.VpcId
        SecurityGroupEgress=$rules
    }
    Database = Resource 'AWS::RDS::DBInstance' @{
        DBInstanceIdentifier=$db.DBInstanceIdentifier; DBInstanceClass=$db.DBInstanceClass
        Engine=$db.Engine; EngineVersion=$db.EngineVersion; AllocatedStorage=[string]$db.AllocatedStorage
        StorageType=$db.StorageType; StorageEncrypted=$db.StorageEncrypted; KmsKeyId=$db.KmsKeyId
        DBSubnetGroupName=@{Ref='DatabaseSubnetGroup'}
        VPCSecurityGroups=@($db.VpcSecurityGroups | ForEach-Object { $_.VpcSecurityGroupId })
        DBParameterGroupName=$db.DBParameterGroups[0].DBParameterGroupName
        MasterUsername=$db.MasterUsername; Port=[string]$db.Endpoint.Port
        BackupRetentionPeriod=$db.BackupRetentionPeriod; PreferredBackupWindow=$db.PreferredBackupWindow
        PreferredMaintenanceWindow=$db.PreferredMaintenanceWindow; MultiAZ=$db.MultiAZ
        PubliclyAccessible=$db.PubliclyAccessible; AutoMinorVersionUpgrade=$db.AutoMinorVersionUpgrade
        MaxAllocatedStorage=$db.MaxAllocatedStorage; CopyTagsToSnapshot=$db.CopyTagsToSnapshot
        DeletionProtection=$db.DeletionProtection; EnableIAMDatabaseAuthentication=$db.IAMDatabaseAuthenticationEnabled
        MonitoringInterval=$db.MonitoringInterval; MonitoringRoleArn=@{'Fn::GetAtt'=@('DatabaseMonitoringRole','Arn')}
        EnablePerformanceInsights=$db.PerformanceInsightsEnabled
        PerformanceInsightsRetentionPeriod=$db.PerformanceInsightsRetentionPeriod
        PerformanceInsightsKMSKeyId=$db.PerformanceInsightsKMSKeyId
        CACertificateIdentifier=$db.CACertificateIdentifier; NetworkType=$db.NetworkType
    }
}
$template = [ordered]@{
    AWSTemplateFormatVersion='2010-09-09'
    Description='StoreOps existing database and application networking; imported in place, retained on deletion/replacement.'
    Resources=$resources
}
$imports = @(
    @{ResourceType='AWS::IAM::Role'; LogicalResourceId='DatabaseMonitoringRole'; ResourceIdentifier=@{RoleName=$roleName}},
    @{ResourceType='AWS::RDS::DBSubnetGroup'; LogicalResourceId='DatabaseSubnetGroup'; ResourceIdentifier=@{DBSubnetGroupName=$subnet.DBSubnetGroupName}},
    @{ResourceType='AWS::EC2::SecurityGroup'; LogicalResourceId='ApiSecurityGroup'; ResourceIdentifier=@{Id=$LambdaSecurityGroupId}},
    @{ResourceType='AWS::RDS::DBInstance'; LogicalResourceId='Database'; ResourceIdentifier=@{DBInstanceIdentifier=$DatabaseId}}
)
$templatePath = Join-Path $PSScriptRoot 'infrastructure.template.json'
$importsPath = Join-Path $PSScriptRoot 'infrastructure.import.json'
$utf8 = New-Object Text.UTF8Encoding($false)
[IO.File]::WriteAllText($templatePath, ($template | ConvertTo-Json -Depth 40), $utf8)
[IO.File]::WriteAllText($importsPath, ($imports | ConvertTo-Json -Depth 20), $utf8)
Invoke-Aws @('cloudformation','validate-template','--template-body',('file://' + $templatePath),'--region',$Region) | Out-Null
Write-Host 'Validated adoption template. Existing settings are preserved; no passwords are written to it.'
if (-not $Execute) { return }
$changeSet = 'adopt-existing-' + (Get-Date -Format 'yyyyMMddHHmmss')
Invoke-Aws @('cloudformation','create-change-set','--stack-name',$StackName,'--change-set-name',$changeSet,
    '--change-set-type','IMPORT','--template-body',('file://' + $templatePath),
    '--resources-to-import',('file://' + $importsPath),'--capabilities','CAPABILITY_NAMED_IAM','--region',$Region) | Out-Null
Write-Host "Created import change set $changeSet. Review it before executing."
Write-Output $changeSet
