# 业务系统安全嵌入对接说明

本文说明如何将 Omni Data Panel 的仪表盘或图表，以相对安全的方式嵌套到业务系统中。

**业务系统请使用「签名嵌入」，不要使用永久公开链接。**

## 1. 概述

| 能力 | 页面地址 | 有效期 | 撤销 | 适用场景 |
|------|----------|--------|------|----------|
| 公开链接 | `/public/dashboard/{token}` | 不过期 | 可在 UI 中撤销 | 对外临时分享 |
| **签名嵌入** | `/embed/dashboard/{jwt}` | **固定 1 小时** | 过期即失效；可关全局开关 | **内嵌业务系统（推荐）** |

签名嵌入的安全模型：

1. 业务系统**服务端**用具备资源写权限的账号登录 Omni，拿到用户 JWT。
2. 用户打开业务页时，业务后端调用签发接口，获得短期 embed JWT。
3. 业务页用 iframe 加载 `/embed/dashboard/{embedJwt}`；前端页面内部再请求 `/api/embed/...` 拉取只读渲染结果。
4. 渲染以**资源所有者**身份执行，参数仅使用仪表盘配置中的**默认值**（不支持访客交互改参）。

```text
业务浏览器
    │
    │  打开业务页
    ▼
业务后端 ──POST /api/auth/login──────────────► Omni（缓存用户 JWT，勿下发浏览器）
    │
    │  POST /api/embed/tokens  Authorization: Bearer <用户JWT>
    ▼
Omni 返回 embed JWT（1h）
    │
    ▼
业务页 iframe.src = https://{omni}/embed/dashboard/{embedJwt}
    │
    ▼
嵌入页 GET /api/embed/dashboards/{embedJwt} → 只读图表结果
```

## 2. 前置条件

1. **开启嵌入**：管理端「设置」中打开「允许嵌入」（配置键 `embed.enabled`）。关闭后签发与解析均会失败。
2. **服务账号**：准备一个具备目标仪表盘或图表 **WRITE** 权限的账号（所有者或被授予 WRITE 的角色）。签发接口会校验写权限。
3. **网络**：业务前端所在浏览器能访问 Omni 的 Web 基址（iframe 与 `/api`）。服务端已关闭 `X-Frame-Options`（未设置限制性 `frame-ancestors`），任意父页面均可嵌套；另下发 `X-Content-Type-Options: nosniff`、`Referrer-Policy`、`Permissions-Policy`。若后续加嵌入域名白名单，需同步调整业务域名。
4. **限流**：`/api/embed/**` 与 `/api/public/**` 按客户端 IP 做进程内限流（默认嵌入 180 次/分钟、公开 120 次/分钟，见 `omni.security.rate-limit.*`）；超限返回 HTTP 429。
5. **生产安全**：更换默认管理员密码与 `JWT_SECRET`；服务账号密码与用户 JWT **只放在业务服务端**。

## 3. API 说明

统一响应包装：

```json
{
  "code": 0,
  "message": "成功",
  "data": { }
}
```

`code !== 0` 表示失败，错误信息见 `message`。下列路径均相对于 Omni API 根，例如 `https://bi.example.com/api`。

### 3.1 登录（获取用户 JWT）

本地密码登录需先取**一次性挑战**再提交 HMAC 签名，不可只传用户名密码。

1. `GET /api/auth/login-challenge` → `challengeId`、`nonce`、`timestamp`、`expiresAt`、`signKey`（十六进制）
2. 计算签名：`HMAC-SHA256(signKey, username + "\n" + password + "\n" + nonce + "\n" + timestamp)`，结果为小写十六进制；其中 `timestamp` 为客户端当前 Unix 秒
3. `POST /api/auth/login` 提交完整载荷

请求体：

```json
{
  "username": "embed-service",
  "password": "********",
  "challengeId": "<来自挑战>",
  "nonce": "<来自挑战>",
  "timestamp": 1710000000,
  "signature": "<hmac-hex>"
}
```

成功响应 `data`（若该账号启用了 TOTP，则先返回 `mfaToken`，需再调 `POST /api/auth/mfa/verify`；服务账号建议关闭 MFA 或由自动化完成第二步）：

```json
{
  "accessToken": "<用户JWT>",
  "tokenType": "Bearer"
}
```

后续签发请求头：`Authorization: Bearer <accessToken>`。

建议在业务服务端缓存该 token，并在 401 时重新走挑战登录；**不要**把用户 JWT 下发给业务前端。前端参考实现：`web/src/auth/loginSignature.ts`。

### 3.2 签发嵌入令牌

- **方法 / 路径**：`POST /api/embed/tokens`
- **鉴权**：Bearer 用户 JWT
- **权限**：对目标资源具备 WRITE

请求：

```json
{
  "resourceType": "DASHBOARD",
  "resourceId": 123
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `resourceType` | string | `DASHBOARD` 或 `QUESTION`（图表） |
| `resourceId` | number | 仪表盘或图表主键 |

成功响应 `data`：

```json
{
  "token": "<embedJwt>"
}
```

令牌特性：

- HMAC 签名，声明含 `typ=embed`、`resourceType`、`resourceId`
- **有效期固定 1 小时**
- 受 `embed.enabled` 控制；关闭后无法签发或解析

### 3.3 嵌入页面与数据接口（浏览器侧，匿名）

| 用途 | 地址 |
|------|------|
| 仪表盘页面 | `https://{omni-host}/embed/dashboard/{embedJwt}` |
| 图表页面 | `https://{omni-host}/embed/question/{embedJwt}` |
| 仪表盘数据 | `GET /api/embed/dashboards/{embedJwt}` |
| 图表数据 | `GET /api/embed/questions/{embedJwt}` |

业务系统一般只需拼 **页面 URL** 给 iframe；数据接口由嵌入页自行调用。

## 4. 对接示例

以下将 `OMNI_BASE` 设为 Omni Web 根地址（无尾斜杠），例如 `https://bi.example.com`。API 前缀为 `{OMNI_BASE}/api`。

### 4.1 curl

```bash
OMNI_BASE=https://bi.example.com
# 实际对接请在业务后端实现：先 GET /api/auth/login-challenge，
# 再按 §3.1 计算 HMAC 后 POST /api/auth/login（下列伪变量仅示意顺序）。
USER_JWT="<完成挑战登录后的 accessToken>"

EMBED_JWT=$(curl -s -X POST "$OMNI_BASE/api/embed/tokens" \
  -H "Authorization: Bearer $USER_JWT" \
  -H 'Content-Type: application/json' \
  -d '{"resourceType":"DASHBOARD","resourceId":123}' \
  | jq -r '.data.token')

echo "$OMNI_BASE/embed/dashboard/$EMBED_JWT"
```

### 4.2 Java（业务后端示意）

```java
// 伪代码：登录 token 由服务端缓存；页面接口按需签发 embed URL
public String buildDashboardEmbedUrl(long dashboardId) {
    String userJwt = omniAuthClient.ensureAccessToken(); // 服务端缓存，401 时重登
    String embedJwt = omniHttp.post("/api/embed/tokens", userJwt,
            Map.of("resourceType", "DASHBOARD", "resourceId", dashboardId));
    return omniBaseUrl + "/embed/dashboard/" + embedJwt;
}
```

业务 Controller 向本系统前端只返回 `embedUrl`，不返回服务账号密码或用户 JWT。

### 4.3 业务前端 iframe

```html
<iframe
  src="https://bi.example.com/embed/dashboard/eyJhbGciOi..."
  title="仪表盘"
  style="width:100%;height:800px;border:0;"
  allowfullscreen
></iframe>
```

注意：

- `src` 必须由业务后端在当次页面加载时生成（或通过业务 API 拉取），不要写死在仓库或静态配置中。
- embed JWT 过期后 iframe 会加载失败，需由业务端重新签发并刷新 `src`（可按接近 1 小时或收到错误时刷新）。

## 5. 安全要求与当前限制

### 必须遵守

- **服务端代签**：仅业务后端持有服务账号与用户 JWT。
- **按需签发**：用户打开页面时再签发；不要长期缓存 embed JWT 超过其 TTL。
- **最小权限**：服务账号只对需要嵌入的资源授予 WRITE，避免使用全局管理员（除非运维需要）。
- **密钥与密码**：生产环境保护 `JWT_SECRET` 与服务账号凭据。

### 当前产品限制（对接前请确认可接受）

- 嵌入视图为**所有者默认参数**下的只读结果，**不能**按业务用户传入部门、租户等锁定参数做行级隔离。
- 无嵌入域名白名单；获知 URL 的第三方页面也可 iframe（在 token 有效期内）。
- embed JWT 泄露后，在过期前可访问与所有者相同的默认视图。
- 嵌入令牌与登录 JWT 共用签名密钥（靠 `typ` / `subject` 区分用途）。
- 嵌入页仍可能提供导出等只读能力，分享面大于「纯数据 API」。

### 禁止

- 将 `/public/dashboard/...` 公开链接作为内网业务系统的正式嵌入方案（不过期、可转发）。
- 在浏览器 JS 中保存服务账号密码，或用前端直接调用 `POST /api/embed/tokens`。
- 把 embed JWT 当作「当前登录用户身份」——它只绑定资源 ID，不做访客鉴权。

## 6. 公开链接何时使用

- 需要给外部人员一个可复制的只读链接，且可在 Omni UI 中创建/撤销 → 使用公开分享。
- 需要嵌进自有业务系统、控制时效与凭据落点 → 使用本文签名嵌入流程。

## 7. 排错

| 现象 | 可能原因 | 处理 |
|------|----------|------|
| `403` /「嵌入功能已关闭」 | `embed.enabled` 为 false | 管理端开启「允许嵌入」 |
| `401` /「嵌入令牌无效或已过期」 | JWT 损坏、过期或签名不匹配 | 重新签发；检查 Omni 实例与密钥是否一致 |
| `401` 签发时失败 | 用户 JWT 无效 | 重新走挑战登录（§3.1） |
| `401` /「登录挑战…」 | 未签名或挑战过期/已用 | 每次登录重新 `GET /login-challenge` |
| `429` | 触发 IP 限流 | 降低轮询/重试频率；检查代理是否透传真实客户端 IP |
| 「仪表盘/图表不存在」或无权限 | `resourceId` 错误，或账号无 WRITE | 核对资源 ID；为服务账号授权 |
| 「嵌入仅支持 DASHBOARD 或 QUESTION」 | `resourceType` 拼写错误 | 使用大写 `DASHBOARD` / `QUESTION` |
| iframe 空白或无法加载 | 基址错误、混合内容（HTTPS 页嵌 HTTP）、网络不通 | 使用与业务页一致的 HTTPS 基址；检查浏览器控制台 |
| 嵌入后参数无法改 | 产品设计如此 | 公开/嵌入仅默认参数；登录态渲染才支持交互传参 |

## 8. 后续增强（尚未提供）

以下能力当前**未实现**，若业务强依赖需单独排期：

- JWT 内锁定参数（按部门/租户过滤）
- 嵌入域名 / `frame-ancestors` 白名单
- 独立 embed 签名密钥与可配置 TTL
- 嵌入签发 UI / iframe 片段一键复制
- SSO / 业务用户会话桥接

---

相关代码入口：

- 签发与渲染：`server/.../controller/EmbedController.java`
- 令牌逻辑：`server/.../service/EmbedTokenService.java`
- 嵌入页：`web/src/views/EmbedDashboardView.vue`、`EmbedQuestionView.vue`
- 登录签名：`web/src/auth/loginSignature.ts`、`LoginChallengeService`

投产与反代加固见 [production.md](production.md)。应用内帮助页（`/help`）可直接阅读本说明。
