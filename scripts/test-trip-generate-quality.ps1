param(
  [string]$BaseUrl = "http://127.0.0.1:8080",
  [string]$Account = "user",
  [string]$Password = "123456",
  [string]$Prompt = "",
  [string]$Departure = "",
  [switch]$ViaAnalyze
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

if ([string]::IsNullOrWhiteSpace($Prompt)) {
  $Prompt = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String("5bim54i25q+N5Y675p2t5bee546pM+Wkqe+8jOS4jeimgeWkque0r++8jOWWnOasouiHqueEtumjjuWFieWSjOWOhuWPsuaWh+WMlu+8jOe+jumjn+S5n+aDs+S9k+mqjOS4gOS4i++8jOmihOeulzQwMDDku6XlhoXvvIzku47kuIrmtbflh7rlj5HjgII="))
}
if ([string]::IsNullOrWhiteSpace($Departure)) {
  $Departure = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String("5LiK5rW3"))
}

function Post-Json($Path, $Body, $Headers = @{}) {
  $json = $Body | ConvertTo-Json -Depth 80 -Compress
  $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
  Invoke-RestMethod -Method Post -Uri "$BaseUrl$Path" -Headers $Headers -ContentType "application/json; charset=utf-8" -Body $bytes
}

function Decode-Utf8($Base64) {
  [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($Base64))
}

function Add-Issue([System.Collections.Generic.List[string]]$Issues, [string]$Issue) {
  if (![string]::IsNullOrWhiteSpace($Issue)) {
    [void]$Issues.Add($Issue)
  }
}

function Has-Value($Value) {
  if ($null -eq $Value) { return $false }
  if ($Value -is [string]) { return -not [string]::IsNullOrWhiteSpace($Value) }
  return $true
}

function In-ChinaCoord($Lng, $Lat) {
  if ($null -eq $Lng -or $null -eq $Lat) { return $false }
  $lngNum = [double]$Lng
  $latNum = [double]$Lat
  return $lngNum -ge 73 -and $lngNum -le 136 -and $latNum -ge 3 -and $latNum -le 54
}

function Distance-Km($Lng1, $Lat1, $Lng2, $Lat2) {
  $r = 6371.0
  $latRad1 = [double]$Lat1 * [Math]::PI / 180
  $latRad2 = [double]$Lat2 * [Math]::PI / 180
  $deltaLat = ([double]$Lat2 - [double]$Lat1) * [Math]::PI / 180
  $deltaLng = ([double]$Lng2 - [double]$Lng1) * [Math]::PI / 180
  $a = [Math]::Sin($deltaLat / 2) * [Math]::Sin($deltaLat / 2) +
    [Math]::Cos($latRad1) * [Math]::Cos($latRad2) *
    [Math]::Sin($deltaLng / 2) * [Math]::Sin($deltaLng / 2)
  $c = 2 * [Math]::Atan2([Math]::Sqrt($a), [Math]::Sqrt(1 - $a))
  return $r * $c
}

Write-Host "== PlanGo generate quality test =="
Write-Host "BaseUrl: $BaseUrl"
Write-Host "Prompt : $Prompt"

$login = Post-Json "/api/auth/login" @{ account = $Account; password = $Password }
if ($login.code -ne 200 -or -not $login.data.token) {
  throw "登录失败：$($login | ConvertTo-Json -Depth 20)"
}
$headers = @{ Authorization = "Bearer $($login.data.token)" }
Write-Host "Login : OK user=$($login.data.user.username)"

$hangzhou = Decode-Utf8 "5p2t5bee"
$shanghai = Decode-Utf8 "5LiK5rW3"
$nature = Decode-Utf8 "6Ieq54S26aOO5YWJ"
$history = Decode-Utf8 "5Y6G5Y+y5paH5YyW"
$food = Decode-Utf8 "576O6aOf"
$notTired = Decode-Utf8 "5LiN6KaB5aSq57Sv"

if ($ViaAnalyze) {
  $analyze = Post-Json "/api/ai/trips/analyze" @{ userInput = $Prompt } $headers
  if ($analyze.code -ne 200) {
    throw "analyze failed: $($analyze | ConvertTo-Json -Depth 20)"
  }
  $analysis = $analyze.data
  Write-Host "Analyze: $($analysis.status)"
  if ($analysis.status -ne "READY") {
    throw "analyze did not reach READY: $($analysis | ConvertTo-Json -Depth 40)"
  }
} else {
  $analysis = @{
    conversationId = [guid]::NewGuid().ToString()
    requirement = @{
      departure = $shanghai
      destination = $hangzhou
      routeMode = "DESTINATION_CITY_TRIP"
      routeStructure = "SINGLE_CITY"
      routeCities = @($hangzhou)
      days = 3
      budget = 4000
      budgetType = "TOTAL"
      peopleCount = 3
      preferences = @($nature, $history, $food)
      pace = "LIGHT"
      avoidances = @($notTired)
    }
  }
  Write-Host "Analyze: skipped, using structured requirement"
}

$generate = Post-Json "/api/ai/trips/generate" @{
  conversationId = $analysis.conversationId
  requirement = $analysis.requirement
} $headers
if ($generate.code -ne 200) {
  throw "generate 失败：$($generate | ConvertTo-Json -Depth 20)"
}

$data = $generate.data
$plan = $data.tripPlan
$issues = [System.Collections.Generic.List[string]]::new()
$score = 10

if ($data.schemaVersion -ne "trip-plan-v1") {
  Add-Issue $issues "schemaVersion 不是 trip-plan-v1，实际为：$($data.schemaVersion)"
  $score -= 1
}
if (-not (Has-Value $plan.title)) { Add-Issue $issues "tripPlan.title 缺失"; $score -= 1 }
if (-not (Has-Value $plan.destination)) { Add-Issue $issues "tripPlan.destination 缺失"; $score -= 1 }
if (-not $plan.dailyPlans -or @($plan.dailyPlans).Count -eq 0) {
  Add-Issue $issues "dailyPlans 为空"
  $score -= 3
}

$spotNames = [System.Collections.Generic.List[string]]::new()
$missingCoord = 0
$mockCount = 0
$spotCount = 0

foreach ($day in @($plan.dailyPlans)) {
  if (-not (Has-Value $day.theme)) { Add-Issue $issues "Day $($day.day) theme 缺失"; $score -= 0.5 }
  if (-not (Has-Value $day.intensity)) { Add-Issue $issues "Day $($day.day) intensity 缺失"; $score -= 0.5 }
  $spots = @($day.spots)
  if ($spots.Count -lt 2 -or $spots.Count -gt 4) {
    Add-Issue $issues "Day $($day.day) spots 数量应为 2-4，实际 $($spots.Count)"
    $score -= 1
  }
  foreach ($spot in $spots) {
    $spotCount++
    [void]$spotNames.Add([string]$spot.name)
    if (-not (Has-Value $spot.poiId)) { Add-Issue $issues "Day $($day.day) spot 缺 poiId：$($spot.name)"; $score -= 0.3 }
    if (-not (Has-Value $spot.name)) { Add-Issue $issues "Day $($day.day) spot 缺 name"; $score -= 0.5 }
    if (-not (Has-Value $spot.order)) { Add-Issue $issues "Day $($day.day) spot 缺 order：$($spot.name)"; $score -= 0.3 }
    if (-not (Has-Value $spot.reason)) { Add-Issue $issues "Day $($day.day) spot 缺 reason：$($spot.name)"; $score -= 0.3 }
    if (-not (Has-Value $spot.suggestedDurationMinutes)) { Add-Issue $issues "Day $($day.day) spot 缺 suggestedDurationMinutes：$($spot.name)"; $score -= 0.3 }
    if (-not (In-ChinaCoord $spot.lng $spot.lat)) {
      $missingCoord++
      Add-Issue $issues "Day $($day.day) spot 坐标缺失或异常：$($spot.name) lng=$($spot.lng) lat=$($spot.lat)"
      $score -= 0.5
    }
    if ($spot.source -ne "AMAP") {
      $mockCount++
    }
  }
  $maxDistance = 0
  for ($i = 0; $i -lt $spots.Count; $i++) {
    for ($j = $i + 1; $j -lt $spots.Count; $j++) {
      if ((In-ChinaCoord $spots[$i].lng $spots[$i].lat) -and (In-ChinaCoord $spots[$j].lng $spots[$j].lat)) {
        $distance = Distance-Km $spots[$i].lng $spots[$i].lat $spots[$j].lng $spots[$j].lat
        if ($distance -gt $maxDistance) { $maxDistance = $distance }
      }
    }
  }
  if ($maxDistance -gt 30 -and $day.intensity -eq "LIGHT") {
    Add-Issue $issues ("Day " + $day.day + " route is too spread for LIGHT intensity: max distance " + [Math]::Round($maxDistance, 1) + "km")
    $score -= 1.5
  } elseif ($maxDistance -gt 50) {
    Add-Issue $issues ("Day " + $day.day + " route is too spread: max distance " + [Math]::Round($maxDistance, 1) + "km")
    $score -= 1
  }
}

$duplicates = $spotNames | Group-Object | Where-Object { $_.Count -gt 1 } | ForEach-Object { "$($_.Name)x$($_.Count)" }
if ($duplicates) {
  Add-Issue $issues ("Duplicate spots: " + ($duplicates -join ", "))
  $score -= [Math]::Min(3, @($duplicates).Count)
}

if ($spotCount -gt 0) {
  $uniqueRatio = @($spotNames | Select-Object -Unique).Count / $spotCount
  if ($uniqueRatio -lt 0.7) {
    Add-Issue $issues ("Unique spot ratio too low: " + [Math]::Round($uniqueRatio, 2))
    $score -= 2
  }
}

$poiSource = $null
if ($plan.dataQuality -and $plan.dataQuality.poiSource) {
  $poiSource = $plan.dataQuality.poiSource
}
if ($poiSource -ne "AMAP") {
  Add-Issue $issues "dataQuality.poiSource is not AMAP, actual: $poiSource"
  $score -= 1
}

if ($score -lt 0) { $score = 0 }
$score = [Math]::Round($score, 1)

Write-Host ""
Write-Host "== Summary =="
Write-Host "schemaVersion : $($data.schemaVersion)"
Write-Host "title         : $($plan.title)"
Write-Host "destination   : $($plan.destination)"
Write-Host "days          : $(@($plan.dailyPlans).Count)"
Write-Host "spots         : $spotCount"
Write-Host "non-AMAP spots: $mockCount"
Write-Host "coord issues  : $missingCoord"
Write-Host "poiSource     : $poiSource"
Write-Host "score         : $score / 10"

Write-Host ""
Write-Host "== Daily Spots =="
foreach ($day in @($plan.dailyPlans)) {
  Write-Host "Day $($day.day) [$($day.intensityLabel)/$($day.intensity)] $($day.theme)"
  foreach ($spot in @($day.spots)) {
    Write-Host ("  {0}. {1} | {2},{3} | {4} | {5}" -f $spot.order, $spot.name, $spot.lng, $spot.lat, $spot.source, $spot.reason)
  }
}

Write-Host ""
Write-Host "== Issues =="
if ($issues.Count -eq 0) {
  Write-Host "No blocking issues."
} else {
  foreach ($issue in $issues) {
    Write-Host "- $issue"
  }
}
