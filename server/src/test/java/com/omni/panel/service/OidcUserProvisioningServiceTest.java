package com.omni.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import com.omni.panel.common.BusinessException;
import com.omni.panel.config.OidcProperties;
import com.omni.panel.entity.SysRoleEntity;
import com.omni.panel.entity.SysUser;
import com.omni.panel.mapper.RoleMapper;
import com.omni.panel.mapper.UserMapper;

class OidcUserProvisioningServiceTest {
    private final UserMapper userMapper = mock(UserMapper.class);
    private final RoleMapper roleMapper = mock(RoleMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private OidcUserProvisioningService service;

    @BeforeEach
    void setUp() {
        OidcProperties properties = new OidcProperties(true, "https://idp.example", "cid", "secret",
                "企业登录", "USER", null);
        service = new OidcUserProvisioningService(userMapper, roleMapper, passwordEncoder, properties);
        when(passwordEncoder.encode(any())).thenReturn("hash");
    }

    @Test
    void 按Subject命中已有用户() {
        SysUser existing = user("alice", "sub-1", "alice@example.com", true);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        SysUser result = service.provision(oidcUser("sub-1", "alice@example.com", "Alice"));

        assertThat(result.getUsername()).isEqualTo("alice");
    }

    @Test
    void 按邮箱绑定并JIT建号() {
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(null, null);
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        SysRoleEntity role = new SysRoleEntity();
        role.setId(2L);
        role.setCode("USER");
        role.setEnabled(true);
        when(roleMapper.selectOne(any(Wrapper.class))).thenReturn(role);
        when(userMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            user.setId(9L);
            return 1;
        });

        SysUser result = service.provision(oidcUser("sub-9", "bob@example.com", "Bob"));

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(captor.capture());
        assertThat(captor.getValue().getAuthSource()).isEqualTo("OIDC");
        assertThat(captor.getValue().getIdpSubject()).isEqualTo("sub-9");
        assertThat(captor.getValue().getEmail()).isEqualTo("bob@example.com");
        verify(userMapper).insertRoles(eq(9L), anyList());
        assertThat(result.getId()).isEqualTo(9L);
    }

    @Test
    void 禁用用户拒绝登录() {
        SysUser disabled = user("alice", "sub-1", "alice@example.com", false);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(disabled);

        assertThatThrownBy(() -> service.provision(oidcUser("sub-1", "alice@example.com", "Alice")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("禁用");
    }

    private static SysUser user(String username, String subject, String email, boolean enabled) {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername(username);
        user.setIdpSubject(subject);
        user.setEmail(email);
        user.setEnabled(enabled);
        user.setActivated(true);
        return user;
    }

    private static OidcUser oidcUser(String subject, String email, String name) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", subject);
        claims.put("email", email);
        claims.put("name", name);
        claims.put("preferred_username", email);
        OidcIdToken idToken = new OidcIdToken("token", java.time.Instant.now(),
                java.time.Instant.now().plusSeconds(3600), claims);
        return new DefaultOidcUser(java.util.List.of(new OAuth2UserAuthority(claims)), idToken);
    }
}
