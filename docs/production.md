# 生产部署清单

面向**单组织 / 单租户自建**投产（开源项目默认模型：一组织一实例）。

**不在范围**：多客户同集群的多租户 SaaS、按租户计费/配额、强合规对外托管。需要隔离请部署多套实例，勿期望库内 `tenant` 分区。

## 1. 启动前必做

| 项 | 要求 |
|---|---|
| `JWT_SECRET` | 独立 32 字节密钥，Base64；禁止沿用 `deploy/.env.example` 示例值 |
| `CREDENTIAL_MASTER_KEY` | 独立 32 字节 AES 密钥，Base64；与 JWT 密钥分开 |
| `ADMIN_INITIAL_PASSWORD` | ≥10 位且非 `admin123`；仅在 admin 仍为开发默认密码时生效一次 |
| MySQL / Redis / MinIO 口令 | 全部替换示例密码 |
| `FRONTEND_URL` | 浏览器可访问的 Web 根地址（订阅链接、OIDC 回调推导依赖） |

生成密钥示例：

```bash
openssl rand -base64 32
```

生产配置启动时，`SecurityBootstrap` 会拒绝弱 JWT / 主密钥，以及未设置合格初始管理员密码的情况。

## 2. 推荐 Compose / 反代

**首选：GitHub Release 一键包**（预构建镜像，无需分别启前后端）：

1. 从 [Releases](https://github.com/OmniSynth/omni-data-panel/releases) 下载 `omni-data-panel-vX.Y.Z-compose.zip` 并解压。
2. 复制 `.env.example` → `.env` 并按上表改密。
3. Windows：`.\start.ps1`；Linux/macOS：`./start.sh`（内部 `docker compose pull && up -d`）。
4. 对外只暴露 Web（nginx）与必要的 API；MinIO Console、MySQL、Redis **不要**对公网开放。
5. TLS 终止放在前置反向代理；保证代理正确传递 `X-Forwarded-For` / `X-Forwarded-Proto`，并配置 `TRUSTED_PROXIES`（见 §4）。

**源码目录构建**（开发或镜像未发布时）：在仓库 `deploy/` 下配置 `.env` 后执行 `docker compose up --build -d`。

### 从 Release 升级

1. 下载新版 zip，覆盖 `compose.yml` / 脚本（**保留**现有 `.env`）。
2. 将 `.env` 中 `OMNI_SERVER_IMAGE`、`OMNI_WEB_IMAGE` 的 tag 改为新版本（如 `v0.2.0`）。
3. 再执行 `start.ps1` / `start.sh`。
4. 数据在 Docker 卷中，升级一般无需重建 MySQL/MinIO 卷；重大迁移以 Flyway 为准。

健康探针（仅暴露 `health`，无详情）：

| 探针 | 路径 |
|---|---|
| 存活 | `GET /actuator/health/liveness` |
| 就绪 | `GET /actuator/health/readiness`（聚合元数据库与 Redis） |

## 3. 可选能力开关

| 能力 | 配置 | 说明 |
|---|---|---|
| 签名嵌入 | 管理端 `embed.enabled` | 业务系统 iframe 嵌入前必须开启 |
| 嵌入域名白名单 | `embed.allowed-origins` + `EMBED_ALLOWED_ORIGINS` | CSP `frame-ancestors`；Compose 环境变量须与设置一致 |
| 查询缓存 | `cache.query.enabled` / TTL | 站点级；编辑页可强制回源 |
| 企业 SSO | `OIDC_*` | 见 [oidc-sso.md](oidc-sso.md) |
| 订阅 PDF | `SUBSCRIPTION_PDF_ENABLED` | 需镜像内 Playwright Chromium |
| 并发会话 | `auth.session.max-concurrent` | 超限踢最旧会话 |
| Prometheus 指标 | `OMNI_METRICS_TOKEN` | 配置后开放 `/actuator/prometheus`；见 [observability.md](observability.md) |

## 4. 安全加固（投产前核对）

### 已内置

- HMAC 登录挑战 + JWT + 可选 TOTP MFA
- 登录 / 公开链接 / 嵌入接口按 IP 限流（默认鉴权 30/分、公开 120/分、嵌入 180/分）；**Redis 固定窗口优先**，不可用时降级本机 Caffeine
- 可信代理：`TRUSTED_PROXIES`（CIDR）；仅当 `remoteAddr` 属于白名单时才解析 `X-Forwarded-For`（自右向左剥可信跳）；默认空 = 不信任转发头
- 响应头：`X-Content-Type-Options`、`Referrer-Policy`、`Permissions-Policy`、CSP `frame-ancestors`（嵌入域名白名单）
- Quartz **JDBC 集群**（多实例共享触发器，避免重复订阅）
- 只读 SQL 门禁、对象 ACL、资源 ACL、登录/查询/模型审计

### 运维须自行收口

| 风险 | 建议 |
|---|---|
| OIDC 按邮箱绑定账号 | 确保 IdP 邮箱已验证；见 [oidc-sso.md](oidc-sso.md) §安全注意 |
| Hive / ClickHouse 驱动 | 默认胖包不含 ClickHouse；Hive 需 `server/lib` 或 `-Dloader.path`（见 README 驱动说明） |
| 管理端白名单与 nginx 环境变量不同步 | 修改 `embed.allowed-origins` 后同步更新 `EMBED_ALLOWED_ORIGINS` 并重启 web 容器 |
| `TRUSTED_PROXIES` 过宽且 server 对公网可达 | 攻击者可直连并伪造 XFF；仅在前置代理后暴露 API，或收紧为代理 IP/CIDR |

## 5. 调度与订阅

- **仪表盘订阅**：管理端「系统 → 订阅」配置 Cron 与收件人；产品 UI 已覆盖。
- **通用调度**：管理端「系统 → 调度」（`/admin/schedules`，需 `schedule:manage`）配置元数据同步、仪表盘后台刷新、按已有订阅发送；Quartz JobStore 为 JDBC 并开启集群。

## 6. 备份与演练

至少覆盖：

1. MySQL 元库（含 Flyway 版本、`QRTZ_*`、用户与 ACL）
2. MinIO 导出桶（若业务依赖历史导出）
3. `JWT_SECRET` / `CREDENTIAL_MASTER_KEY` 的离线保管与轮转预案（轮转会使旧 JWT / 已加密数据源凭据失效，需配套重登与凭据重录流程）

## 7. 验证清单

- [ ] `GET /actuator/health/readiness` 返回 UP
- [ ] 管理员可登录；默认 `admin123` 已不可用
- [ ] 创建数据源 → 同步元数据 → 语义查询出数
- [ ] （若启用）OIDC 登录闭环与 JIT 角色符合预期；IdP 已强制 MFA / 邮箱验证
- [ ] （若启用）签名嵌入：管理端白名单含业务 Origin，且 `EMBED_ALLOWED_ORIGINS` 已同步；iframe 可加载
- [ ] （若启用）订阅任务在多实例下不重复发送；调度页可创建元数据同步任务
- [ ] 故意输错密码多次，观察鉴权限流是否返回 429（多副本时配额在 Redis 合计）
- [ ] （若启用）Prometheus 刮取带 `OMNI_METRICS_TOKEN` 成功；系统日志可按 `requestId` 检索

## 8. 相关文档

- 产品总览与本地开发：[README.md](../README.md)
- Compose 一键部署：[deploy/README.md](../deploy/README.md)
- 可观测性：[observability.md](observability.md)
- OIDC SSO：[oidc-sso.md](oidc-sso.md)
- 签名嵌入：[embed-integration.md](embed-integration.md)
- 使用手册（含管理端调度/设置）：[user-guide.md](user-guide.md)

应用内 **帮助**（`/help`）与上述 `docs/*.md` 同源渲染。
