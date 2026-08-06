# Omni Data Panel 一键部署

用 Docker Compose 同时启动 Web、API、MySQL、Redis、MinIO，**无需分别启动前后端**。

部署模型为**单租户自建**（一组织一实例）；不做多客户同集群多租户。多环境隔离请起多套 Compose / 多套 `.env`。

## 前置条件

- Docker Engine 24+ 与 Compose v2（Windows / macOS 可用 Docker Desktop）
- 首次启动需能访问：
  - `ghcr.io`（拉取 `omni-data-panel-server` / `omni-data-panel-web`）
  - Docker Hub（MySQL / Redis / MinIO 基础镜像）

若 GHCR 包为私有，需先 `docker login ghcr.io`；公开包可直接拉取。仓库维护者请在 GitHub Packages 将两个包设为 **Public**。

投产变量说明见 [docs/production.md](../docs/production.md)（`TRUSTED_PROXIES`、`EMBED_ALLOWED_ORIGINS`、`OMNI_METRICS_TOKEN` 等）。应用启动后也可打开 **帮助**（`/help`）阅读同源文档。

## 三步启动

1. 准备环境文件：

```powershell
Copy-Item .env.example .env
# 编辑 .env：替换全部密码与 JWT_SECRET / CREDENTIAL_MASTER_KEY
```

```bash
cp .env.example .env
# 编辑 .env
```

2. 启动：

```powershell
.\start.ps1
```

```bash
chmod +x start.sh stop.sh
./start.sh
```

首次若尚无 `.env`，脚本会自动从 `.env.example` 复制并退出，请改密后再跑一次。

3. 浏览器打开 Web 地址（默认 `http://localhost`），使用：

- 用户名：`admin`
- 密码：`.env` 中的 `ADMIN_INITIAL_PASSWORD`

| 服务 | 默认地址 |
|---|---|
| Web | `http://localhost` |
| API | `http://localhost:8080` |
| MinIO Console | `http://localhost:9001` |

## 停机与升级

```powershell
.\stop.ps1
```

```bash
./stop.sh
```

升级到新 Release：

1. 用新版 zip 覆盖本目录文件（保留你的 `.env`）。
2. 将 `.env` 中 `OMNI_SERVER_IMAGE` / `OMNI_WEB_IMAGE` 的 tag 改为新版本（如 `v0.2.0`）。
3. 再执行 `start.ps1` / `start.sh`（内部会 `pull` + `up -d`）。

## 源码目录本地构建

若在 Git 仓库的 `deploy/` 下开发，可忽略预构建镜像、本地构建：

```bash
docker compose up --build -d
```

## 更多

- 生产加固：[docs/production.md](../docs/production.md)
- OIDC：[docs/oidc-sso.md](../docs/oidc-sso.md)
- 签名嵌入：[docs/embed-integration.md](../docs/embed-integration.md)
