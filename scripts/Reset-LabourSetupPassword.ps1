param(
    [string]$ConfigPath = "$env:LOCALAPPDATA\FoodInventory\database.properties"
)

$ErrorActionPreference = "Stop"

function ConvertTo-Properties([string[]]$Lines) {
    $properties = [ordered]@{}

    foreach ($line in $Lines) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith("#")) {
            continue
        }

        $separator = $line.IndexOf("=")
        if ($separator -lt 0) {
            continue
        }

        $key = $line.Substring(0, $separator).Trim()
        $value = $line.Substring($separator + 1)
        $properties[$key] = $value
    }

    return $properties
}

$configFile = [IO.FileInfo]::new($ConfigPath)
if (-not $configFile.Directory.Exists) {
    New-Item -ItemType Directory -Path $configFile.Directory.FullName | Out-Null
}

$properties = [ordered]@{}
if (Test-Path -LiteralPath $configFile.FullName) {
    $properties = ConvertTo-Properties (Get-Content -LiteralPath $configFile.FullName)
}

$properties.Remove("labour.setup.password.hash")
$properties.Remove("labour.setup.password_initialized")

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# StoreOps Manager database settings")

foreach ($key in $properties.Keys) {
    $lines.Add("$key=$($properties[$key])")
}

Set-Content -LiteralPath $configFile.FullName -Value $lines -Encoding UTF8
Write-Host "Labour Setup password reset for $($configFile.FullName)"
Write-Host "Next Labour Setup login will require the temporary password, then prompt for a new password."
