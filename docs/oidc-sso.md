# 企业 OIDC SSO

Omni Data Panel 支持通过 **OIDC Authorization Code** 对接企业 IdP（Azure AD / Entra ID、Okta、Keycloak 等）。本地密码登录始终保留；首次 SSO 登录可按邮箱自动建号（JIT）并赋予默认角色 `USER`。

应用内帮助页（`/help`）也提供本说明的渲染视图。

## 环境变量

| 变量 | 说明 |
|---|---|
| `OIDC_ENABLED` | `true` 启用 |
| `OIDC_ISSUER_URI` | IdP Issuer，如 `https://login.microsoftonline.com/{tenant}/v2.0` |
| `OIDC_CLIENT_ID` | 客户端 ID |
| `OIDC_CLIENT_SECRET` | 客户端密钥 |
| `OIDC_CLIENT_NAME` | 登录按钮文案，默认「企业登录」 |
| `OIDC_DEFAULT_ROLE_CODE` | JIT 默认角色，默认 `USER`（不可为 `ADMIN`） |
| `OIDC_FRONTEND_REDIRECT_URI` | 可选；默认 `{FRONTEND_URL}/login/oidc/callback` |
| `FRONTEND_URL` | 前端基址，用于推导回调页 |

启用且三要素（issuer / client-id / secret）齐全时才会注册 OAuth2 客户端；否则应用按「未启用 SSO」启动。Compose 示例见 `deploy/.env.example`。

## IdP 回调 URL

在 IdP 中登记：

```text
{API公网基址}/login/oauth2/code/omni
```

开发环境经 Vite 代理时，浏览器访问前端同源路径即可（`/oauth2/**`、`/login/oauth2/**` 已代理到后端）。生产 nginx 同样反代上述路径到后端（与 `/api` 一致）。

## 登录流程

1. 登录页调用 `GET /api/auth/oidc/status`
2. 若 `enabled=true`，展示企业登录按钮；点击后浏览器跳转 `/oauth2/authorization/omni`
3. IdP 回调 `/login/oauth2/code/omni`
4. 后端 JIT/映射用户，签发 Omni JWT，生成一次性兑换码，302 到前端 `/login/oidc/callback?code=`
5. 前端 `POST /api/auth/oidc/exchange` 换取 `accessToken`，写入本地并进入系统

```text
浏览器                Omni API                 IdP
  │  GET /auth/oidc/status │                     │
  │◄──── enabled/name ─────│                     │
  │  /oauth2/authorization/omni ───────────────►│
  │◄─────────── 登录页 / 同意 ──────────────────│
  │  /login/oauth2/code/omni ◄── code ──────────│
  │  302 /login/oidc/callback?code=…            │
  │  POST /auth/oidc/exchange                   │
  │◄──── accessToken ──────│                     │
```

SSO 路径**不**再强制应用内 TOTP；本地密码登录仍走原有 HMAC 挑战 + 可选 MFA。

## JIT 规则

1. 按 `idp_subject`（OIDC `sub`）查找
2. 否则按邮箱查找并绑定 `idp_subject`
3. 否则新建用户：随机不可用密码哈希、`activated=true`、角色=`OIDC_DEFAULT_ROLE_CODE`、`auth_source=OIDC`

邮箱优先取 OIDC `email`，其次 `preferred_username`（规范化小写）。缺少可用邮箱时无法 JIT 建号。

## 安全注意

| 点 | 说明 |
|---|---|
| 邮箱绑定 | 若 IdP 未强制邮箱验证，攻击者可能用他人邮箱声明绑定已有本地账号。生产务必在 IdP 侧开启 **email verified**，或仅信任企业托管域名 |
| SSO 与 MFA | SSO 成功后直接签发应用 JWT，**跳过**应用内 TOTP；双因素应在 IdP 侧强制 |
| 默认角色 | `OIDC_DEFAULT_ROLE_CODE` 不可为 `ADMIN`；管理员仍通过本地角色分配提升 |
| 本地密码 | SSO 用户仍可能保留本地密码哈希（JIT 为随机）；可按需在管理端重置/禁用密码登录策略（产品层未强制禁用） |
| 会话 | 与本地登录共用 JWT / 并发会话上限 |

## 手工验证建议

1. 用 Keycloak（或 Entra ID）创建 confidential client，redirect 如上。
2. 配置 `OIDC_*` 与 `FRONTEND_URL` 后重启后端。
3. 打开登录页，确认出现企业登录按钮。
4. 完成一轮：新用户 JIT → 再次登录按 `sub` 命中 → 用已有邮箱的本地用户验证绑定。
5. 确认管理端用户列表中 `auth_source` / 角色符合预期。

## 相关文档

- 投产清单：[production.md](production.md)
- 使用手册：[user-guide.md](user-guide.md)
- 产品总览：[README.md](../README.md)

应用内帮助页（`/help?tab=oidc`）可直接阅读本说明。
