#!/usr/bin/env sh
# Omni Data Panel 一键启动（Docker Compose）
set -eu
cd "$(dirname "$0")"

if ! command -v docker >/dev/null 2>&1; then
  echo "未找到 docker。请先安装 Docker Engine 与 Compose v2。" >&2
  exit 1
fi
if ! docker compose version >/dev/null 2>&1; then
  echo "docker compose 不可用。请升级到 Compose v2。" >&2
  exit 1
fi

if [ ! -f .env ]; then
  if [ ! -f .env.example ]; then
    echo "缺少 .env.example，无法生成 .env。" >&2
    exit 1
  fi
  cp .env.example .env
  echo "已从 .env.example 创建 .env，请先编辑其中的密码与密钥后再启动。"
  echo "编辑完成后重新运行: ./start.sh"
  exit 0
fi

echo "拉取镜像..."
if ! docker compose pull; then
  echo "pull 失败（若镜像尚未发布，可改用: docker compose up --build -d）"
fi

echo "启动服务..."
docker compose up -d

web_port=$(grep -E '^\s*WEB_PORT\s*=' .env | tail -n1 | cut -d= -f2- | tr -d ' \r' || true)
api_port=$(grep -E '^\s*SERVER_PORT\s*=' .env | tail -n1 | cut -d= -f2- | tr -d ' \r' || true)
minio_port=$(grep -E '^\s*MINIO_CONSOLE_PORT\s*=' .env | tail -n1 | cut -d= -f2- | tr -d ' \r' || true)
web_port=${web_port:-80}
api_port=${api_port:-8080}
minio_port=${minio_port:-9001}

if [ "$web_port" = "80" ]; then
  web_url="http://localhost"
else
  web_url="http://localhost:${web_port}"
fi

echo ""
echo "已启动。首次就绪可能需要 1–2 分钟（数据库初始化）。"
echo "  Web:           ${web_url}"
echo "  API:           http://localhost:${api_port}"
echo "  MinIO Console: http://localhost:${minio_port}"
echo "  账号:          admin / (.env 中 ADMIN_INITIAL_PASSWORD)"
echo "停机: ./stop.sh"
