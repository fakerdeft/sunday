<#
.SYNOPSIS
    주문 서버 앞단 게이트(gate-api) 부하 테스트를 실행한다.

.DESCRIPTION
    order-api(8084) 와 gate-api(8085) 가 모두 실행 중이어야 한다. PostgreSQL 과 Redis 도 필요하다.
    k6 는 Docker 컨테이너로 실행하며, 결과 요약과 최종 재고 상태를 results 디렉터리에 남긴다.

.EXAMPLE
    .\load-test\run-order-pass-benchmark.ps1 -TargetRate 200 -Duration '5s' -Stock 100
#>
param(
    [int]$TargetRate = 200,
    [string]$Duration = '5s',
    [int]$Stock = 100,
    [int]$ProductId = 1,
    [int]$PreAllocatedVUs = 300,
    [int]$MaxVUs = 1000,
    [string]$OrderBaseUrl = 'http://host.docker.internal:8084',
    [string]$GateBaseUrl = 'http://host.docker.internal:8085',
    [string]$RunLabel = ''
)

# k6 는 진행 로그를 stderr 로 내보낸다. Stop 으로 두면 정상 실행도 중단되므로 Continue 로 둔다.
$ErrorActionPreference = 'Continue'

$loadTestDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$resultsDir = Join-Path $loadTestDir 'results'
if (-not (Test-Path $resultsDir)) { New-Item -ItemType Directory -Path $resultsDir | Out-Null }

$label = if ($RunLabel) { $RunLabel } else { "order-pass-${TargetRate}rps-${Duration}" }
$runId = Get-Date -Format 'yyyyMMddHHmmss'
$summaryPath = Join-Path $resultsDir "$label.json"

Write-Host "== 주문 게이트 부하 테스트 =="
Write-Host "   목표 RPS : $TargetRate / 지속 : $Duration / 재고 : $Stock"

foreach ($server in @(
    @{ Name = 'order-api'; Port = 8084; Url = $OrderBaseUrl },
    @{ Name = 'gate-api'; Port = 8085; Url = $GateBaseUrl }
)) {
    $healthUrl = $server.Url.Replace('host.docker.internal', 'localhost') + '/actuator/health'
    try {
        $health = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 5 -ErrorAction Stop
        if ($health.status -ne 'UP') { throw "$($server.Name) 상태가 UP 이 아닙니다: $($health.status)" }
    } catch {
        throw "$($server.Name)($($server.Port)) 에 연결하지 못했습니다. 먼저 실행해 주세요."
    }
}

docker run --rm -i `
    -e ORDER_BASE_URL=$OrderBaseUrl `
    -e GATE_BASE_URL=$GateBaseUrl `
    -e PRODUCT_ID=$ProductId `
    -e STOCK=$Stock `
    -e TARGET_RATE=$TargetRate `
    -e DURATION=$Duration `
    -e PRE_ALLOCATED_VUS=$PreAllocatedVUs `
    -e MAX_VUS=$MaxVUs `
    -e K6_SUMMARY_EXPORT=/scripts/results/$label.json `
    -v "${loadTestDir}:/scripts" `
    grafana/k6:2.2.0 run /scripts/k6/order-pass-spike.js

$stateUrl = $OrderBaseUrl.Replace('host.docker.internal', 'localhost') + "/load-tests/orders/products/$ProductId/state"
$state = Invoke-RestMethod -Uri $stateUrl -TimeoutSec 10

if (Test-Path $summaryPath) {
    $summary = Get-Content $summaryPath -Raw | ConvertFrom-Json
    $m = $summary.metrics

    function MetricCount($name) {
        if ($m.PSObject.Properties.Name -contains $name) { return [int]$m.$name.count }
        return 0
    }
    function MetricP95($name) {
        if ($m.PSObject.Properties.Name -contains $name) { return [math]::Round($m.$name.'p(95)', 2) }
        return $null
    }

    $analysis = [ordered]@{
        runId              = $runId
        targetRate         = $TargetRate
        duration           = $Duration
        productId          = $ProductId
        stock              = $Stock
        journeys           = [int]$m.iterations.count
        droppedIterations  = MetricCount 'dropped_iterations'
        passP95Ms          = MetricP95 'pass_request_duration'
        orderP95Ms         = MetricP95 'pass_order_duration'
        journeyP95Ms       = MetricP95 'pass_journey_duration'
        outcomes           = [ordered]@{
            reserved       = MetricCount 'pass_reserved_count'
            soldOutAtGate  = MetricCount 'pass_sold_out_at_gate_count'
            soldOutAtOrder = MetricCount 'pass_sold_out_at_order_count'
            notAdmitted    = MetricCount 'pass_not_admitted_count'
        }
        gateApiRequests    = MetricCount 'pass_gate_api_requests'
        orderApiRequests   = MetricCount 'pass_order_api_requests'
        finalState         = [ordered]@{
            pendingReservations = $state.pendingReservations
            availableUnitStocks = $state.availableUnitStocks
            productStockColumn  = $state.productStockColumn
        }
        invariantHolds     = ($state.pendingReservations -eq $Stock) -and
                             (($state.availableUnitStocks + $state.pendingReservations) -eq $Stock)
    }

    $analysisPath = Join-Path $resultsDir "$label-analysis.json"
    $analysis | ConvertTo-Json -Depth 5 | Out-File -FilePath $analysisPath -Encoding utf8

    Write-Host ""
    Write-Host "== 요약 =="
    Write-Host "   전체 요청          : $($analysis.journeys)"
    Write-Host "   주문 성공          : $($analysis.outcomes.reserved)"
    Write-Host "   게이트에서 품절     : $($analysis.outcomes.soldOutAtGate)"
    Write-Host "   gate-api 요청 수   : $($analysis.gateApiRequests)"
    Write-Host "   order-api 요청 수  : $($analysis.orderApiRequests)"
    Write-Host "   게이트 응답 p95     : $($analysis.passP95Ms) ms"
    Write-Host "   전체 여정 p95      : $($analysis.journeyP95Ms) ms"
    Write-Host "   재고 불변식 유지    : $($analysis.invariantHolds)"
    Write-Host ""
    Write-Host "분석 파일: $analysisPath"
}
