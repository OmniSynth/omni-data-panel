package com.omni.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.omni.panel.common.BusinessException;
import com.omni.panel.entity.UserCredentialTokenEntity;
import com.omni.panel.mapper.UserCredentialTokenMapper;

class UserCredentialTokenServiceTest {
    private final UserCredentialTokenMapper mapper = mock(UserCredentialTokenMapper.class);
    private final UserCredentialTokenService service = new UserCredentialTokenService(mapper);

    @Test
    void 签发令牌写入哈希并可核销() {
        when(mapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);
        when(mapper.insert(any(UserCredentialTokenEntity.class))).thenReturn(1);

        String raw = service.issue(9L, UserCredentialTokenEntity.PURPOSE_ACTIVATE);

        ArgumentCaptor<UserCredentialTokenEntity> captor = ArgumentCaptor.forClass(UserCredentialTokenEntity.class);
        verify(mapper).insert(captor.capture());
        UserCredentialTokenEntity stored = captor.getValue();
        assertThat(stored.getUserId()).isEqualTo(9L);
        assertThat(stored.getTokenHash()).isEqualTo(UserCredentialTokenService.hash(raw));
        assertThat(stored.getExpiresAt()).isAfter(LocalDateTime.now());

        when(mapper.selectOne(any(QueryWrapper.class))).thenReturn(stored);
        when(mapper.updateById(any(UserCredentialTokenEntity.class))).thenReturn(1);

        UserCredentialTokenEntity consumed = service.consume(raw, UserCredentialTokenEntity.PURPOSE_ACTIVATE);
        assertThat(consumed.getUsedAt()).isNotNull();
    }

    @Test
    void 过期令牌被拒绝() {
        UserCredentialTokenEntity expired = new UserCredentialTokenEntity();
        expired.setPurpose(UserCredentialTokenEntity.PURPOSE_RESET);
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(mapper.selectOne(any(QueryWrapper.class))).thenReturn(expired);

        assertThatThrownBy(() -> service.requireValid("token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无效或已过期");
    }
}
