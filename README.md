# Omni Data Panel

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![CI](https://github.com/OmniSynth/omni-data-panel/actions/workflows/ci.yml/badge.svg)](https://github.com/OmniSynth/omni-data-panel/actions/workflows/ci.yml)

**自建、可控、不按席位收费的数据分析平台。**

付费 BI 往往把「看数」锁在授权席位、封闭插件和厂商托管里：方言受限、嵌入受限、权限模型不透明，成本随人数与数据源线性膨胀。Omni Data Panel 把分析能力交还给团队——源码可控、部署自有、连接你已有的数仓与业务库，用开放架构覆盖从取数到分享的完整链路。

**License:** [Apache License 2.0](LICENSE) · Copyright © 2025–2026 OmniSynth

### 定位与非目标

- **定位**：开源、**单租户自建** BI——一个组织部署一份实例，元数据与权限在该实例内管理。
- **非目标**：**不做**多客户共享集群的多租户 SaaS（无 `tenant_id` 隔离、无按租户计费/配额）。需要隔离时请分别部署多套实例。
- **组织内协作**：用角色、资源 ACL、集合与数据策略满足部门/项目权限，不等于多租户。

> 默认账号 `admin` / `admin123` 仅用于本地开发。生产必须通过 `ADMIN_INITIAL_PASSWORD` 设置至少 10 位的非默认初始密码，并替换 `deploy/.env` 中全部密码与密钥。

## 我们解决什么问题

| 付费 BI 常见痛点 | Omni 的做法 |
|---|---|
| 席位 / 模块按年付费 | 自建部署，能力不绑许可证 |
| 只开放「官方支持」的数据源 | 插件化方言：关系库 + ClickHouse / Hive / Spark |
| 嵌入与分享被厂商 API 绑架 | 公开链接 + **签名嵌入**（短期 JWT，服务端代签） |
| 共享看板泄露查询与底表细节 | 仪表盘安全渲染：只出结果与展示配置 |
| 原生 SQL 失控 | 只读策略、对象级表/列 ACL、审计可追溯 |
| 黑盒运维 | 连接池健康、登录/查询/模型/系统日志全链路可见 |

技术栈：Spring Boot 3 · Vue 3 · Vite 6 · ECharts 6 · MySQL 8 · Redis · MinIO。

## 架构

```text
Browser → nginx / Vue SPA → /api → Spring Boot
                                   ├── MySQL 8   元数据（Flyway）
                                   ├── Redis     查询状态 / 结果缓存
                                   └── MinIO     导出文件
```

| 目录 | 说明 |
|---|---|
| `server/` | Java 21、Spring Boot 3 后端（含 Maven Wrapper） |
| `web/` | Vue 3 前端 |
| `deploy/` | Docker Compose 与环境变量示例 |
| `docs/` | 使用手册、嵌入、OIDC、投产清单（应用内 `/help` 同步渲染） |

### 文档索引

| 文档 | 说明 |
|---|---|
| [docs/user-guide.md](docs/user-guide.md) | 使用手册：模型 / 图表 / 仪表盘 / 订阅 / **管理端设置·调度** |
| [docs/api-log-dashboard-guide.md](docs/api-log-dashboard-guide.md) | 实战：`sys_api_log` 看板（含截图） |
| [docs/embed-integration.md](docs/embed-integration.md) | 业务系统签名嵌入（域名白名单、JWT 锁定参数） |
| [docs/oidc-sso.md](docs/oidc-sso.md) | 企业 OIDC SSO |
| [docs/production.md](docs/production.md) | 生产密钥、探针、可信代理与加固清单 |
| [docs/observability.md](docs/observability.md) | Prometheus 指标、requestId、告警示例 |
| [deploy/README.md](deploy/README.md) | Compose / Release 一键部署 |
| [CHANGELOG.md](CHANGELOG.md) | 版本变更记录 |
| [CONTRIBUTING.md](CONTRIBUTING.md) | 贡献指南 |
| [SECURITY.md](SECURITY.md) | 安全漏洞披露 |

应用内 **帮助**（`/help`）与上表 `docs/*.md` **同源**渲染，改文档即更新页面。

## 完整功能

### 分析工作台

- **首页续看**：最近打开的图表、仪表盘、模型，降低「找资产」成本。
- **集合**：树状组织个人与共享空间；移动、重命名、角色共享；空集合可删。
- **全局搜索**：按权限过滤图表、仪表盘、模型、集合。
- **废纸篓**：软删除后可恢复或彻底清除。
- **顶栏创建**：集合、问题（图表）、SQL、仪表盘、模型一键入口。
- **中英双语**：登录页与顶栏切换（`localStorage` 键 `omni.locale`）；支持明暗主题。

### 数据连接与元数据

- **数据库类型（方言）**：`MYSQL`、`MARIADB`、`POSTGRESQL`、`MSSQL`、`ORACLE`、`CLICKHOUSE`、`HIVE`、`SPARK`（扩展点 `DialectPlugin`）。
- **连接生命周期**：创建 / 编辑 / 测试连通 / 元数据同步 / 删除；凭据 AES 加密存储。
- **命名空间**：
  - MySQL / MariaDB / ClickHouse / Hive / Spark：库名即命名空间，跨库用 `库.表`。
  - PostgreSQL / SQL Server / Oracle：连接须填库名或服务名；同步业务 schema，SQL 用 `schema.table`。
- **数据浏览器**：分析侧浏览已授权数据源的库表结构，与管理端连接维护分离。
- **驱动说明**：MySQL / MariaDB / PostgreSQL / SQL Server / Oracle 默认内置。ClickHouse 需自行加入 classpath（推荐 `com.clickhouse:clickhouse-jdbc:0.9.8:all` 并 exclusion 传递依赖）。Hive / Spark 共用 `jdbc:hive2`，本地可将 `hive-jdbc-*-standalone.jar` 放在 `server/lib/`（见 `pom.xml` system 依赖）；打包部署可用 `-Dloader.path=lib`。创建 Spark 数据源时须显式选择类型 `SPARK`。

### 语义层：模型与指标

- **模型（Dataset）**：物理表或自定义 SQL；字段推断；维度 / 度量类型。
- **指标（Metric）**：绑定模型字段的业务指标，语义查询通过 `metricIds` 引用。
- **数据策略**：字段权限、行级规则；数据源对象 ACL（表 / 列 deny）；原生 SQL 经解析校验，防止越权扫表。

### 取数：语义查询 + SQL

| 入口 | 权限 | 能力 |
|---|---|---|
| 查询工作台 `/query` | `query:execute` | 维度 / 度量 / 指标 / 过滤 / 排序 / limit；预览图表；保存为问题 |
| SQL 工作台 `/sql` | `query:raw` | 只读原生 SQL；方言高亮、补全、格式化；参数对齐；导出；保存为图表 |

后端查询：提交 → 状态轮询 → 可取消；语义查询编译为参数化 SQL；`SqlPolicyGuard` 仅允许单条 `SELECT` / `WITH`。

### 可视化：图表与仪表盘

- **图表类型**：表格、柱状 / 条形、折线、面积、组合图、饼图、散点、KPI、漏斗、地图等。
- **编码与下钻**：`configJson.encoding` 映射类目 / 数值；支持客户端下钻路径。
- **仪表盘布局**：GridStack 拖拽；多 Tab；多卡并行渲染且保持顺序。
- **交互参数**：文本 / 数字 / 日期 / 区间 / 单选 / 多选；静态选项或模型字段 DISTINCT；卡片绑定语义 filter 或 SQL `?`；点击类目写参并重渲染。
- **结果缓存**：站点级开关 `cache.query.enabled` 与 TTL；编辑页刷新 / 定时刷新可强制回源。
- **导出**：前端 PDF / PNG；查询结果 CSV / XLSX（同步或异步经 MinIO）。

### 分享与嵌入

| 方式 | 适用场景 | 要点 |
|---|---|---|
| 公开链接 | 对外只读分享 | 可撤销；可选有效期（默认永不过期） |
| 签名嵌入 | 嵌进业务系统 | 短期 JWT（约 1h）、开启 `embed.enabled`、配置嵌入域名白名单、**服务端代签**；签发时可写入仪表盘**锁定参数** |
| 打印页 | 订阅邮件 PDF | Playwright 无头打开 `/print/dashboard/{token}` |

仪表盘安全渲染（`/api/dashboards/{id}/render`）：以图表所有者权限执行已保存查询，只返回展示配置与结果（含实际合并后的 `parameterValues`），不向只读用户暴露查询 JSON、模型或数据源细节。公开链接使用参数默认值；签名嵌入优先应用 JWT 锁定参数，其余用默认值——访客均不可改参。

完整对接步骤见 [业务系统安全嵌入说明](docs/embed-integration.md)。

### 调度与订阅

- **订阅（产品 UI）**：侧栏「订阅」（`/subscriptions`，需 `subscription:manage`）按 Cron 将仪表盘邮件推送给站内用户；可手动触发；可选附带 PDF（`SUBSCRIPTION_PDF_ENABLED`）。
- **通用调度（产品 UI）**：管理端「调度」页（`/admin/schedules`，需 `schedule:manage`）管理 Quartz 任务：元数据同步、仪表盘后台刷新、按已有订阅发送；JobStore 为 JDBC（主库 `QRTZ_*`）并开启集群。
- **邮件**：管理端配置 SMTP 并测试发送；亦可使用环境变量 `MAIL_*`。

### 权限与安全

- **多角色 RBAC**：功能权限取启用角色并集；内置 `ADMIN` 受保护（不可编辑 / 禁用 / 删除 / 普通接口分配）。
- **功能权限示例**：`data-source:manage`、`dataset:manage`、`dashboard:manage`、`query:execute`、`query:raw`、`schedule:manage`、`subscription:manage`、`export:execute`。
- **资源 ACL**：集合 / 仪表盘 / 图表 / 模型 / 指标 / 数据源等按角色授予 `READ` / `WRITE`（取最高；`WRITE` 含 `READ`）。数据源角色仅 `READ`；连接维护仅 `ADMIN`。集合权限可继承；个人集合不可共享。
- **登录 hardening**：HMAC 登录挑战、JWT、可选 TOTP 双因素与备份码、并发会话上限（`auth.session.max-concurrent`）。
- **企业 SSO（OIDC）**：可选对接 IdP（`OIDC_*`）；保留本地密码登录；首次登录可 JIT 建号并赋予默认 `USER` 角色。详见 [docs/oidc-sso.md](docs/oidc-sso.md)。
- **限流与响应头**：登录 / 公开 / 嵌入按 IP 限流（Redis 优先，`omni.security.rate-limit.*`）；`TRUSTED_PROXIES` 控制是否信任 `X-Forwarded-For`；响应带 `X-Content-Type-Options`、`Referrer-Policy`、`Permissions-Policy`、CSP `frame-ancestors`。
- **审计**：登录、查询（含详情）、模型变更、**导出**、系统日志；支持清理。连接池健康页便于运维排障。

### 管理控制台（`/admin`）

| 分组 | 能力 |
|---|---|
| 系统 | 站点名称、嵌入开关与域名白名单、查询缓存、会话上限、SMTP；**订阅**（亦见产品端 `/subscriptions`）、**调度** |
| 人员 | 用户多角色、启停、密码重置、激活邮件、MFA 重置；角色与权限目录 |
| 数据源 | 连接维护、连接池健康、对象 ACL / 角色授权 |
| 审计 | 查询 / 登录 / 系统日志 / 模型审计 / **导出审计** |

通用设置键：`site.name`、`embed.enabled`、`embed.allowed-origins`、`cache.query.enabled`、`cache.query.ttl-seconds`、`auth.session.max-concurrent`、`mail.*`。

部署相关环境变量（详见 [production.md](docs/production.md)）：`TRUSTED_PROXIES`、`EMBED_ALLOWED_ORIGINS`、`OMNI_METRICS_TOKEN`、`OIDC_*`。

## 概念速查

| 产品用语 | API / 资源 |
|---|---|
| 数据源 | `data-sources` |
| 模型 | `datasets` |
| 指标 | `metrics` |
| 图表 / 问题 | `charts` / questions |
| 仪表盘 | `dashboards` |
| 集合 | `collections` |

## API 概览

业务 API 前缀 `/api`。公开端点：`/api/auth/login`、`/api/public/**`、`GET /api/embed/**`、`GET /actuator/health`、`GET /actuator/health/liveness`、`GET /actuator/health/readiness`；其余需 Bearer JWT。健康探针仅暴露 `health`（`show-details: never`）；liveness 表示进程存活，readiness 聚合元数据库与 Redis。

| 模块 | 路径 |
|---|---|
| 认证 | `/api/auth`（登录、挑战、MFA、当前用户、改密） |
| 组织 | `/api/collections`、`/recents`、`/search`、`/trash` |
| 指标 | `/api/metrics` |
| 身份 | `/api/users`、`/roles`、`/permissions` |
| 数据 | `/api/data-sources`、`/metadata`、对象 ACL |
| 模型 | `/api/datasets`、字段 DISTINCT、行/字段策略 |
| 查询 | `/api/queries` |
| 可视化 | `/api/charts`、`/dashboards`、`/dashboards/{id}/render` |
| 导出 / 调度 / 订阅 | `/api/exports`、`/schedules`、`/subscriptions` |
| 审计 | `/api/query-audits`、`/login-audits`、`/system-logs`、`/dataset-audits`、`/export-audits` |
| 授权 | `/api/resources/{type}/{id}/permissions` |
| 分享 | `/api/public-links`、`/public/**`、`/embed/**`（签发 `POST /embed/tokens` 可带 `parameters`） |
| 设置 | `/api/settings` |

## 快速开始

### 前置条件

- **一键部署（推荐）**：Docker Engine 24+、Compose v2（Windows / macOS 可用 Docker Desktop）
- **源码开发**：JDK 21、Node.js 22、MySQL 8；可选 Redis、MinIO

### GitHub Release 一键包（推荐）

打 `v*` tag 后，CI 会推送预构建镜像到 GHCR，并在 [Releases](https://github.com/OmniSynth/omni-data-panel/releases) 附上 `omni-data-panel-vX.Y.Z-compose.zip`。

1. 下载并解压 zip  
2. 复制 `.env.example` → `.env`，替换全部密码与密钥  
3. Windows：`.\start.ps1`；Linux / macOS：`chmod +x start.sh && ./start.sh`  
4. 浏览器打开 `http://localhost`（账号 `admin` / `.env` 中 `ADMIN_INITIAL_PASSWORD`）

无需分别启动前后端。说明见 [deploy/README.md](deploy/README.md)。

发布新版本（维护者）：

```bash
git tag v0.1.0
git push origin v0.1.0
```

首次发布后请到 GitHub → Packages，将 `omni-data-panel-server` / `omni-data-panel-web` 设为 **Public**，否则匿名无法 `docker pull`。

### 源码目录 Compose（本地构建）

在仓库内从源码构建镜像（开发或镜像尚未发布时）：

```powershell
Copy-Item deploy/.env.example deploy/.env
cd deploy
.\start.ps1
# 若 pull 失败：docker compose up --build -d
```

```bash
cp deploy/.env.example deploy/.env
cd deploy
chmod +x start.sh stop.sh
./start.sh
# 若 pull 失败：docker compose up --build -d
```

- Web：`http://localhost`
- API：`http://localhost:8080`
- MinIO Console：`http://localhost:9001`

Compose 以生产配置启动后端：首次若 `admin` 仍为开发默认密码，将用 `ADMIN_INITIAL_PASSWORD` 替换（空、等于 `admin123` 或少于 10 位会拒绝启动）。之后不会自动重置密码。

### 本地开发

后端：

```powershell
cd server
$env:DB_URL="jdbc:mysql://localhost:3306/omni_panel?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:DB_USERNAME="<用户>"
$env:DB_PASSWORD="<密码>"
.\mvnw.cmd spring-boot:run
```

```bash
cd server
DB_URL='jdbc:mysql://localhost:3306/omni_panel?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
DB_USERNAME='<用户>' DB_PASSWORD='<密码>' ./mvnw spring-boot:run
```

前端：

```bash
cd web
npm ci
npm run dev
```

开发服务器：`http://localhost:5173`，`/api` 代理到 `127.0.0.1:8080`（可用 `VITE_API_TARGET` 覆盖）。

### 环境变量要点

完整示例见 `deploy/.env.example`（实际 `deploy/.env` 已被 Git 忽略）。

| 类别 | 变量 |
|---|---|
| 数据库 | `MYSQL_*`、`DB_URL` / `DB_USERNAME` / `DB_PASSWORD` |
| Redis / MinIO | `REDIS_*`、`MINIO_*` |
| 安全 | `JWT_SECRET`、`CREDENTIAL_MASTER_KEY`、`ADMIN_INITIAL_PASSWORD` |
| OIDC（可选） | `OIDC_ENABLED`、`OIDC_ISSUER_URI`、`OIDC_CLIENT_ID`、`OIDC_CLIENT_SECRET`、`OIDC_CLIENT_NAME`、`OIDC_DEFAULT_ROLE_CODE` |
| 邮件 / 订阅 | `MAIL_*`、`FRONTEND_URL`、`SUBSCRIPTION_PDF_ENABLED`、`SUBSCRIPTION_PDF_TIMEOUT_MS` |
| 端口 | `SERVER_PORT`、`WEB_PORT` |
| 镜像（Compose） | `OMNI_SERVER_IMAGE`、`OMNI_WEB_IMAGE`（Release 包已写入版本 tag） |

生产部署前用 `openssl rand -base64 32` 重新生成密钥，替换全部示例密码；完整核对项见 [docs/production.md](docs/production.md)。订阅 PDF 在官方 server 镜像内已含 Chromium；源码本地跑订阅 PDF 需自行安装：

```bash
cd server
./mvnw.cmd exec:java -e "-Dexec.mainClass=com.microsoft.playwright.CLI" "-Dexec.args=install chromium"
```

未配置 SMTP 时可保存订阅，发送任务会明确失败。

## 构建与测试

CI（GitHub Actions）：推送 / PR 到 `main` 或 `master` 时并行跑后端 `mvnw verify` 与前端 `npm test` + `npm run build`。推送 `v*` tag 时运行 Release 流水线（构建推送 GHCR 镜像并上传 Compose 一键包）。默认 CI **不**跑依赖全栈的 Playwright e2e。

```powershell
cd server
.\mvnw.cmd clean verify
```

```bash
cd server && ./mvnw clean verify
# Testcontainers（需 Docker，且显式开启）
./mvnw -Domni.test.docker=true test
```

```bash
cd web
npm ci
npm test          # Vitest 单元测试
npm run build
# e2e（可选）：需已启动前后端，并设置 E2E_USERNAME / E2E_PASSWORD
npm run test:e2e
```

## 设计原则

1. **能力在你这边**：部署、数据、密钥、审计日志不离开你的环境。
2. **分享不泄底**：嵌入与只读渲染优先「结果可见、定义可控」。
3. **方言可扩展**：数仓与关系库同一套连接 / 查询 / 元数据插件模型。
4. **权限可解释**：功能权限 + 资源 ACL + 行/列策略 + SQL 门禁，层层可审计。

付费 BI 卖的是封闭与席位；Omni Data Panel 交付的是**可拥有的分析基础设施**。

## 许可证

本项目以 [Apache License 2.0](LICENSE) 发布（SPDX: `Apache-2.0`）。见 [NOTICE](NOTICE)。

欢迎贡献：请阅读 [CONTRIBUTING.md](CONTRIBUTING.md) 与 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)。安全问题请按 [SECURITY.md](SECURITY.md) 私下报告。
