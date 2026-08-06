package com.omni.panel.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import com.omni.panel.config.SecurityProperties;

/**
 * 按可信代理配置解析客户端 IP；仅当 {@code remoteAddr} 属于可信 CIDR 时信任转发头。
 */
@Component
public class ClientIpResolver {
    private static volatile ClientIpResolver holder;

    private final List<IpAddressMatcher> trustedMatchers;

    /**
     * @param securityProperties 含 {@code trusted-proxies} 的安全配置
     */
    public ClientIpResolver(SecurityProperties securityProperties) {
        List<IpAddressMatcher> matchers = new ArrayList<>();
        for (String entry : securityProperties.trustedProxyEntries()) {
            try {
                matchers.add(new IpAddressMatcher(entry));
            } catch (IllegalArgumentException ignored) {
                // 非法 CIDR 跳过，避免启动失败；运维应修正配置
            }
        }
        this.trustedMatchers = List.copyOf(matchers);
    }

    @PostConstruct
    void register() {
        holder = this;
    }

    @PreDestroy
    void unregister() {
        if (holder == this) {
            holder = null;
        }
    }

    /**
     * 供 {@link ClientRequestInfo} 静态入口使用；未注册时回退为仅 {@code remoteAddr}。
     */
    static ClientIpResolver current() {
        return holder;
    }

    /**
     * 解析请求的客户端 IP。
     *
     * @param request HTTP 请求
     * @return 截断后的 IP；无法解析时为 null
     */
    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String remote = truncate(blankToNull(request.getRemoteAddr()), 64);
        if (remote == null) {
            return null;
        }
        if (!isTrustedProxy(remote)) {
            return remote;
        }
        String fromForwarded = clientFromForwardedFor(request.getHeader("X-Forwarded-For"));
        if (fromForwarded != null) {
            return fromForwarded;
        }
        String realIp = truncate(blankToNull(request.getHeader("X-Real-IP")), 64);
        if (realIp != null && !isTrustedProxy(realIp)) {
            return realIp;
        }
        return remote;
    }

    /**
     * 自右向左遍历 XFF，跳过可信代理跳，返回第一个非可信 IP。
     */
    String clientFromForwardedFor(String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return null;
        }
        String[] parts = forwardedFor.split(",");
        List<String> hops = new ArrayList<>(parts.length);
        for (String part : parts) {
            String hop = blankToNull(part);
            if (hop != null) {
                hops.add(truncate(hop, 64));
            }
        }
        Collections.reverse(hops);
        for (String hop : hops) {
            if (!isTrustedProxy(hop)) {
                return hop;
            }
        }
        return null;
    }

    boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isBlank() || trustedMatchers.isEmpty()) {
            return false;
        }
        for (IpAddressMatcher matcher : trustedMatchers) {
            try {
                if (matcher.matches(ip)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // 非法 IP 视为不可信
            }
        }
        return false;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
