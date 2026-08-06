#!/usr/bin/env sh
# Omni Data Panel 停机（保留数据卷）
set -eu
cd "$(dirname "$0")"

if ! command -v docker >/dev/null 2>&1; then
  echo "未找到 docker。" >&2
  exit 1
fi

docker compose down
echo "已停止容器。数据卷仍保留；彻底清除请执行: docker compose down -v"
