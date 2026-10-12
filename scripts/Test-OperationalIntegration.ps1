param(
    [string]$PostgresBinDirectory = (Join-Path $PSScriptRoot '../.admin/pg-native/pgsql/bin')
)

$ErrorActionPreference = 'Stop'
$projectDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$binaryDirectory = [IO.Path]::GetFullPath($PostgresBinDirectory)
foreach ($executable in @('initdb.exe', 'pg_ctl.exe', 'createdb.exe')) {
    if (-not (Test-Path -LiteralPath (Join-Path $binaryDirectory $executable))) {
        throw "Supply a complete PostgreSQL binary directory with -PostgresBinDirectory. Missing $executable."
    }
}
if (-not (Test-Path -LiteralPath (Join-Path $binaryDirectory '../share/postgres.bki'))) {
    throw 'The PostgreSQL package is incomplete: share/postgres.bki is required.'
}

$artifactDirectory = Join-Path $projectDirectory ('.admin/operational-verification-' + [Guid]::NewGuid().ToString('N'))
$dataDirectory = Join-Path $artifactDirectory 'data'
[IO.Directory]::CreateDirectory($artifactDirectory) | Out-Null
$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$listener.Start()
$port = $listener.LocalEndpoint.Port
$listener.Stop()
$jdbcUrl = "jdbc:postgresql://127.0.0.1:$port/labour_verification"
$mavenExecutable = (Get-Command mvn -ErrorAction Stop).Source
$originalDirectory = Get-Location
$serverStarted = $false

function Invoke-MavenVerification {
    param([string[]]$Arguments, [string]$LogName)
    $logPath = Join-Path $artifactDirectory $LogName
    # Native stderr is captured as evidence; Maven's exit code decides success.
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & $mavenExecutable @Arguments *> $logPath
    $verificationExitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousPreference
    if ($verificationExitCode -ne 0) { throw "Verification failed. Inspect $logPath" }
}

try {
    Set-Location -LiteralPath $projectDirectory
    & (Join-Path $binaryDirectory 'initdb.exe') -D $dataDirectory -U labour_test --auth=trust --encoding=UTF8 --no-locale *> (Join-Path $artifactDirectory 'initdb.log')
    if ($LASTEXITCODE -ne 0) { throw 'Failed to initialize the disposable test cluster.' }
    $startArguments = @('-D', ('"' + $dataDirectory + '"'), '-l', ('"' + (Join-Path $artifactDirectory 'postgres.log') + '"'),
        '-o', ('"-h 127.0.0.1 -p ' + $port + '"'), '-w', 'start')
    $starter = Start-Process -FilePath (Join-Path $binaryDirectory 'pg_ctl.exe') -ArgumentList $startArguments -WindowStyle Hidden -PassThru
    if (-not $starter.WaitForExit(30000) -or $starter.ExitCode -ne 0) { throw 'Failed to start the isolated PostgreSQL server.' }
    $serverStarted = $true
    & (Join-Path $binaryDirectory 'createdb.exe') -h 127.0.0.1 -p $port -U labour_test labour_verification
    if ($LASTEXITCODE -ne 0) { throw 'Failed to create the disposable test database.' }

    Invoke-MavenVerification -Arguments @('-f', 'api/pom.xml', ("-Dlabour.integration.url=$jdbcUrl"),
        '-Dtest=LabourRepositoryTest,InventoryRepositoryTest', 'test', '-q') -LogName 'native-repository-tests.log'
    Invoke-MavenVerification -Arguments @(("-Dlabour.integration.url=$jdbcUrl"), '-Dprism.order=sw',
        '-Dtest=OperationalViewsIntegrationTest', 'test', '-q') -LogName 'authenticated-ui-tests.log'
    Write-Output "Native PostgreSQL and authenticated JavaFX verification passed. Logs: $artifactDirectory"
} finally {
    if ($serverStarted) {
        & (Join-Path $binaryDirectory 'pg_ctl.exe') -D $dataDirectory -m fast -w stop *> (Join-Path $artifactDirectory 'stop.log')
        if ($LASTEXITCODE -eq 0) {
            $resolvedDataDirectory = [IO.Path]::GetFullPath($dataDirectory)
            $resolvedArtifactDirectory = [IO.Path]::GetFullPath($artifactDirectory)
            if (-not $resolvedDataDirectory.StartsWith($resolvedArtifactDirectory + [IO.Path]::DirectorySeparatorChar,
                    [StringComparison]::OrdinalIgnoreCase)) { throw 'Refusing cleanup outside the disposable artifact directory.' }
            Remove-Item -LiteralPath $resolvedDataDirectory -Recurse -Force
        }
    }
    Set-Location -LiteralPath $originalDirectory.Path
}
