package com.omni.panel.config;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.jsonwebtoken.JwtException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.omni.panel.service.JwtService;
import com.omni.panel.service.UserAuthenticationService;

/**
 * 从 Bearer JWT 恢复用户身份、角色和权限，并写入 Spring Security 上下文。
 *
 * <p>无效令牌不会中断过滤链，后续鉴权规则将按未认证请求处理。</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserAuthenticationService authenticationService;

    public JwtAuthenticationFilter(JwtService jwtService, UserAuthenticationService authenticationService) {
        this.jwtService = jwtService;
        this.authenticationService = authenticationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                var claims = jwtService.parse(authorization.substring(7));
                long userId = Long.parseLong(claims.getSubject());
                SecurityContextHolder.getContext().setAuthentication(authenticationService.load(userId));
            } catch (JwtException | IllegalArgumentException | com.omni.panel.common.BusinessException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
