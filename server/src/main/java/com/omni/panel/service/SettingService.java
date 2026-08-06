package com.omni.panel.service;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.omni.panel.common.BusinessException;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.datasource.CredentialCrypto;
import com.omni.panel.entity.SettingEntity;
import com.omni.panel.mapper.SettingMapper;

/**
 * 管理系统设置键值。
 */
@Service
public class SettingService {
    static final String EMBED_ALLOWED_ORIGINS = "embed.allowed-origins";
    static final String CACHE_QUERY_ENABLED = "cache.query.enabled";
    static final String CACHE_QUERY_TTL_SECONDS = "cache.query.ttl-seconds";
    static final String AUTH_SESSION_MAX_CONCURRENT = "auth.session.max-concurrent";
    static final String MAIL_HOST = "mail.host";
    static final String MAIL_PORT = "mail.port";
    static final String MAIL_USERNAME = "mail.username";
    static final String MAIL_PASSWORD = "mail.password";
    static final String MAIL_PASSWORD_SET = "mail.password.set";
    static final String MAIL_FROM = "mail.from";
    static final String MAIL_SMTP_AUTH = "mail.smtp.auth";
    static final String MAIL_SMTP_STARTTLS = "mail.smtp.starttls";

    private static final int DEFAULT_CACHE_TTL_SECONDS = 300;
    private static final int MIN_CACHE_TTL_SECONDS = 30;
    private static final int MAX_CACHE_TTL_SECONDS = 86_400;
    private static final int DEFAULT_MAX_CONCURRENT_SESSIONS = 2;
    private static final int MIN_MAX_CONCURRENT_SESSIONS = 0;
    private static final int MAX_MAX_CONCURRENT_SESSIONS = 100;
    private static final int DEFAULT_MAIL_PORT = 25;
    private static final int MAX_EMBED_ORIGINS = 50;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern ORIGIN_SPLIT = Pattern.compile("[,\\s]+");

    private static final List<String> ALLOWED_KEYS = List.of(
            "site.name",
            "embed.enabled",
            EMBED_ALLOWED_ORIGINS,
            "ui.sql.tips-collapsed-default",
            CACHE_QUERY_ENABLED,
            CACHE_QUERY_TTL_SECONDS,
            AUTH_SESSION_MAX_CONCURRENT,
            MAIL_HOST,
            MAIL_PORT,
            MAIL_USERNAME,
            MAIL_PASSWORD,
            MAIL_FROM,
            MAIL_SMTP_AUTH,
            MAIL_SMTP_STARTTLS);
    private static final Set<String> ALLOWED = new LinkedHashSet<>(ALLOWED_KEYS);
    private static final Set<String> BOOLEAN_KEYS = Set.of(
            "embed.enabled",
            "ui.sql.tips-collapsed-default",
            CACHE_QUERY_ENABLED,
            MAIL_SMTP_AUTH,
            MAIL_SMTP_STARTTLS);
    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("site.name", "全域数据分析"),
            Map.entry("embed.enabled", "true"),
            Map.entry(EMBED_ALLOWED_ORIGINS, ""),
            Map.entry("ui.sql.tips-collapsed-default", "false"),
            Map.entry(CACHE_QUERY_ENABLED, "false"),
            Map.entry(CACHE_QUERY_TTL_SECONDS, String.valueOf(DEFAULT_CACHE_TTL_SECONDS)),
            Map.entry(AUTH_SESSION_MAX_CONCURRENT, String.valueOf(DEFAULT_MAX_CONCURRENT_SESSIONS)),
            Map.entry(MAIL_HOST, ""),
            Map.entry(MAIL_PORT, String.valueOf(DEFAULT_MAIL_PORT)),
            Map.entry(MAIL_USERNAME, ""),
            Map.entry(MAIL_PASSWORD, ""),
            Map.entry(MAIL_FROM, ""),
            Map.entry(MAIL_SMTP_AUTH, "false"),
            Map.entry(MAIL_SMTP_STARTTLS, "false"));

    private final SettingMapper mapper;
    private final CredentialCrypto crypto;

    /**
     * 注入设置持久化与凭据加解密服务。
     *
     * @param mapper 设置数据访问
     * @param crypto 凭据加解密（邮件密码）
     */
    public SettingService(SettingMapper mapper, CredentialCrypto crypto) {
        this.mapper = mapper;
        this.crypto = crypto;
    }

    /**
     * 读取全部允许的设置；库中缺失时回落默认值。密码永不返回，仅暴露是否已配置。
     *
     * @return 设置映射
     */
    public Map<String, String> list() {
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : ALLOWED_KEYS) {
            if (MAIL_PASSWORD.equals(key)) {
                String stored = rawValue(MAIL_PASSWORD);
                values.put(MAIL_PASSWORD_SET, stored != null && !stored.isBlank() ? "true" : "false");
                continue;
            }
            String stored = rawValue(key);
            values.put(key, stored != null ? stored : DEFAULTS.get(key));
        }
        return values;
    }

    /**
     * 读取单个设置值（含邮件密码密文）；库中不存在时返回 {@code null}。
     *
     * @param key 设置键
     * @return 设置值；不存在时返回 {@code null}
     */
    public String get(String key) {
        return rawValue(key);
    }

    /**
     * 读取设置值；缺失时回落默认值。
     *
     * @param key 设置键
     * @return 非空字符串（可能为空串）
     */
    public String getOrDefault(String key) {
        String value = rawValue(key);
        if (value != null) {
            return value;
        }
        return DEFAULTS.getOrDefault(key, "");
    }

    /**
     * 未登录可暴露的品牌设置（不含邮件等敏感项）。
     *
     * @return 仅含 {@code site.name} 的映射
     */
    public Map<String, String> publicBranding() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("site.name", getOrDefault("site.name"));
        return values;
    }

    /**
     * 判断嵌入功能是否启用。
     *
     * @return 启用时返回 {@code true}
     */
    public boolean embedEnabled() {
        String value = get("embed.enabled");
        return value == null || Boolean.parseBoolean(value);
    }

    /**
     * 读取已规范化的嵌入父页面 Origin 白名单（可能为空）。
     *
     * @return Origin 列表，如 {@code https://app.example.com}
     */
    public List<String> embedAllowedOrigins() {
        return parseOrigins(getOrDefault(EMBED_ALLOWED_ORIGINS));
    }

    /**
     * 组装仅含 {@code frame-ancestors} 的 Content-Security-Policy 值。
     * 白名单为空时仅允许 {@code 'self'}。
     *
     * @return CSP 头值
     */
    public String frameAncestorsCsp() {
        List<String> origins = embedAllowedOrigins();
        if (origins.isEmpty()) {
            return "frame-ancestors 'self'";
        }
        return "frame-ancestors 'self' " + String.join(" ", origins);
    }

    /**
     * 判断查询结果缓存是否启用。
     *
     * @return 启用时返回 {@code true}；缺省关闭
     */
    public boolean queryCacheEnabled() {
        String value = get(CACHE_QUERY_ENABLED);
        return value != null && Boolean.parseBoolean(value);
    }

    /**
     * 读取查询结果缓存 TTL（秒）。
     *
     * @return 有效秒数；缺省或非法时回落 300
     */
    public int queryCacheTtlSeconds() {
        String value = get(CACHE_QUERY_TTL_SECONDS);
        if (value == null || value.isBlank()) {
            return DEFAULT_CACHE_TTL_SECONDS;
        }
        try {
            int seconds = Integer.parseInt(value.trim());
            if (seconds < MIN_CACHE_TTL_SECONDS || seconds > MAX_CACHE_TTL_SECONDS) {
                return DEFAULT_CACHE_TTL_SECONDS;
            }
            return seconds;
        } catch (NumberFormatException exception) {
            return DEFAULT_CACHE_TTL_SECONDS;
        }
    }

    /**
     * 读取单用户最大同时登录会话数；0 表示不限制，缺省 2。
     *
     * @return 并发上限
     */
    public int maxConcurrentSessions() {
        String value = get(AUTH_SESSION_MAX_CONCURRENT);
        if (value == null || value.isBlank()) {
            return DEFAULT_MAX_CONCURRENT_SESSIONS;
        }
        try {
            int max = Integer.parseInt(value.trim());
            if (max < MIN_MAX_CONCURRENT_SESSIONS || max > MAX_MAX_CONCURRENT_SESSIONS) {
                return DEFAULT_MAX_CONCURRENT_SESSIONS;
            }
            return max;
        } catch (NumberFormatException exception) {
            return DEFAULT_MAX_CONCURRENT_SESSIONS;
        }
    }

    /**
     * 批量更新设置，仅管理员。
     *
     * @param values 设置映射
     * @return 更新后的设置
     */
    @Transactional
    public Map<String, String> update(Map<String, String> values) {
        if (!AuthenticatedUser.current().admin()) {
            throw new BusinessException(403, "仅管理员可修改系统设置");
        }
        if (values == null || values.isEmpty()) {
            throw new BusinessException("设置不能为空");
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            if (MAIL_PASSWORD_SET.equals(key)) {
                continue;
            }
            if (!ALLOWED.contains(key)) {
                throw new BusinessException("不支持的设置键：" + key);
            }
            if (MAIL_PASSWORD.equals(key)) {
                String raw = entry.getValue() == null ? "" : entry.getValue().trim();
                if (raw.isEmpty() || "********".equals(raw)) {
                    continue;
                }
                upsert(key, crypto.encrypt(raw));
                continue;
            }
            upsert(key, normalizeValue(key, entry.getValue()));
        }
        return list();
    }

    /**
     * 按主键插入或更新设置行。
     *
     * @param key        设置键
     * @param normalized 已规范化的存储值
     */
    private void upsert(String key, String normalized) {
        SettingEntity entity = mapper.selectById(key);
        boolean insert = entity == null;
        if (insert) {
            entity = new SettingEntity();
            entity.setSettingKey(key);
        }
        entity.setSettingValue(normalized);
        entity.setUpdatedAt(LocalDateTime.now());
        if (insert) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
    }

    /**
     * 从库中读取原始设置值，不做默认回落。
     *
     * @param key 设置键
     * @return 库中值；不存在时返回 {@code null}
     */
    private String rawValue(String key) {
        SettingEntity entity = mapper.selectById(key);
        return entity == null ? null : entity.getSettingValue();
    }

    /**
     * 校验并规范化单个设置值（布尔开关、TTL 范围、邮件端口等）。
     *
     * @param key   设置键
     * @param value 原始值
     * @return 规范化后的存储值
     */
    private String normalizeValue(String key, String value) {
        String raw = value == null ? "" : value.trim();
        if (BOOLEAN_KEYS.contains(key)) {
            if (!"true".equalsIgnoreCase(raw) && !"false".equalsIgnoreCase(raw)) {
                throw new BusinessException("开关仅支持 true 或 false：" + key);
            }
            return Boolean.parseBoolean(raw) ? "true" : "false";
        }
        if (EMBED_ALLOWED_ORIGINS.equals(key)) {
            List<String> origins = parseOrigins(raw);
            if (origins.size() > MAX_EMBED_ORIGINS) {
                throw new BusinessException("嵌入域名白名单最多 " + MAX_EMBED_ORIGINS + " 项");
            }
            return String.join("\n", origins);
        }
        if (CACHE_QUERY_TTL_SECONDS.equals(key)) {
            int seconds;
            try {
                seconds = Integer.parseInt(raw);
            } catch (NumberFormatException exception) {
                throw new BusinessException("缓存时间必须是整数秒");
            }
            if (seconds < MIN_CACHE_TTL_SECONDS || seconds > MAX_CACHE_TTL_SECONDS) {
                throw new BusinessException("缓存时间需在 " + MIN_CACHE_TTL_SECONDS
                        + "–" + MAX_CACHE_TTL_SECONDS + " 秒之间");
            }
            return String.valueOf(seconds);
        }
        if (AUTH_SESSION_MAX_CONCURRENT.equals(key)) {
            int max;
            try {
                max = Integer.parseInt(raw);
            } catch (NumberFormatException exception) {
                throw new BusinessException("同时登录设备数必须是整数");
            }
            if (max < MIN_MAX_CONCURRENT_SESSIONS || max > MAX_MAX_CONCURRENT_SESSIONS) {
                throw new BusinessException("同时登录设备数需在 " + MIN_MAX_CONCURRENT_SESSIONS
                        + "–" + MAX_MAX_CONCURRENT_SESSIONS + " 之间（0 表示不限制）");
            }
            return String.valueOf(max);
        }
        if (MAIL_PORT.equals(key)) {
            if (raw.isEmpty()) {
                return String.valueOf(DEFAULT_MAIL_PORT);
            }
            int port;
            try {
                port = Integer.parseInt(raw);
            } catch (NumberFormatException exception) {
                throw new BusinessException("邮件端口必须是整数");
            }
            if (port < 1 || port > 65_535) {
                throw new BusinessException("邮件端口需在 1–65535 之间");
            }
            return String.valueOf(port);
        }
        if (MAIL_FROM.equals(key) && !raw.isEmpty()) {
            validateEmail(raw, "发件人邮箱格式不合法");
        }
        if (MAIL_USERNAME.equals(key) && !raw.isEmpty() && raw.contains("@")) {
            validateEmail(raw, "邮件账号格式不合法");
        }
        return raw;
    }

    /**
     * 用正则与 {@link InternetAddress} 校验邮箱；非法时抛出业务异常。
     *
     * @param value   待校验地址
     * @param message 失败时的业务提示
     */
    private static void validateEmail(String value, String message) {
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(message);
        }
        try {
            InternetAddress address = new InternetAddress(value);
            address.validate();
            if (!value.equals(address.getAddress())) {
                throw new AddressException();
            }
        } catch (AddressException exception) {
            throw new BusinessException(message);
        }
    }

    /**
     * 解析并校验嵌入 Origin 列表；非法项抛出业务异常。
     *
     * @param raw 原始文本（逗号 / 空白 / 换行分隔）
     * @return 去重后的 Origin 列表
     */
    static List<String> parseOrigins(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String token : ORIGIN_SPLIT.split(raw.trim())) {
            if (token.isBlank()) {
                continue;
            }
            unique.add(normalizeOrigin(token.trim()));
        }
        return new ArrayList<>(unique);
    }

    /**
     * 将单个输入规范化为 {@code scheme://host[:port]} Origin。
     *
     * @param raw 原始 Origin
     * @return 规范化 Origin
     */
    private static String normalizeOrigin(String raw) {
        String candidate = raw;
        if (candidate.contains("*") || candidate.contains("'")) {
            throw new BusinessException("嵌入域名不允许通配或引号：" + raw);
        }
        URI uri;
        try {
            uri = URI.create(candidate);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("嵌入域名格式不合法：" + raw);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new BusinessException("嵌入域名仅支持 http/https：" + raw);
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new BusinessException("嵌入域名缺少主机名：" + raw);
        }
        if (uri.getUserInfo() != null) {
            throw new BusinessException("嵌入域名不能包含用户信息：" + raw);
        }
        String path = uri.getPath();
        if (path != null && !path.isEmpty() && !"/".equals(path)) {
            throw new BusinessException("嵌入域名不能包含路径：" + raw);
        }
        if (uri.getQuery() != null || uri.getFragment() != null) {
            throw new BusinessException("嵌入域名不能包含查询或片段：" + raw);
        }
        StringBuilder origin = new StringBuilder();
        origin.append(scheme).append("://").append(uri.getHost().toLowerCase(Locale.ROOT));
        if (uri.getPort() > 0) {
            origin.append(':').append(uri.getPort());
        }
        return origin.toString();
    }
}
