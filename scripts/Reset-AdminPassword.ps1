param(
    [string]$ConfigPath = "$env:LOCALAPPDATA\FoodInventory\database.properties"
)

$ErrorActionPreference = "Stop"

function Read-PasswordText([string]$Prompt) {
    $secure = Read-Host -Prompt $Prompt -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}

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

function New-PasswordHash([string]$Password) {
    $iterations = 310000
    $salt = New-Object byte[] 16
    [Security.Cryptography.RandomNumberGenerator]::Fill($salt)

    $deriveBytes = [Security.Cryptography.Rfc2898DeriveBytes]::new(
        $Password,
        $salt,
        $iterations,
        [Security.Cryptography.HashAlgorithmName]::SHA256
    )
    $hash = $deriveBytes.GetBytes(32)

    return "pbkdf2_sha256`$$iterations`$$(ConvertTo-Base64String $salt)`$$(ConvertTo-Base64String $hash)"
}

function ConvertTo-Base64String([byte[]]$Bytes) {
    return [Convert]::ToBase64String($Bytes)
}

$password = Read-PasswordText "New administrator password"
$confirmPassword = Read-PasswordText "Confirm administrator password"

if ([string]::IsNullOrWhiteSpace($password)) {
    throw "Password cannot be blank."
}

if ($password -ne $confirmPassword) {
    throw "Passwords do not match."
}

$configFile = [IO.FileInfo]::new($ConfigPath)
if (-not $configFile.Directory.Exists) {
    New-Item -ItemType Directory -Path $configFile.Directory.FullName | Out-Null
}

$properties = [ordered]@{}
if (Test-Path -LiteralPath $configFile.FullName) {
    $properties = ConvertTo-Properties (Get-Content -LiteralPath $configFile.FullName)
}

$properties["admin.password.hash"] = New-PasswordHash $password
$properties["admin.password_initialized"] = "true"
$properties.Remove("admin.password")

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# ESM Operations Manager database settings")

foreach ($key in $properties.Keys) {
    $lines.Add("$key=$($properties[$key])")
}

Set-Content -LiteralPath $configFile.FullName -Value $lines -Encoding UTF8
Write-Host "Administrator password reset for $($configFile.FullName)"
