$ErrorActionPreference = "Stop"
$token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwidXNlcm5hbWUiOiJhZG1pbiIsImlhdCI6MTc4NTgxMjk3MSwiZXhwIjoxNzg1ODQxNzcxfQ.zL4IEEXDEqHZEUAlWfmlE9AATW8fuyYNRqkneGa-sdI"
$headers = @{ Authorization = "Bearer $token" }
$base = "http://localhost:5173/api"
$sourceId = "2084177715329564673"

function Invoke-Api {
  param(
    [Parameter(Mandatory = $true)][string]$Method,
    [Parameter(Mandatory = $true)][string]$Path,
    [object]$Body
  )
  $params = @{
    Uri     = ($base + $Path)
    Method  = $Method
    Headers = $headers
  }
  if ($null -ne $Body) {
    $json = $Body | ConvertTo-Json -Depth 30 -Compress
    $params.ContentType = "application/json; charset=utf-8"
    $params.Body = [System.Text.Encoding]::UTF8.GetBytes($json)
  }
  $resp = Invoke-RestMethod @params
  if ($resp.code -ne 0) {
    throw ($resp | ConvertTo-Json -Depth 10)
  }
  return $resp.data
}

$tree = Invoke-Api -Method GET -Path "/collections/tree"
$collectionId = $tree[0].id
Write-Host "collection=$collectionId"

$sql = "SELECT DATE(`create_time`) AS `stat_date`, `caller` AS `caller`, COUNT(*) AS `request_count` FROM `sys_api_log` WHERE COALESCE(`is_delete`, 0) = 0 GROUP BY DATE(`create_time`), `caller`"

$datasetBody = @{
  name          = "sys_api_log_daily"
  description   = "sys_api_log daily request count by caller"
  modelType     = "SQL"
  dataSourceId  = $sourceId
  definitionSql = $sql
  collectionId  = $collectionId
  fields        = @(
    @{ name = "stat_date"; columnName = "stat_date"; fieldType = "DIMENSION" }
    @{ name = "caller"; columnName = "caller"; fieldType = "DIMENSION" }
    @{ name = "request_count"; columnName = "request_count"; fieldType = "METRIC"; aggregation = "SUM" }
  )
}
$dataset = Invoke-Api -Method POST -Path "/datasets" -Body $datasetBody
Write-Host "dataset=$($dataset.id)"

$queryObj = @{
  datasetId  = $dataset.id
  dimensions = @("stat_date", "caller")
  metrics    = @("request_count")
  sorts      = @(@{ field = "stat_date"; direction = "ASC" })
  limit      = 1000
}
$configObj = @{
  encoding = @{
    category = "stat_date"
    value    = @("request_count")
  }
}

$chartBody = @{
  name         = "daily_requests_by_caller"
  description  = "sys_api_log request count per day and caller"
  datasetId    = $dataset.id
  queryJson    = ($queryObj | ConvertTo-Json -Depth 20 -Compress)
  chartType    = "bar"
  configJson   = ($configObj | ConvertTo-Json -Depth 20 -Compress)
  collectionId = $collectionId
}
$chart = Invoke-Api -Method POST -Path "/charts" -Body $chartBody
Write-Host "chart=$($chart.id)"

$today = Get-Date
$start = $today.AddDays(-7).ToString("yyyy-MM-dd")
$end = $today.ToString("yyyy-MM-dd")

$zhParamsObject = @{
  parameters = @(
    @{
      id           = "dateRange"
      label        = "date range"
      type         = "date-range"
      required     = $true
      defaultValue = @{ start = $start; end = $end }
    }
    @{
      id          = "caller"
      label       = "caller"
      type        = "select"
      required    = $false
      optionsFrom = @{
        datasetId = $dataset.id
        field     = "caller"
        limit     = 200
      }
    }
  )
}

$dashboardBody = @{
  name         = "api_call_daily_report"
  description  = "big_data sys_api_log daily request counts by caller with date filter"
  configJson   = ($zhParamsObject | ConvertTo-Json -Depth 20 -Compress)
  collectionId = $collectionId
}
$dashboard = Invoke-Api -Method POST -Path "/dashboards" -Body $dashboardBody
Write-Host "dashboard=$($dashboard.id)"

$bindings = @(
  @{ parameterId = "dateRange"; mode = "semantic"; field = "stat_date"; operator = "EQ" }
  @{ parameterId = "caller"; mode = "semantic"; field = "caller"; operator = "EQ" }
)
$cardBody = @{
  chartId      = $chart.id
  title        = "daily requests by caller"
  layoutJson   = '{"x":0,"y":0,"w":12,"h":8}'
  bindingsJson = ($bindings | ConvertTo-Json -Depth 10 -Compress)
}
$card = Invoke-Api -Method POST -Path "/dashboards/$($dashboard.id)/cards" -Body $cardBody
Write-Host "card=$($card.id)"

$renderBody = @{
  forceRefresh    = $true
  parameterValues = @{
    dateRange = @{ start = $start; end = $end }
  }
}
$render = Invoke-Api -Method POST -Path "/dashboards/$($dashboard.id)/render" -Body $renderBody
Write-Host "renderCards=$($render.cards.Count)"
$first = $render.cards[0]
if ($first.error) {
  Write-Host "error=$($first.error)"
} else {
  Write-Host "rows=$($first.result.rows.Count)"
  Write-Host ("cols=" + ($first.result.columns -join ","))
}

Write-Host "VIEW=http://localhost:5173/dashboards/$($dashboard.id)/view"
Write-Host "EDIT=http://localhost:5173/dashboards/$($dashboard.id)/edit"
