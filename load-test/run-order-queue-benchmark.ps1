param(
    [Parameter(Mandatory = $true)]
    [string]$JavaHome,
    [int]$TargetRate = 200,
    [string]$Duration = "5s",
    [int]$ProductId = 1,
    [int]$Stock = 100,
    [int]$WarmupRequests = 20,
    [int]$Run = 1,
    [int]$PreAllocatedVUs = 100,
    [int]$MaxVUs = 500,
    [int]$QueueDrainTimeoutSeconds = 300,
    [switch]$KeepRedisData,
    [string]$K6Image = "grafana/k6:2.2.0"
)

$ErrorActionPreference = "Stop"
$configRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$projectRoot = (Resolve-Path (Join-Path $configRoot "..")).Path
$javaExe = Join-Path $JavaHome "bin\java.exe"
$jar = Join-Path $projectRoot "order-api\build\libs\order-api-0.0.1-SNAPSHOT.jar"
$config = "--spring.config.additional-location=file:///$($configRoot.Replace('\', '/'))/config/order-api/"
$sessionId = Get-Date -Format "yyyyMMddHHmmss"
$queueKeyPrefix = "sunday:order:{queue}:load-test:$sessionId"
$consumerGroup = "reservation-workers"
$memberIdBase = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() * 1000
$resultName = "order-queue-${TargetRate}rps-${Duration}-run$Run"
$resultFile = "/scripts/results/$resultName.json"
$analysisFile = Join-Path $PSScriptRoot "results\$resultName-analysis.json"
$runtimeLogDirectory = Join-Path $projectRoot ".runtime-logs"
$stdout = Join-Path $runtimeLogDirectory "order-queue-$sessionId.out.log"
$stderr = Join-Path $runtimeLogDirectory "order-queue-$sessionId.err.log"
$dockerExe = (Get-Command docker).Source
$orderProcess = $null

function Get-Percentile {
    param(
        [double[]]$Values,
        [double]$Percentile
    )

    if ($Values.Count -eq 0) {
        return 0
    }

    $sorted = @($Values | Sort-Object)
    $index = [Math]::Max(0, [Math]::Ceiling($sorted.Count * $Percentile) - 1)

    return [Math]::Round($sorted[$index], 2)
}

function Wait-ForHealth {
    $deadline = (Get-Date).AddSeconds(60)
    $health = 0

    do {
        Start-Sleep -Milliseconds 500
        try {
            $health = (Invoke-WebRequest `
                -UseBasicParsing `
                -Uri "http://localhost:8084/actuator/health" `
                -TimeoutSec 1).StatusCode
        } catch {
            $health = 0
        }
    } until (($health -eq 200) -or ((Get-Date) -ge $deadline))

    if ($health -ne 200) {
        throw "Order API가 제한 시간 안에 시작되지 않았습니다."
    }
}

function Wait-ForQueueDrain {
    $deadline = (Get-Date).AddSeconds($QueueDrainTimeoutSeconds)
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $nextReportAt = Get-Date

    do {
        $lengthOutput = @(& docker exec redis-local redis-cli XLEN "$queueKeyPrefix`:requests")
        $lengthExitCode = $LASTEXITCODE
        if ($lengthExitCode -ne 0 -or $lengthOutput.Count -eq 0 -or $lengthOutput[0] -notmatch '^\d+$') {
            throw "Redis Stream 길이를 조회하지 못했습니다."
        }
        $length = [int]$lengthOutput[0]

        $pendingOutput = @(& docker exec redis-local redis-cli XPENDING "$queueKeyPrefix`:requests" $consumerGroup)
        $pendingExitCode = $LASTEXITCODE
        if ($pendingExitCode -ne 0 -or $pendingOutput.Count -eq 0 -or $pendingOutput[0] -notmatch '^\d+$') {
            throw "Redis Pending 메시지 수를 조회하지 못했습니다."
        }
        $pending = [int]$pendingOutput[0]

        if ((Get-Date) -ge $nextReportAt -or $length -eq 0) {
            Write-Host "대기열 잔여 메시지: $length, 처리 중 메시지: $pending"
            $nextReportAt = (Get-Date).AddSeconds(10)
        }

        if (($length -eq 0) -and ($pending -eq 0)) {
            $stopwatch.Stop()

            return $stopwatch.ElapsedMilliseconds
        }

        Start-Sleep -Seconds 1
    } until ((Get-Date) -ge $deadline)

    throw "대기열이 ${QueueDrainTimeoutSeconds}초 안에 비워지지 않았습니다."
}

function Get-QueueRecords {
    $statusScript = @"
local cursor = '0'
local result = {}
repeat
    local scanned = redis.call('SCAN', cursor, 'MATCH', ARGV[1], 'COUNT', 1000)
    cursor = scanned[1]
    for _, key in ipairs(scanned[2]) do
        local values = redis.call('HMGET', key, 'memberId', 'status', 'attempts', 'createdAt', 'updatedAt')
        table.insert(result, table.concat({values[1] or '', values[2] or '', values[3] or '', values[4] or '', values[5] or ''}, '|'))
    end
until cursor == '0'
return result
"@
    $pattern = "$queueKeyPrefix`:status:*"
    $rows = @(& docker exec redis-local redis-cli --raw EVAL $statusScript 0 $pattern)
    if ($LASTEXITCODE -ne 0) {
        throw "Redis 주문 상태를 집계하지 못했습니다."
    }

    return @(
        $rows |
            ForEach-Object {
                $values = $_ -split '\|', 5
                if ($values.Count -eq 5 -and [long]$values[0] -ge $memberIdBase) {
                    [pscustomobject]@{
                        MemberId = [long]$values[0]
                        Status = $values[1]
                        Attempts = [int]$values[2]
                        CreatedAt = [DateTimeOffset]::Parse($values[3])
                        UpdatedAt = [DateTimeOffset]::Parse($values[4])
                    }
                }
            }
    )
}

function Remove-QueueData {
    $deleteScript = @"
local keys = redis.call('KEYS', ARGV[1])
for _, key in ipairs(keys) do
    redis.call('UNLINK', key)
end
return #keys
"@
    $deleted = & docker exec redis-local redis-cli --raw EVAL $deleteScript 0 "$queueKeyPrefix`:*"
    if ($LASTEXITCODE -ne 0) {
        throw "부하 테스트 Redis 데이터를 정리하지 못했습니다."
    }

    Write-Host "부하 테스트 Redis 키 ${deleted}개를 정리했습니다."
}

if (-not (Test-Path -LiteralPath $javaExe)) {
    throw "Java 실행 파일을 찾을 수 없습니다: $javaExe"
}
if (-not (Test-Path -LiteralPath $jar)) {
    throw "Order API JAR를 찾을 수 없습니다: $jar"
}

$listener = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
    Where-Object { $_.LocalPort -eq 8084 }
if ($null -ne $listener) {
    throw "8084 포트를 다른 프로세스가 사용 중입니다."
}

New-Item -ItemType Directory -Force -Path $runtimeLogDirectory | Out-Null

try {
    $arguments = @(
        "-jar",
        $jar,
        "--spring.profiles.active=local",
        $config,
        "--sunday.order.queue.key-prefix=$queueKeyPrefix",
        "--sunday.order.queue.consumer-name=single-worker-$sessionId"
    )
    $startParameters = @{
        FilePath = $javaExe
        ArgumentList = $arguments
        WorkingDirectory = $projectRoot
        WindowStyle = "Hidden"
        RedirectStandardOutput = $stdout
        RedirectStandardError = $stderr
        PassThru = $true
    }
    $orderProcess = Start-Process @startParameters
    Wait-ForHealth

    $dockerMount = "${PSScriptRoot}:/scripts"
    & $dockerExe run --rm `
        -e "TARGET_RATE=$TargetRate" `
        -e "DURATION=$Duration" `
        -e "PRODUCT_ID=$ProductId" `
        -e "STOCK=$Stock" `
        -e "WARMUP_REQUESTS=$WarmupRequests" `
        -e "PRE_ALLOCATED_VUS=$PreAllocatedVUs" `
        -e "MAX_VUS=$MaxVUs" `
        -e "RUN_ID=$sessionId" `
        -e "MEMBER_ID_BASE=$memberIdBase" `
        -v $dockerMount `
        $K6Image run `
        --quiet `
        "--summary-export=$resultFile" `
        /scripts/k6/order-queue-spike.js
    if ($LASTEXITCODE -ne 0) {
        throw "k6 대기열 부하 테스트가 실패했습니다."
    }

    $drainDurationMs = Wait-ForQueueDrain
    $records = @(Get-QueueRecords)
    $durations = @($records | ForEach-Object { ($_.UpdatedAt - $_.CreatedAt).TotalMilliseconds })
    $statusCounts = @{}
    $records | Group-Object Status | ForEach-Object { $statusCounts[$_.Name] = $_.Count }
    $retryCount = @($records | Where-Object { $_.Attempts -gt 1 }).Count
    $state = Invoke-RestMethod `
        -Uri "http://localhost:8084/load-tests/orders/products/$ProductId/state" `
        -Method Get `
        -TimeoutSec 10
    $k6Summary = Get-Content -Raw -Encoding UTF8 (Join-Path $PSScriptRoot "results\$resultName.json") |
        ConvertFrom-Json
    $accepted = [int]$k6Summary.metrics.order_queue_accepted_count.count
    $dropped = [int]$k6Summary.metrics.dropped_iterations.count
    $acceptP95 = [Math]::Round(
        [double]$k6Summary.metrics.order_queue_accepted_duration.'p(95)',
        2
    )
    $processingWindowMs = if ($records.Count -eq 0) {
        0
    } else {
        (($records | Sort-Object UpdatedAt | Select-Object -Last 1).UpdatedAt -
            ($records | Sort-Object CreatedAt | Select-Object -First 1).CreatedAt).TotalMilliseconds
    }
    $throughput = if ($processingWindowMs -eq 0) {
        0
    } else {
        [Math]::Round($records.Count * 1000 / $processingWindowMs, 2)
    }

    $analysis = [ordered]@{
        runId = $sessionId
        targetRate = $TargetRate
        duration = $Duration
        productId = $ProductId
        stock = $Stock
        accepted = $accepted
        droppedIterations = $dropped
        acceptanceP95Ms = $acceptP95
        terminalStatuses = [ordered]@{
            succeeded = [int]$statusCounts["SUCCEEDED"]
            soldOut = [int]$statusCounts["SOLD_OUT"]
            rejected = [int]$statusCounts["REJECTED"]
            failed = [int]$statusCounts["FAILED"]
        }
        retriedRequests = $retryCount
        queueDrainAfterLoadMs = $drainDurationMs
        processingThroughputPerSecond = $throughput
        endToEndLatencyMs = [ordered]@{
            p50 = Get-Percentile $durations 0.50
            p95 = Get-Percentile $durations 0.95
            max = if ($durations.Count -eq 0) { 0 } else { [Math]::Round(($durations | Measure-Object -Maximum).Maximum, 2) }
        }
        finalState = [ordered]@{
            pendingReservations = [long]$state.pendingReservations
            availableUnitStocks = [long]$state.availableUnitStocks
            productStockColumn = [long]$state.productStockColumn
        }
    }

    $invariantHolds =
        $accepted -eq $records.Count -and
        [int]$statusCounts["SUCCEEDED"] -eq $Stock -and
        [int]$statusCounts["SOLD_OUT"] -eq ($accepted - $Stock) -and
        [int]$statusCounts["REJECTED"] -eq 0 -and
        [int]$statusCounts["FAILED"] -eq 0 -and
        [long]$state.pendingReservations -eq $Stock -and
        [long]$state.availableUnitStocks -eq 0
    $analysis["invariantHolds"] = $invariantHolds

    $analysisJson = $analysis | ConvertTo-Json -Depth 5
    [System.IO.File]::WriteAllText(
        $analysisFile,
        $analysisJson,
        [System.Text.UTF8Encoding]::new($false)
    )
    Write-Output $analysisJson

    if (-not $invariantHolds) {
        throw "대기열 처리 결과가 재고 불변식을 만족하지 않습니다."
    }

    if (-not $KeepRedisData) {
        Remove-QueueData
    }
} finally {
    if ($null -ne $orderProcess -and -not $orderProcess.HasExited) {
        Stop-Process -Id $orderProcess.Id -Force
    }
}
