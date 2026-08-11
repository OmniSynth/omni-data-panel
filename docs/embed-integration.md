# 业务系统安全嵌入对接说明

本文说明如何将 Omni Data Panel 的仪表盘或图表，以相对安全的方式嵌套到业务系统中。

**业务系统请使用「签名嵌入」，不要使用永久公开链接。**

## 1. 概述

| 能力 | 页面地址 | 有效期 | 撤销 | 适用场景 |
|------|----------|--------|------|----------|
| 公开链接 | `/public/dashboard/{token}` | 可选（默认永不过期；也可选 1/7/30/90 天） | 可在 UI 中撤销；到期自动失效 | 对外临时分享 |
| **签名嵌入** | `/embed/dashboard/{jwt}` | **固定 1 小时** | 过期即失效；可关全局开关 | **内嵌业务系统（推荐）** |

签名嵌入的安全模型：

1. 业务系统**服务端**用具备资源写权限的账号登录 Omni，拿到用户 JWT。
2. 用户打开业务页时，业务后端调用签发接口，获得短期 embed JWT。
3. 业务页用 iframe 加载 `/embed/dashboard/{embedJwt}`；前端页面内部再请求 `/api/embed/...` 拉取只读渲染结果。
4. 渲染以**资源所有者**身份执行。仪表盘参数优先使用签发时写入 JWT 的**锁定参数**，其余沿用配置**默认值**；访客不可交互改参。

```text
业务浏览器
    │
    │  打开业务页
    ▼
业务后端 ──POST /api/auth/login──────────────► Omni（缓存用户 JWT，勿下发浏览器）
    │
    │  POST /api/embed/tokens  Authorization: Bearer <用户JWT>
    │  body 可含 parameters（锁定过滤）
    ▼
Omni 返回 embed JWT（1h）
    │
    ▼
业务页 iframe.src = https://{omni}/embed/dashboard/{embedJwt}
    │
    ▼
嵌入页 GET /api/embed/dashboards/{embedJwt} → 只读图表结果（已应用锁定参数）
```

## 2. 前置条件

1. **开启嵌入**：管理端「设置」中打开「允许嵌入」（`embed.enabled`），并配置「嵌入域名白名单」（`embed.allowed-origins`）。关闭嵌入开关后签发与解析均会失败；白名单为空时外域无法 iframe 嵌入页。
2. **服务账号**：准备一个具备目标仪表盘或图表 **WRITE** 权限的账号（所有者或被授予 WRITE 的角色）。签发接口会校验写权限。
3. **网络与 frame-ancestors**：业务前端所在浏览器能访问 Omni 的 Web 基址（iframe 与 `/api`）。服务端与 Web 网关下发 CSP `frame-ancestors`：
   - 管理端「嵌入域名白名单」（`embed.allowed-origins`）为权威列表；空则仅允许同源嵌套。
   - Compose / 镜像通过环境变量 `EMBED_ALLOWED_ORIGINS`（**空格分隔** Origin）注入 nginx，须与管理端白名单一致，否则 HTML 页与 API 策略可能不一致。
   - 登录与后台页固定 `frame-ancestors 'self'`，不可被外域嵌套。
4. **限流**：`/api/embed/**` 与 `/api/public/**` 按客户端 IP 限流（Redis 优先固定窗口，默认嵌入 180 次/分钟、公开 120 次/分钟，见 `omni.security.rate-limit.*`）；超限返回 HTTP 429。客户端 IP 仅在 `TRUSTED_PROXIES` 匹配时采信转发头。
5. **生产安全**：更换默认管理员密码与 `JWT_SECRET`；服务账号密码与用户 JWT **只放在业务服务端**。
### 嵌入域名白名单示例

管理端多行：

```text
https://app.example.com
https://portal.example.com:8443
```

Compose `.env`：

```bash
EMBED_ALLOWED_ORIGINS=https://app.example.com https://portal.example.com:8443
```

本地 Vite 开发可设 `VITE_EMBED_ALLOWED_ORIGINS`（空格分隔），行为与 nginx 一致。

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
  "resourceId": 123,
  "parameters": {
    "dept_id": "华东",
    "region": "上海"
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `resourceType` | string | `DASHBOARD` 或 `QUESTION`（图表） |
| `resourceId` | number | 仪表盘或图表主键 |
| `parameters` | object | **可选**。仅 `DASHBOARD`：按仪表盘已声明参数 id 锁定过滤值；未知 key 返回 400；最多 32 项。`QUESTION` 携带时返回 400 |

成功响应 `data`：

```json
{
  "token": "<embedJwt>"
}
```

令牌特性：

- HMAC 签名，声明含 `typ=embed`、`resourceType`、`resourceId`；若签发时传入则另含 `parameters`
- **有效期固定 1 小时**
- 受 `embed.enabled` 控制；关闭后无法签发或解析
- 嵌入渲染：默认值 ← JWT `parameters` 覆盖；访客无法通过 URL/请求覆盖

### 3.3 嵌入页面与数据接口（浏览器侧，匿名）

| 用途 | 地址 |
|------|------|
| 仪表盘页面 | `https://{omni-host}/embed/dashboard/{embedJwt}` |
| 图表页面 | `https://{omni-host}/embed/question/{embedJwt}` |
| 仪表盘数据 | `GET /api/embed/dashboards/{embedJwt}` |
| 图表数据 | `GET /api/embed/questions/{embedJwt}` |

业务系统一般只需拼 **页面 URL** 给 iframe；数据接口由嵌入页自行调用。仪表盘数据响应中的 `parameterValues` 为服务端实际合并后的参数（默认值 + JWT 锁定），嵌入页参数条只读展示该值。

## 4. 业务系统完整对接轮子（推荐照抄）

目标：业务浏览器**永远拿不到** Omni 服务账号密码与用户 JWT；只拿到当次页面可用的短期 `embedUrl`。

约定：

| 变量 | 含义 | 示例 |
|------|------|------|
| `OMNI_BASE` | Omni Web 根（无尾斜杠） | `https://bi.example.com` |
| API | `{OMNI_BASE}/api` | `https://bi.example.com/api` |
| 服务账号 | 对目标仪表盘有 WRITE | `embed-service` / 环境变量注入密码 |

### 4.0 对接检查清单

1. Omni 管理端开启「允许嵌入」，白名单加入业务 Origin（如 `https://app.example.com`）。
2. Compose / 网关同步 `EMBED_ALLOWED_ORIGINS`（空格分隔，与白名单一致）。
3. 创建服务账号，仅对要嵌入的仪表盘/图表授予 WRITE；关闭 MFA（或自动化完成 MFA）。
4. 仪表盘声明好过滤参数 id（如 `dept_id`）；查询里用 `:dept_id` 等绑定，锁定才有行级效果。
5. 业务后端实现：挑战登录 → 缓存用户 JWT → 按页签发 embed → 只返回 `embedUrl`。
6. 业务前端：打开页时调自家 API 取 `embedUrl`，塞进 iframe；约 50 分钟或失败时重新拉取。

### 4.1 完整时序（必须按此落点）

```text
业务用户浏览器          业务后端                    Omni
      │                    │                         │
      │ 打开业务页          │                         │
      │───────────────────►│                         │
      │                    │ GET /api/auth/login-challenge
      │                    │────────────────────────►│
      │                    │◄── challenge + signKey ─│
      │                    │ HMAC 后 POST /api/auth/login
      │                    │────────────────────────►│
      │                    │◄── accessToken（缓存，勿下发）
      │                    │                         │
      │                    │ POST /api/embed/tokens   │
      │                    │ Authorization: Bearer … │
      │                    │ body: resourceId + 锁定参数
      │                    │────────────────────────►│
      │                    │◄── embedJwt（1h）───────│
      │◄── { embedUrl } ───│                         │
      │                    │                         │
      │ iframe → /embed/dashboard/{embedJwt}         │
      │─────────────────────────────────────────────►│
      │ GET /api/embed/dashboards/{embedJwt}（嵌入页自己调）
      │─────────────────────────────────────────────►│
```

### 4.2 Java 完整轮子（Spring Boot 业务后端）

以下为**可直接粘贴改造**的参考实现：挑战登录 + 用户 JWT 缓存 + 签发 + 业务 API。依赖：`java.net.http.HttpClient`（JDK 11+）、Jackson（Spring Boot 自带）。

#### 4.2.1 配置

```yaml
# application.yml（业务系统）
omni:
  base-url: https://bi.example.com
  username: embed-service
  password: ${OMNI_EMBED_PASSWORD}   # 切勿写进仓库
```

```java
package com.example.biz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Omni 对接配置：Web 根地址与服务账号凭据。
 *
 * @param baseUrl  Omni Web 根地址（无尾斜杠），如 https://bi.example.com
 * @param username 具备目标资源 WRITE 的服务账号
 * @param password 服务账号密码（仅环境变量注入，勿入库）
 */
@ConfigurationProperties(prefix = "omni")
public record OmniProperties(String baseUrl, String username, String password) {
    /**
     * 拼接 Omni API 绝对地址。
     *
     * @param path API 路径，如 /auth/login 或 embed/tokens
     * @return {baseUrl}/api{path}
     */
    public String api(String path) {
        String root = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return root + "/api" + (path.startsWith("/") ? path : "/" + path);
    }

    /**
     * 拼接 Omni 前端页绝对地址（iframe 用）。
     *
     * @param path 页面路径，如 /embed/dashboard/{jwt}
     * @return {baseUrl}{path}
     */
    public String web(String path) {
        String root = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return root + (path.startsWith("/") ? path : "/" + path);
    }
}
```

启动类加 `@EnableConfigurationProperties(OmniProperties.class)`（或 `@ConfigurationPropertiesScan`）。
#### 4.2.2 Omni 客户端（核心）

```java
package com.example.biz.omni;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.biz.config.OmniProperties;
import org.springframework.stereotype.Component;

/**
 * 业务后端专用 Omni 客户端：挑战登录、缓存用户 JWT、签发嵌入 URL。
 * <p>凭据与用户 JWT 仅留在本进程，不得下发给业务浏览器。
 */
@Component
public class OmniEmbedClient {
    private final OmniProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    /** 缓存的 Omni 用户 JWT；签发遇 401 时清空并重登。 */
    private final AtomicReference<String> accessToken = new AtomicReference<>();

    /**
     * @param props  Omni 基址与服务账号
     * @param mapper JSON 序列化（Spring Boot 注入即可）
     */
    public OmniEmbedClient(OmniProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    /**
     * 签发仪表盘嵌入页完整 URL（可带锁定参数做行级过滤）。
     *
     * @param dashboardId  Omni 仪表盘主键
     * @param lockedParams 仅填仪表盘已声明的参数 id；未知 key 会 400；应由业务会话推导，勿信任前端原样传入
     * @return 形如 {OMNI_BASE}/embed/dashboard/{embedJwt}，有效期约 1 小时
     */
    public String buildDashboardEmbedUrl(long dashboardId, Map<String, Object> lockedParams) {
        String embedJwt = createEmbedToken("DASHBOARD", dashboardId, lockedParams);
        return props.web("/embed/dashboard/" + embedJwt);
    }

    /**
     * 签发图表（QUESTION）嵌入页完整 URL。
     * <p>图表嵌入不支持锁定参数。
     *
     * @param questionId Omni 图表主键
     * @return 形如 {OMNI_BASE}/embed/question/{embedJwt}
     */
    public String buildQuestionEmbedUrl(long questionId) {
        String embedJwt = createEmbedToken("QUESTION", questionId, null);
        return props.web("/embed/question/" + embedJwt);
    }

    /**
     * 调用 POST /api/embed/tokens；若用户 JWT 失效（401）则清缓存后重试一次。
     *
     * @param resourceType DASHBOARD 或 QUESTION
     * @param resourceId   资源主键
     * @param parameters   锁定参数；QUESTION 须为 null/空
     * @return embed JWT 字符串
     */
    private String createEmbedToken(String resourceType, long resourceId, Map<String, Object> parameters) {
        try {
            return doCreateEmbedToken(resourceType, resourceId, parameters);
        } catch (OmniHttpException ex) {
            if (ex.status == 401) {
                accessToken.set(null);
                return doCreateEmbedToken(resourceType, resourceId, parameters);
            }
            throw ex;
        }
    }

    /**
     * 使用当前用户 JWT 向 Omni 申请嵌入令牌。
     *
     * @param resourceType DASHBOARD 或 QUESTION
     * @param resourceId   资源主键
     * @param parameters   可选锁定参数
     * @return embed JWT
     */
    private String doCreateEmbedToken(String resourceType, long resourceId, Map<String, Object> parameters) {
        String userJwt = ensureAccessToken();
        var body = mapper.createObjectNode();
        body.put("resourceType", resourceType);
        body.put("resourceId", resourceId);
        if (parameters != null && !parameters.isEmpty()) {
            body.set("parameters", mapper.valueToTree(parameters));
        }
        JsonNode data = postJson("/embed/tokens", body, userJwt);
        String token = text(data, "token");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("签发嵌入令牌失败：响应无 token");
        }
        return token;
    }

    /**
     * 返回可用的 Omni 用户 JWT；无缓存时走挑战登录，双重检查避免并发重复登录。
     *
     * @return Authorization Bearer 用的 accessToken
     */
    private String ensureAccessToken() {
        String cached = accessToken.get();
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        synchronized (this) {
            cached = accessToken.get();
            if (cached != null && !cached.isBlank()) {
                return cached;
            }
            String fresh = loginWithChallenge();
            accessToken.set(fresh);
            return fresh;
        }
    }

    /**
     * 挑战登录：先 GET /auth/login-challenge，再按 Omni 约定做 HMAC-SHA256 后 POST /auth/login。
     * <p>约定与 Omni {@code LoginChallengeService}、前端 {@code loginSignature.ts} 一致：
     * {@code HMAC-SHA256(signKey, username + "\\n" + password + "\\n" + nonce + "\\n" + timestamp)}。
     *
     * @return 用户 accessToken；服务账号若开启 MFA 将抛错（嵌入场景建议关闭 MFA）
     */
    private String loginWithChallenge() {
        JsonNode challenge = getJson("/auth/login-challenge", null);
        String challengeId = text(challenge, "challengeId");
        String nonce = text(challenge, "nonce");
        String signKey = text(challenge, "signKey");
        long timestamp = System.currentTimeMillis() / 1000L;
        String signature = hmacSha256Hex(signKey,
                props.username() + "\n" + props.password() + "\n" + nonce + "\n" + timestamp);

        var loginBody = mapper.createObjectNode();
        loginBody.put("username", props.username());
        loginBody.put("password", props.password());
        loginBody.put("challengeId", challengeId);
        loginBody.put("nonce", nonce);
        loginBody.put("timestamp", timestamp);
        loginBody.put("signature", signature);

        JsonNode data = postJson("/auth/login", loginBody, null);
        if (Boolean.TRUE.equals(bool(data, "mfaRequired"))) {
            throw new IllegalStateException("服务账号启用了 MFA，请关闭 MFA 或在自动化中完成 /auth/mfa/verify");
        }
        String token = text(data, "accessToken");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("登录失败：无 accessToken");
        }
        return token;
    }

    /**
     * 计算登录请求签名（小写十六进制）。
     *
     * @param signKeyHex 挑战返回的 signKey（十六进制）
     * @param message    username\\npassword\\nnonce\\ntimestamp
     * @return HMAC-SHA256 十六进制串
     */
    private static String hmacSha256Hex(String signKeyHex, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(HexFormat.of().parseHex(signKeyHex), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("登录 HMAC 计算失败", e);
        }
    }

    /**
     * GET Omni API，解析统一包装后的 data。
     *
     * @param apiPath 相对于 /api 的路径
     * @param bearer  可选用户 JWT；登录挑战传 null
     * @return data 节点
     */
    private JsonNode getJson(String apiPath, String bearer) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(props.api(apiPath)))
                    .timeout(Duration.ofSeconds(30))
                    .GET();
            if (bearer != null) {
                b.header("Authorization", "Bearer " + bearer);
            }
            return readData(http.send(b.build(), HttpResponse.BodyHandlers.ofString()));
        } catch (OmniHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("调用 Omni GET " + apiPath + " 失败", e);
        }
    }

    /**
     * POST JSON 到 Omni API，解析统一包装后的 data。
     *
     * @param apiPath 相对于 /api 的路径
     * @param body    请求体对象（Jackson 可序列化）
     * @param bearer  可选用户 JWT；登录传 null
     * @return data 节点
     */
    private JsonNode postJson(String apiPath, Object body, String bearer) {
        try {
            byte[] bytes = mapper.writeValueAsBytes(body);
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(props.api(apiPath)))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bytes));
            if (bearer != null) {
                b.header("Authorization", "Bearer " + bearer);
            }
            return readData(http.send(b.build(), HttpResponse.BodyHandlers.ofString()));
        } catch (OmniHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("调用 Omni POST " + apiPath + " 失败", e);
        }
    }

    /**
     * 解析 Omni 统一响应 {@code {code,message,data}}；HTTP/业务 401 抛 {@link OmniHttpException}。
     *
     * @param response 原始 HTTP 响应
     * @return data 节点（可能为空对象）
     */
    private JsonNode readData(HttpResponse<String> response) throws Exception {
        JsonNode root = mapper.readTree(response.body() == null ? "{}" : response.body());
        int code = root.path("code").asInt(-1);
        if (response.statusCode() == 401 || code == 401) {
            throw new OmniHttpException(401, root.path("message").asText("未授权"));
        }
        if (response.statusCode() >= 400 || code != 0) {
            throw new OmniHttpException(response.statusCode(),
                    root.path("message").asText("Omni 调用失败 HTTP " + response.statusCode()));
        }
        return root.path("data");
    }

    /**
     * 读取 JSON 字符串字段；缺失或 null 时返回 null。
     *
     * @param node  父节点
     * @param field 字段名
     * @return 文本值或 null
     */
    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    /**
     * 读取 JSON 布尔字段；缺失或 null 时返回 null。
     *
     * @param node  父节点
     * @param field 字段名
     * @return 布尔值或 null
     */
    private static Boolean bool(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asBoolean();
    }

    /**
     * Omni HTTP/业务错误；{@link #status} 为 HTTP 状态或业务码（如 401）。
     */
    public static final class OmniHttpException extends RuntimeException {
        /** HTTP 状态或业务错误码。 */
        public final int status;

        /**
         * @param status  状态码
         * @param message 错误说明
         */
        public OmniHttpException(int status, String message) {
            super(message);
            this.status = status;
        }
    }
}
```

#### 4.2.3 业务 API（只把 embedUrl 交给前端）

```java
package com.example.biz.web;

import java.util.Map;

import com.example.biz.omni.OmniEmbedClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 业务侧嵌入入口：按当前登录用户推导锁定参数，只向浏览器返回短期 embedUrl。
 * <p>切勿把 Omni 服务账号密码、用户 JWT 或前端任意 parameters 透传出去。
 */
@RestController
@RequestMapping("/api/bi")
public class BiEmbedController {
    private final OmniEmbedClient omni;

    /**
     * @param omni Omni 嵌入客户端
     */
    public BiEmbedController(OmniEmbedClient omni) {
        this.omni = omni;
    }

    /**
     * 获取仪表盘 iframe 地址。
     * <p>锁定参数必须由后端根据业务会话计算（示例为部门），不要接收前端随意提交的过滤值。
     *
     * @param dashboardId Omni 仪表盘 ID
     * @return embedUrl（含短期 JWT）与参考过期秒数（Omni 固定约 3600）
     */
    @GetMapping("/dashboards/{dashboardId}/embed-url")
    public Map<String, String> dashboardEmbedUrl(@PathVariable long dashboardId) {
        String deptId = currentUserDeptId();
        String embedUrl = omni.buildDashboardEmbedUrl(dashboardId, Map.of("dept_id", deptId));
        return Map.of("embedUrl", embedUrl, "expiresInSeconds", "3600");
    }

    /**
     * 从业务会话取出当前用户部门，用于写入仪表盘锁定参数 {@code dept_id}。
     *
     * @return 部门标识；须与仪表盘参数可选值/查询绑定一致
     */
    private String currentUserDeptId() {
        // TODO: 从 SecurityContext / Session 取当前用户部门
        return "华东";
    }
}
```

要点：

- **锁定参数必须由业务后端根据当前用户算出**，不要接受前端任意 `parameters`（否则等于放开行级过滤）。
- 响应里**不要**带 `accessToken`、服务账号密码、原始 embed JWT 以外的密钥材料；`embedUrl` 本身含 JWT，仍属敏感短期票据，勿写入日志全文。

#### 4.2.4 业务前端（Vue / 任意框架）

```vue
<script setup>
import { onMounted, onUnmounted, ref } from 'vue'

const props = defineProps({ dashboardId: { type: Number, required: true } })
/** iframe 最终地址，由业务后端签发，勿写死。 */
const embedUrl = ref('')
const error = ref('')
/** 到期前主动换票的定时器。 */
let refreshTimer

/**
 * 向业务后端拉取当次 embedUrl，并在约 50 分钟后自动换新（JWT 固定 1h）。
 */
async function loadEmbedUrl() {
  error.value = ''
  const res = await fetch(`/api/bi/dashboards/${props.dashboardId}/embed-url`, {
    credentials: 'include',
  })
  if (!res.ok) {
    error.value = '获取嵌入地址失败'
    return
  }
  const data = await res.json()
  embedUrl.value = data.embedUrl
  clearTimeout(refreshTimer)
  refreshTimer = setTimeout(loadEmbedUrl, 50 * 60 * 1000)
}

onMounted(loadEmbedUrl)
onUnmounted(() => clearTimeout(refreshTimer))
</script>

<template>
  <p v-if="error" style="color:#c00">{{ error }}</p>
  <iframe
    v-else-if="embedUrl"
    :src="embedUrl"
    title="仪表盘"
    style="width:100%;height:800px;border:0;"
    allowfullscreen
  />
</template>
```

### 4.3 curl 冒烟（可选）

用于运维快速验证签发链路（仍需自行完成挑战登录；生产请用 §4.2）：

```bash
OMNI_BASE=https://bi.example.com
USER_JWT="<业务后端 loginWithChallenge 得到的 accessToken>"

curl -s -X POST "$OMNI_BASE/api/embed/tokens" \
  -H "Authorization: Bearer $USER_JWT" \
  -H 'Content-Type: application/json' \
  -d '{"resourceType":"DASHBOARD","resourceId":123,"parameters":{"dept_id":"华东"}}' \
  | jq -r '"'"$OMNI_BASE"'/embed/dashboard/\(.data.token)"'
```

### 4.4 安全落点对照（自检）

| 做法 | 正确 | 错误 |
|------|------|------|
| 服务账号密码 | 仅业务后端环境变量 | 写进前端 / 仓库 |
| Omni 用户 JWT | 业务后端内存/私有缓存 | 下发给浏览器 |
| embed URL | 打开页时由业务 API 下发 | 写死在静态页 / CDN |
| 锁定参数 | 后端按登录用户计算 | 前端随便传 `dept_id` |
| 公开链接 | 仅对外临时分享 | 当业务系统正式嵌入 |

## 5. 安全要求与当前限制

### 必须遵守

- **服务端代签**：仅业务后端持有服务账号与用户 JWT。
- **按需签发**：用户打开页面时再签发；不要长期缓存 embed JWT 超过其 TTL。
- **最小权限**：服务账号只对需要嵌入的资源授予 WRITE，避免使用全局管理员（除非运维需要）。
- **密钥与密码**：生产环境保护 `JWT_SECRET` 与服务账号凭据。

### 当前产品限制（对接前请确认可接受）

- 嵌入视图为只读；参数由签发时 JWT 锁定（或仪表盘默认值），**访客不能**交互改参。
- 锁定参数只能约束仪表盘**已声明**的参数；行级安全仍依赖查询/策略把这些参数绑进过滤条件。
- embed JWT 泄露后，在过期前可访问签发时锁定的同一视图（含锁定参数）。
- 嵌入令牌与登录 JWT 共用签名密钥（靠 `typ` / `subject` 区分用途）。
- 嵌入页仍可能提供导出等只读能力，分享面大于「纯数据 API」。

### 禁止

- 将 `/public/dashboard/...` 公开链接作为内网业务系统的正式嵌入方案（默认可长期有效、可转发）。
- 在浏览器 JS 中保存服务账号密码，或用前端直接调用 `POST /api/embed/tokens`。
- 把 embed JWT 当作「当前登录用户身份」——它只绑定资源 ID（及可选锁定参数），不做访客鉴权。

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
| 「锁定参数不存在」 | `parameters` 含未声明 id | 与仪表盘参数 id 对齐 |
| 「图表嵌入不支持锁定参数」 | QUESTION 携带了 `parameters` | 去掉 parameters，或改用 DASHBOARD |
| iframe 空白或被拒嵌 | 基址错误、混合内容、白名单未含业务 Origin / 未同步 `EMBED_ALLOWED_ORIGINS`、网络不通 | 管理端配置白名单并重启 web；使用 HTTPS 同源策略；检查控制台 CSP 报错 |
| 嵌入后参数无法改 | 产品设计如此 | 锁定/默认值在签发与配置侧决定；登录态渲染才支持交互传参 |

## 8. 后续增强（尚未提供）

以下能力当前**未实现**，若业务强依赖需单独排期：

- 独立 embed 签名密钥与可配置 TTL
- 嵌入签发 UI / iframe 片段一键复制
- SSO / 业务用户会话桥接

已提供：`embed.enabled`、嵌入域名白名单（`embed.allowed-origins` + `EMBED_ALLOWED_ORIGINS`）、CSP `frame-ancestors`、Redis 优先 IP 限流与 `TRUSTED_PROXIES`、**JWT 锁定参数**（仪表盘）。

---

相关代码入口：

- 签发与渲染：`server/.../controller/EmbedController.java`
- 令牌逻辑：`server/.../service/EmbedTokenService.java`
- 嵌入页：`web/src/views/EmbedDashboardView.vue`、`EmbedQuestionView.vue`
- 登录签名：`web/src/auth/loginSignature.ts`、`LoginChallengeService`

投产与反代加固见 [production.md](production.md)。应用内帮助页（`/help`）可直接阅读本说明。
