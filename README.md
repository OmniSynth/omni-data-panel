# Omni Data Panel

Omni Data Panel 是基于 Spring Boot 3、Vue 3、Vite 6 与 ECharts 6 的 Metabase 式数据分析平台：集合组织内容、首页续看、模型/指标、图表与仪表盘、废纸篓、公开分享与嵌入，并保留多角色 RBAC 与数据源/仪表盘角色授权。

> 默认账号 `admin`、密码 `admin123` 仅用于本地开发。生产部署必须通过 `ADMIN_INITIAL_PASSWORD` 设置至少10位的非默认初始密码，并替换 `deploy/.env` 中的全部密码和密钥。

## 架构

浏览器访问 nginx 托管的 Vue 单页应用，`/api` 请求由 nginx 转发到 Spring Boot。后端使用 MySQL 8 保存元数据，Redis 保存查询状态，MinIO 保存导出文件。

```text
Browser -> nginx/web -> server
                         |-- MySQL 8
                         |-- Redis
                         `-- MinIO
```

## 目录

- `server/`：Java 21、Spring Boot 3 后端及 Maven Wrapper
- `web/`：Vue 3、Vite 6、ECharts 6 前端
- `deploy/`：Docker Compose 与环境变量示例

后端 Java 包按技术层划分：`controller`（接口）、`service`（实现）、`mapper`（持久化）、`entity`（实体）、`config`（安全等配置）；查询引擎、方言等基础设施仍保留在 `query`、`datasource` 等包。

## 前置条件

本地开发：

- JDK 21
- Node.js 22 与 npm
- MySQL 8
- 可选：Redis、MinIO

容器运行：

- Docker Engine 24+
- Docker Compose v2

## 本地启动后端

先准备 MySQL 数据库，再设置后端环境变量。开发配置自带的密钥和默认数据库凭据只适用于本机开发。

PowerShell：

```powershell
cd server
$env:DB_URL="jdbc:mysql://localhost:3306/omni_panel?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:DB_USERNAME="<本地数据库用户>"
$env:DB_PASSWORD="<本地数据库密码>"
.\mvnw.cmd spring-boot:run
```

Linux/macOS：

```bash
cd server
DB_URL='jdbc:mysql://localhost:3306/omni_panel?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
DB_USERNAME='<本地数据库用户>' \
DB_PASSWORD='<本地数据库密码>' \
./mvnw spring-boot:run
```

后端默认监听 `http://0.0.0.0:8080`（本机可用 `http://localhost:8080`），Flyway 会在启动时初始化或升级元数据库。

## 本地启动前端

```bash
cd web
npm ci
npm run dev
```

前端默认监听 `http://localhost:5173`，并同时绑定局域网地址（`0.0.0.0`）。开发服务器将 `/api` 代理到 `http://127.0.0.1:8080`。如需修改目标地址，设置 `VITE_API_TARGET`。

同网段设备可通过本机局域网 IP 访问，例如 `http://192.168.x.x:5173`。启动前端后终端会打印 Network 地址；若无法访问，请在 Windows 防火墙中放行 `5173`（前端）与 `8080`（后端）端口。

后端默认监听 `0.0.0.0:8080`，可用 `SERVER_ADDRESS` / `SERVER_PORT` 覆盖。

## Docker Compose 启动

复制开发环境示例：

PowerShell：

```powershell
Copy-Item deploy/.env.example deploy/.env
cd deploy
docker compose up --build -d
```

Linux/macOS：

```bash
cp deploy/.env.example deploy/.env
cd deploy
docker compose up --build -d
```

查看状态和日志：

```bash
cd deploy
docker compose ps
docker compose logs -f server web
```

停止服务：

```bash
cd deploy
docker compose down
```

如需同时删除 MySQL、Redis、MinIO 数据卷，使用 `docker compose down -v`。默认入口：

- Web：`http://localhost`
- 后端：`http://localhost:8080`
- MinIO Console：`http://localhost:9001`

Compose 文件位于 `deploy/`，其中构建上下文使用 `..` 指向仓库根目录；请勿将它改为以执行命令所在目录为基准的路径。

Compose 以生产配置启动后端。首次启动时，如果数据库中的 `admin` 仍使用开发默认密码，后端会使用 `ADMIN_INITIAL_PASSWORD` 替换它；该变量为空、等于 `admin123` 或少于10位时会拒绝启动。管理员密码已被修改后，后续启动不会重置。登录后可在右上角“修改密码”中继续更换密码，成功后需重新登录。

## 环境变量

`deploy/.env.example` 提供完整的本地开发示例，实际运行文件为 `deploy/.env`，该文件已被 Git 忽略。

数据库：

- `MYSQL_DATABASE`、`MYSQL_USER`、`MYSQL_PASSWORD`、`MYSQL_ROOT_PASSWORD`
- `MYSQL_PORT`：宿主机 MySQL 端口

Redis：

- `REDIS_PASSWORD`
- `REDIS_PORT`：宿主机 Redis 端口

MinIO：

- `MINIO_ROOT_USER`、`MINIO_ROOT_PASSWORD`
- `MINIO_BUCKET`：导出文件桶，启动时由 `minio-init` 自动创建
- `MINIO_API_PORT`、`MINIO_CONSOLE_PORT`

后端安全：

- `JWT_SECRET`：Base64 编码的至少 32 字节 HMAC 密钥
- `CREDENTIAL_MASTER_KEY`：Base64 编码的 32 字节 AES 密钥
- `ADMIN_INITIAL_PASSWORD`：生产环境首次启动的管理员密码，至少10位且不能为 `admin123`

订阅邮件（可选）：

- `MAIL_HOST`、`MAIL_PORT`、`MAIL_USERNAME`、`MAIL_PASSWORD`：SMTP 连接信息
- `MAIL_SMTP_AUTH`：是否启用 SMTP 认证
- `MAIL_STARTTLS`：是否启用 STARTTLS
- `MAIL_FROM`：订阅邮件发件人
- `FRONTEND_URL`：邮件中仪表盘链接使用的前端地址

邮件变量不使用 Compose 强制校验。未配置邮件服务时订阅仍可创建和保存，但任务执行发送时会明确失败。

端口：

- `SERVER_PORT`：宿主机后端端口
- `WEB_PORT`：宿主机 Web 端口

生产部署前必须重新生成两个安全密钥，例如使用 `openssl rand -base64 32`，并替换数据库、Redis、MinIO 的全部示例密码。不要提交真实 `.env`。

## 产品导览

- **分析壳**：首页续看、集合树、数据源浏览、模型、指标、图表、仪表盘、搜索、废纸篓；顶栏「+ 创建」；顶栏/登录页可切换中英文（`localStorage` 键 `omni.locale`）。
- **管理壳** `/admin`：通用设置、数据源连接维护、用户、角色、数据权限、订阅（仅 ADMIN）。
- **概念映射**：数据源=`data-sources`；模型=`datasets`；图表=`charts`；指标=`metrics`；集合=`collections`。
- **分析库方言**：运行时已注册 `MYSQL`、`MARIADB`、`POSTGRESQL`、`MSSQL`、`ORACLE`、`CLICKHOUSE`、`HIVE`（插件扩展点 `DialectPlugin`）。
  - MySQL/MariaDB/ClickHouse/Hive：库名即可选命名空间；跨库用 `库.表`。
  - PostgreSQL/SQL Server/Oracle：连接须填库名/服务名（写入 JDBC URL）；元数据同步枚举其下业务 schema，SQL 使用 `schema.table`。
  - 默认已内置 MySQL/MariaDB/PostgreSQL/SQL Server/Oracle 驱动；**ClickHouse / Hive 需自行将 JDBC 驱动加入运行时 classpath**（避免有缺陷的驱动经 SPI 拖垮 Flyway/元数据库启动）。ClickHouse 推荐 `com.clickhouse:clickhouse-jdbc:0.9.8:all` 并 exclusion 全部传递依赖。
- **通用设置键**：`site.name`、`embed.enabled`；查询结果缓存 `cache.query.enabled`（默认关闭）、`cache.query.ttl-seconds`（默认 300，范围 30–86400）。开启后仪表盘/公开图表渲染可复用 Redis 中的图表结果；编辑页「刷新卡片」与定时刷新会强制重新查询并回写缓存。
- **仪表盘参数**：在 `configJson.parameters` 定义文本/数字/日期/区间/单选/多选控件；卡片 `bindings_json` 可绑定到语义 filter 或 SQL `?` 占位；`click_action_json` 支持点击图表类目写入参数并重渲染。登录查看使用 `POST /api/dashboards/{id}/render` 传参；公开/嵌入仅使用默认值。单选/多选可配置 `optionsFrom`（模型字段 DISTINCT，接口 `GET /api/datasets/{id}/fields/{field}/distinct`），也可使用静态 `options`。图表 `configJson.encoding` 可显式映射类目/数值列，并支持 `combo` 组合图；仪表盘多卡渲染并行执行且保持卡片顺序。

## API 模块

所有业务 API 使用 `/api` 前缀：

- `/api/auth`：登录、当前用户与修改密码
- `/api/collections`、`/api/recents`、`/api/search`、`/api/trash`：集合、续看、搜索、废纸篓
- `/api/metrics`：指标（可按 `modelId` 过滤；语义查询通过 `query.metricIds` 引用）
- `/api/roles`、`/api/permissions`：角色管理与功能权限目录（仅 ADMIN）
- `/api/users`：用户、多角色绑定与密码重置（仅 ADMIN）
- `/api/data-sources`：数据源管理与连通性测试
- `/api/data-sources/{sourceId}/metadata`：元数据同步与浏览
- `/api/datasets`：模型（原数据集）及字段、行权限策略
- `/api/datasets/{id}/fields/{field}/distinct`：模型字段去重取值（参数动态选项）
- `/api/queries`：查询提交、状态与取消
- `/api/charts`、`/api/dashboards`：图表与仪表盘
- `/api/exports`：同步及异步导出
- `/api/schedules`、`/api/subscriptions`：调度与订阅
- `/api/resources/{resourceType}/{resourceId}/permissions`：角色资源授权
- `/api/dashboards/{id}/render`：不暴露查询定义的仪表盘安全渲染
- `/api/public-links`、`/api/public/**`、`/api/embed/**`：公开链接与签名嵌入
- `/api/settings`：站点名称与嵌入开关（读：登录用户；写：ADMIN）

公开端点：`/api/auth/login`、`/api/public/**`、`GET /api/embed/**`、`/actuator/health`。其余 API 需要 Bearer JWT。

## 权限模型

用户可绑定多个启用角色，功能权限取所有角色权限的并集；`/api/auth/me` 的 `roles` 返回当前启用角色编码。`ADMIN` 是受保护的内置角色，不能编辑、禁用、删除，也不能通过普通用户管理接口分配或移除。

资源权限按角色授予。同一用户通过多个角色获得不同级别时取最高权限，`WRITE` 包含 `READ`。数据源只支持 `READ` 授权，创建、编辑、删除、测试和元数据同步仅限 `ADMIN`。仪表盘支持 `READ` 与 `WRITE`：所有者天然保留写权限，`WRITE` 可编辑和删除，`READ` 只能查看；角色共享仅由 `ADMIN` 管理。

仪表盘的角色读取使用 `/api/dashboards/{id}/render`。后端以每张图表所有者的实时权限执行已保存查询，只返回图表展示配置和查询结果，不向读取者暴露查询 JSON、数据集或数据源信息。字段权限和行级策略仍按用户作用于数据集查询。

## 构建与测试

后端完整测试：

PowerShell：

```powershell
cd server
.\mvnw.cmd clean verify
```

Linux/macOS：

```bash
cd server
./mvnw clean verify
```

Testcontainers MySQL 集成测试需要显式启用：

```bash
cd server
./mvnw -Domni.test.docker=true test
```

该测试使用 `disabledWithoutDocker = true`。未安装 Docker、Docker 守护进程不可用，或未设置 `omni.test.docker=true` 时，Testcontainers 测试会跳过，不代表测试失败。

前端构建与端到端测试：

```bash
cd web
npm ci
npm run build
npm run test:e2e
```

端到端测试执行前需先启动对应的前后端服务。
