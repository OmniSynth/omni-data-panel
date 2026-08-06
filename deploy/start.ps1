# Omni Data Panel 一键启动（Docker Compose）
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

function Assert-Docker {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw "未找到 docker。请先安装 Docker Desktop（含 Compose v2）。"
    }
    docker compose version | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose 不可用。请升级到 Compose v2。"
    }
}

Assert-Docker

if (-not (Test-Path ".env")) {
    if (-not (Test-Path ".env.example")) {
        throw "缺少 .env.example，无法生成 .env。"
    }
    Copy-Item ".env.example" ".env"
    Write-Host "已从 .env.example 创建 .env，请先编辑其中的密码与密钥后再启动。" -ForegroundColor Yellow
    Write-Host "编辑完成后重新运行: .\start.ps1" -ForegroundColor Yellow
    exit 0
}

Write-Host "拉取镜像..." -ForegroundColor Cyan
docker compose pull
if ($LASTEXITCODE -ne 0) {
    Write-Host "pull 失败（若镜像尚未发布，可改用: docker compose up --build -d）" -ForegroundColor Yellow
}

Write-Host "启动服务..." -ForegroundColor Cyan
docker compose up -d
if ($LASTEXITCODE -ne 0) {
    throw "docker compose up 失败。"
}

$webPort = "80"
$apiPort = "8080"
$minioPort = "9001"
Get-Content ".env" | ForEach-Object {
    if ($_ -match '^\s*WEB_PORT\s*=\s*(.+)\s*$') { $webPort = $Matches[1].Trim() }
    if ($_ -match '^\s*SERVER_PORT\s*=\s*(.+)\s*$') { $apiPort = $Matches[1].Trim() }
    if ($_ -match '^\s*MINIO_CONSOLE_PORT\s*=\s*(.+)\s*$') { $minioPort = $Matches[1].Trim() }
}

$webUrl = if ($webPort -eq "80") { "http://localhost" } else { "http://localhost:$webPort" }

Write-Host ""
Write-Host "已启动。首次就绪可能需要 1–2 分钟（数据库初始化）。" -ForegroundColor Green
Write-Host "  Web:          $webUrl"
Write-Host "  API:          http://localhost:$apiPort"
Write-Host "  MinIO Console: http://localhost:$minioPort"
Write-Host "  账号:         admin / (.env 中 ADMIN_INITIAL_PASSWORD)"
Write-Host "停机: .\stop.ps1"
