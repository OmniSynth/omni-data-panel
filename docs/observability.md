# 可观测性

覆盖 Prometheus 指标、请求级 `requestId` 日志关联，以及可导入的告警规则示例。不内嵌 Grafana/Alertmanager；对接你现有的监控栈。

## 指标（Prometheus）

端点：`GET /actuator/prometheus`

| 条件 | 行为 |
|---|---|
| 未配置 `OMNI_METRICS_TOKEN` | 返回 **404**（不暴露） |
| 已配置令牌 | 需 `Authorization: Bearer <token>` 或 `X-Metrics-Token: <token>`；错误返回 401 |

```bash
curl -sS -H "Authorization: Bearer $OMNI_METRICS_TOKEN" \
  http://127.0.0.1:8080/actuator/prometheus | head
```

**不要**经公网 nginx 反代该路径；仅在内网或监控网刮取 `SERVER_PORT`。

### 业务指标

| 名称（Prometheus） | 说明 |
|---|---|
| `omni_auth_login_total{result}` | 登录：`success` / `failure` / `mfa_required` |
| `omni_http_rate_limited_total{bucket}` | 限流 429：`auth` / `public` / `embed` |
| `omni_query_submit_total` | 查询提交次数 |
| `omni_query_complete_total{status}` | 查询结束：`succeeded` / `failed` / `cancelled` |
| `omni_query_duration_seconds_*` | 查询耗时（按 status） |

另有 Spring Boot 默认 JVM / HTTP server 指标；全局 tag `application=omni-data-panel`。

刮取配置示例见 [deploy/observability/prometheus-scrape.example.yml](../deploy/observability/prometheus-scrape.example.yml)。

## 日志关联（requestId）

1. 过滤器为每个请求生成或透传 `X-Request-Id`（合法字符：`A-Za-z0-9._-`，长度 8–128）。
2. 写入 MDC `requestId`，控制台日志 pattern 含 `[%X{requestId}]`。
3. 响应头回写 `X-Request-Id`。
4. 管理端「系统日志」条目含 `requestId` 字段，可用关键字搜索。

联查步骤：从浏览器 Network 面板或客户端日志取响应头 `X-Request-Id` → 管理端系统日志搜索该 id。

## 审计落库（管理端）

除 Prometheus 外，产品内审计页支持排障与合规留痕（非时序指标）：

| 审计 | 管理端入口 | 说明 |
|------|------------|------|
| 登录 | 登录日志 | 成功 / 失败 / MFA |
| 查询 | 查询日志 | 含详情与耗时 |
| 模型 | 模型审计 | 数据集变更 |
| 导出 | 导出日志 | CSV/XLSX、仪表盘 PNG/PDF 等 |
| 系统 | 系统日志 | 可按 `requestId` 检索 |

清理能力受设置 `logs.clear.enabled` 控制。

## 告警

示例规则：[deploy/observability/alerts.yml](../deploy/observability/alerts.yml)

导入到 Prometheus / Prometheus Operator / Grafana-managed alerts 后，按你的 Alertmanager 路由发送。

建议至少关注：

- 目标 `up == 0`
- 限流速率突增
- 查询失败率
- JVM 堆占用过高

## 配置

| 变量 | 说明 |
|---|---|
| `OMNI_METRICS_TOKEN` | Prometheus 刮取令牌；空则关闭端点 |

Compose 见 `deploy/.env.example` 与 `deploy/compose.yml` 中的 `OMNI_METRICS_TOKEN`。

限流计数在 Redis 可用时跨实例共享（失败降级本机）；指标 `omni_http_rate_limited_total` 仍按实例导出，聚合时注意多副本求和。

## 相关文档

- 投产清单：[production.md](production.md)
- 使用手册：[user-guide.md](user-guide.md)

应用内帮助页（`/help?tab=observability`）可直接阅读本说明。
