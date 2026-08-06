package com.omni.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import com.omni.panel.common.BusinessException;

class OidcExchangeCodeServiceTest {
    private final OidcExchangeCodeService service = new OidcExchangeCodeService();

    @Test
    void 兑换码一次性消费() {
        String code = service.issue("token-1");
        assertThat(service.consume(code)).isEqualTo("token-1");
        assertThatThrownBy(() -> service.consume(code))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无效或已过期");
    }

    @Test
    void 空白兑换码拒绝() {
        assertThatThrownBy(() -> service.consume(" "))
                .isInstanceOf(BusinessException.class);
    }
}
