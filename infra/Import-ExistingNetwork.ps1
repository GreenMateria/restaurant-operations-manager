param([string]$Region='ca-central-1', [string]$VpcId='vpc-0b41e3692165238ed', [switch]$PrepareChangeSet)
$ErrorActionPreference='Stop'
$aws='C:\Program Files\Amazon\AWSCLIV2\aws.exe'
function AwsJson([string[]]$Arguments) {
    $result=& $aws @Arguments --output json --no-cli-pager
    if ($LASTEXITCODE -ne 0) { throw "AWS $($Arguments[0]) $($Arguments[1]) failed." }
    $result | ConvertFrom-Json
}
function RetainedResource([string]$type, [hashtable]$properties) {
    @{Type=$type; Properties=$properties; DeletionPolicy='Retain'; UpdateReplacePolicy='Retain'}
}
if ((AwsJson @('sts','get-caller-identity')).Account -ne '863819358995') { throw 'Wrong AWS account.' }
$stacks=AwsJson @('cloudformation','list-stacks','--region',$Region)
if ($stacks.StackSummaries | Where-Object { $_.StackName -eq 'esm-operations-network' -and $_.StackStatus -notin @('DELETE_COMPLETE','REVIEW_IN_PROGRESS') }) {
    throw 'Network is already adopted. Use the admin launcher Plan/Apply actions; existing templates were not overwritten.'
}
$vpc=(AwsJson @('ec2','describe-vpcs','--vpc-ids',$VpcId,'--region',$Region)).Vpcs[0]
$subnets=(AwsJson @('ec2','describe-subnets','--filters',"Name=vpc-id,Values=$VpcId",'--region',$Region)).Subnets
$groups=(AwsJson @('ec2','describe-security-groups','--filters',"Name=vpc-id,Values=$VpcId",'Name=group-name,Values=default','--region',$Region)).SecurityGroups
$tables=(AwsJson @('ec2','describe-route-tables','--filters',"Name=vpc-id,Values=$VpcId",'--region',$Region)).RouteTables
$gateways=(AwsJson @('ec2','describe-internet-gateways','--filters',"Name=attachment.vpc-id,Values=$VpcId",'--region',$Region)).InternetGateways
$resources=[ordered]@{
    Network=RetainedResource 'AWS::EC2::VPC' @{CidrBlock=$vpc.CidrBlock; EnableDnsSupport=$true; EnableDnsHostnames=$true; InstanceTenancy=$vpc.InstanceTenancy}
}
$imports=New-Object Collections.Generic.List[object]
$imports.Add(@{ResourceType='AWS::EC2::VPC'; LogicalResourceId='Network'; ResourceIdentifier=@{VpcId=$VpcId}})
foreach ($subnet in $subnets) {
    $name='Subnet' + ($subnet.AvailabilityZone -replace '[^A-Za-z0-9]','')
    $resources[$name]=RetainedResource 'AWS::EC2::Subnet' @{
        VpcId=@{Ref='Network'}; CidrBlock=$subnet.CidrBlock; AvailabilityZone=$subnet.AvailabilityZone
        MapPublicIpOnLaunch=$subnet.MapPublicIpOnLaunch; AssignIpv6AddressOnCreation=$subnet.AssignIpv6AddressOnCreation
    }
    $imports.Add(@{ResourceType='AWS::EC2::Subnet'; LogicalResourceId=$name; ResourceIdentifier=@{SubnetId=$subnet.SubnetId}})
}
function Convert-Rules($permissions, [bool]$ingress) {
    foreach ($permission in $permissions) {
        foreach ($pair in $permission.UserIdGroupPairs) {
            $rule=@{IpProtocol=$permission.IpProtocol}
            if ($permission.IpProtocol -ne '-1') { $rule.FromPort=$permission.FromPort; $rule.ToPort=$permission.ToPort }
            if ($ingress) { $rule.SourceSecurityGroupId=$pair.GroupId } else { $rule.DestinationSecurityGroupId=$pair.GroupId }
            if ($pair.Description) { $rule.Description=$pair.Description }
            $rule
        }
        foreach ($range in $permission.IpRanges) {
            $rule=@{IpProtocol=$permission.IpProtocol; CidrIp=$range.CidrIp}
            if ($permission.IpProtocol -ne '-1') { $rule.FromPort=$permission.FromPort; $rule.ToPort=$permission.ToPort }
            if ($range.Description) { $rule.Description=$range.Description }
            $rule
        }
        if ($permission.Ipv6Ranges.Count -gt 0 -or $permission.PrefixListIds.Count -gt 0) { throw 'Review IPv6/prefix rules before this baseline import.' }
    }
}
$group=$groups[0]
$resources.DatabaseAccessGroup=RetainedResource 'AWS::EC2::SecurityGroup' @{
    GroupName=$group.GroupName; GroupDescription=$group.Description; VpcId=@{Ref='Network'}
    SecurityGroupIngress=@(Convert-Rules $group.IpPermissions $true)
    SecurityGroupEgress=@(Convert-Rules $group.IpPermissionsEgress $false)
}
$imports.Add(@{ResourceType='AWS::EC2::SecurityGroup'; LogicalResourceId='DatabaseAccessGroup'; ResourceIdentifier=@{Id=$group.GroupId}})
if ($tables.Count -ne 1 -or $gateways.Count -ne 1) { throw 'Review network topology before importing.' }
$resources.InternetGateway=RetainedResource 'AWS::EC2::InternetGateway' @{}
$imports.Add(@{ResourceType='AWS::EC2::InternetGateway'; LogicalResourceId='InternetGateway'; ResourceIdentifier=@{InternetGatewayId=$gateways[0].InternetGatewayId}})
$resources.RouteTable=RetainedResource 'AWS::EC2::RouteTable' @{VpcId=@{Ref='Network'}}
$imports.Add(@{ResourceType='AWS::EC2::RouteTable'; LogicalResourceId='RouteTable'; ResourceIdentifier=@{RouteTableId=$tables[0].RouteTableId}})
# The implicit default-subnet associations and existing gateway attachment are
# account baseline relationships, not importable CloudFormation resources.
$template=@{AWSTemplateFormatVersion='2010-09-09'; Description='Retained adoption of the existing StoreOps VPC baseline; preserves admin and Lambda access.'; Resources=$resources}
$templatePath=Join-Path $PSScriptRoot 'network.template.json'
$importsPath=Join-Path $PSScriptRoot 'network.import.json'
$encoding=New-Object Text.UTF8Encoding($false)
[IO.File]::WriteAllText($templatePath, ($template | ConvertTo-Json -Depth 40), $encoding)
[IO.File]::WriteAllText($importsPath, ($imports.ToArray() | ConvertTo-Json -Depth 20), $encoding)
AwsJson @('cloudformation','validate-template','--template-body',('file://'+$templatePath),'--region',$Region) | Out-Null
if ($PrepareChangeSet) {
    $name='adopt-network-'+(Get-Date -Format 'yyyyMMddHHmmss')
    AwsJson @('cloudformation','create-change-set','--stack-name','esm-operations-network','--change-set-name',$name,
        '--change-set-type','IMPORT','--template-body',('file://'+$templatePath),'--resources-to-import',('file://'+$importsPath),'--region',$Region) | Out-Null
    Write-Output $name
}
