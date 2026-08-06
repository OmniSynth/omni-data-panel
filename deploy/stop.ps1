# Omni Data Panel 停机（保留数据卷）
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "未找到 docker。"
}

docker compose down
Write-Host "已停止容器。数据卷仍保留；彻底清除请执行: docker compose down -v" -ForegroundColor Green
