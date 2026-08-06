package com.omni.panel.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omni.panel.common.BusinessException;
import com.omni.panel.common.ClientRequestInfo;
import com.omni.panel.config.OmniMetrics;
import com.omni.panel.controller.AuthController;
import com.omni.panel.entity.SysUser;
import com.omni.panel.mapper.UserMapper;
import com.omni.panel.service.JwtService;
import com.omni.panel.service.LoginAuditService;
import com.omni.panel.service.LoginChallengeService;
import com.omni.panel.service.TotpService;
import com.omni.panel.service.UserService;
import com.omni.panel.service.UserSessionRegistry;

import java.time.Instant;

class AuthMfaLoginTest {
    @BeforeAll
    static void 初始化MybatisPlus实体缓存() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
    }

    private final UserMapper userMapper = mock(UserMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final LoginAuditService loginAuditService = mock(LoginAuditService.class);
    private final TotpService totpService = mock(TotpService.class);
    private final LoginChallengeService loginChallengeService = mock(LoginChallengeService.class);
    private final UserSessionRegistry sessionRegistry = mock(UserSessionRegistry.class);
    private final AuthController controller = new AuthController(
            userMapper, passwordEncoder, jwtService, loginAuditService, mock(UserService.class), totpService,
            loginChallengeService, sessionRegistry, mock(OmniMetrics.class));
    private final HttpServletRequest httpRequest = mock(HttpServletRequest.class);

    @BeforeEach
    void 默认签名通过() {
        doNothing().when(loginChallengeService).verifyAndConsume(
                anyString(), anyString(), anyLong(), anyString(), anyString(), anyString());
        when(jwtService.createAccess(anyLong(), anyString()))
                .thenReturn(new JwtService.AccessToken("access-jwt", "jti-1", Instant.now().plusSeconds(3600)));
    }

    @Test
    void 未启用MFA时直接返回访问令牌() {
        SysUser user = activeUser(false);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("password1234", "hash")).thenReturn(true);

        var result = controller.login(signedLogin("tester", "password1234"), httpRequest).data();

        assertThat(result.mfaRequired()).isFalse();
        assertThat(result.accessToken()).isEqualTo("access-jwt");
        verify(sessionRegistry).register(eq(7L), eq("jti-1"), any(Instant.class));
        verify(loginAuditService).record(eq("tester"), eq(7L), eq(true), eq("登录成功"), any(ClientRequestInfo.Info.class));
    }

    @Test
    void 已启用MFA时返回中间令牌() {
        SysUser user = activeUser(true);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("password1234", "hash")).thenReturn(true);
        when(jwtService.createMfaPending(7L, "tester")).thenReturn("mfa-jwt");

        var result = controller.login(signedLogin("tester", "password1234"), httpRequest).data();

        assertThat(result.mfaRequired()).isTrue();
        assertThat(result.mfaToken()).isEqualTo("mfa-jwt");
        assertThat(result.accessToken()).isNull();
        verify(loginAuditService).record(eq("tester"), eq(7L), eq(false), eq("需要MFA"), any(ClientRequestInfo.Info.class));
    }

    @Test
    void 签名失败时拒绝登录() {
        doThrow(new BusinessException(401, "登录签名校验失败")).when(loginChallengeService)
                .verifyAndConsume(anyString(), anyString(), anyLong(), anyString(), anyString(), anyString());

        assertThatThrownBy(() -> controller.login(signedLogin("tester", "password1234"), httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessage("登录签名校验失败");
        verify(loginAuditService).record(eq("tester"), eq(null), eq(false), eq("登录签名失败"), any(ClientRequestInfo.Info.class));
    }

    @Test
    void MFA校验成功签发访问令牌() {
        SysUser user = activeUser(true);
        when(jwtService.requireMfaPendingUserId("mfa-jwt")).thenReturn(7L);
        when(userMapper.selectById(7L)).thenReturn(user);
        when(totpService.verifyLoginCode(7L, "123456")).thenReturn(true);

        var result = controller.verifyMfa(
                new AuthController.MfaVerifyRequest("mfa-jwt", "123456"), httpRequest).data();

        assertThat(result.accessToken()).isEqualTo("access-jwt");
        verify(sessionRegistry).register(eq(7L), eq("jti-1"), any(Instant.class));
        verify(loginAuditService).record(eq("tester"), eq(7L), eq(true), eq("登录成功(MFA)"), any(ClientRequestInfo.Info.class));
    }

    @Test
    void MFA校验失败拒绝登录() {
        SysUser user = activeUser(true);
        when(jwtService.requireMfaPendingUserId("mfa-jwt")).thenReturn(7L);
        when(userMapper.selectById(7L)).thenReturn(user);
        when(totpService.verifyLoginCode(anyLong(), eq("000000"))).thenReturn(false);

        assertThatThrownBy(() -> controller.verifyMfa(
                new AuthController.MfaVerifyRequest("mfa-jwt", "000000"), httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessage("验证码错误");
        verify(loginAuditService).record(eq("tester"), eq(7L), eq(false), eq("MFA验证失败"), any(ClientRequestInfo.Info.class));
    }

    private static AuthController.LoginRequest signedLogin(String username, String password) {
        return new AuthController.LoginRequest(
                username, password, "challenge", "nonce", 1_700_000_000L, "signature");
    }

    private static SysUser activeUser(boolean totpEnabled) {
        SysUser user = new SysUser();
        user.setId(7L);
        user.setUsername("tester");
        user.setPasswordHash("hash");
        user.setEnabled(true);
        user.setActivated(true);
        user.setTotpEnabled(totpEnabled);
        return user;
    }
}
