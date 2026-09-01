param(
    [Parameter(Mandatory = $true)]
    [string]$JavaHome,
    [int]$TargetRate = 200,
    [string]$Duration = "5s",
    [int]$Stock = 100,
    [int]$WarmupRequests = 20,
    [string]$K6Image = "grafana/k6:2.2.0"
)

$ErrorActionPreference = "Stop"
$configRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$projectRoot = (Resolve-Path (Join-Path $configRoot "..")).Path
$javaExe = Join-Path $JavaHome "bin\java.exe"
$jar = Join-Path $projectRoot "order-api\build\libs\order-api-0.0.1-SNAPSHOT.jar"
$config = "--spring.config.additional-location=file:///$($configRoot.Replace('\', '/'))/config/order-api/"
$sequence = @("skip-locked", "pessimistic", "pessimistic", "skip-locked", "skip-locked", "pessimistic")
$runCounts = @{ "skip-locked" = 0; "pessimistic" = 0 }
$sessionId = Get-Date -Format "yyyyMMddHHmmss"
$dockerExe = (Get-Command docker).Source

if (-not (Test-Path -LiteralPath $javaExe)) {
    throw "Java executable not found: $javaExe"
}
if (-not (Test-Path -LiteralPath $jar)) {
    throw "Order API JAR not found: $jar"
}

for ($sequenceIndex = 0; $sequenceIndex -lt $sequence.Count; $sequenceIndex++) {
    $mode = $sequence[$sequenceIndex]
    $runCounts[$mode]++
    $run = $runCounts[$mode]

    $listener = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
        Where-Object { $_.LocalPort -eq 8084 }
    if ($null -ne $listener) {
        $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
        $jarPattern = [regex]::Escape($jar)
        if ($process.CommandLine -notmatch $jarPattern) {
            throw "Unexpected process is listening on port 8084: $($process.CommandLine)"
        }
        Stop-Process -Id $listener.OwningProcess -Force
    }

    $logSuffix = $sequenceIndex + 1
    $stdout = Join-Path $projectRoot ".runtime-logs\benchmark-order-$sessionId-$logSuffix.out.log"
    $stderr = Join-Path $projectRoot ".runtime-logs\benchmark-order-$sessionId-$logSuffix.err.log"
    $arguments = @("-jar", $jar, "--spring.profiles.active=local", $config)
    $startParameters = @{
        FilePath = $javaExe
        ArgumentList = $arguments
        WorkingDirectory = $projectRoot
        WindowStyle = "Hidden"
        RedirectStandardOutput = $stdout
        RedirectStandardError = $stderr
    }
    Start-Process @startParameters

    $deadline = (Get-Date).AddSeconds(60)
    do {
        Start-Sleep -Milliseconds 500
        try {
            $healthParameters = @{
                UseBasicParsing = $true
                Uri = "http://localhost:8084/actuator/health"
                TimeoutSec = 1
            }
            $health = (Invoke-WebRequest @healthParameters).StatusCode
        } catch {
            $health = 0
        }
    } until (($health -eq 200) -or ((Get-Date) -ge $deadline))
    if ($health -ne 200) {
        throw "Order API did not become healthy for sequence $($sequenceIndex + 1)"
    }

    & docker exec postgres-local psql -U sunday -d sunday_local -c `
        "VACUUM ANALYZE order_service.order_reservations, order_service.product_stock, order_service.product, order_service.orders;" |
        Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "VACUUM ANALYZE failed"
    }

    $resultFile = "/scripts/results/order-$mode-${TargetRate}rps-isolated-run$run.json"
    $dockerMount = "${PSScriptRoot}:/scripts"
    $dockerArguments = @(
        "run", "--rm",
        "-e", "TARGET_RATE=$TargetRate",
        "-e", "DURATION=$Duration",
        "-e", "STOCK=$Stock",
        "-e", "WARMUP_REQUESTS=$WarmupRequests",
        "-e", "PRE_ALLOCATED_VUS=300",
        "-e", "MAX_VUS=1000",
        "-e", "ORDER_METHOD=$mode",
        "-v", $dockerMount,
        $K6Image, "run",
        "--quiet",
        "--summary-export=$resultFile",
        "/scripts/k6/order-spike.js"
    )
    $k6Stdout = Join-Path $projectRoot ".runtime-logs\benchmark-k6-$sessionId-$logSuffix.out.log"
    $k6Stderr = Join-Path $projectRoot ".runtime-logs\benchmark-k6-$sessionId-$logSuffix.err.log"
    $dockerParameters = @{
        FilePath = $dockerExe
        ArgumentList = $dockerArguments
        WorkingDirectory = $projectRoot
        WindowStyle = "Hidden"
        RedirectStandardOutput = $k6Stdout
        RedirectStandardError = $k6Stderr
        Wait = $true
        PassThru = $true
    }
    $dockerProcess = Start-Process @dockerParameters
    $output = @(
        Get-Content -LiteralPath $k6Stdout
        Get-Content -LiteralPath $k6Stderr
    )

    if ($dockerProcess.ExitCode -ne 0) {
        $output | Select-Object -Last 80
        throw "$mode isolated run $run failed"
    }
    $state = ($output | Select-String "FINAL_STATE" | Select-Object -Last 1).Line
    Write-Output "sequence=$($sequenceIndex + 1) mode=$mode run=$run completed: $state"
}
