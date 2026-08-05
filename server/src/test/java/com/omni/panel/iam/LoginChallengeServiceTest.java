package com.omni.panel.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import com.omni.panel.common.BusinessException;
import com.omni.panel.service.LoginChallengeService;

class LoginChallengeServiceTest {
    private final LoginChallengeService service = new LoginChallengeService();

    @Test
    void 正确签名可通过并只能使用一次() {
        var challenge = service.issue();
        long timestamp = challenge.timestamp();
        String signature = LoginChallengeService.sign(
                challenge.signKey(), "tester", "password1234", challenge.nonce(), timestamp);

        service.verifyAndConsume(
                challenge.challengeId(), challenge.nonce(), timestamp,
                "tester", "password1234", signature);

        assertThatThrownBy(() -> service.verifyAndConsume(
                challenge.challengeId(), challenge.nonce(), timestamp,
                "tester", "password1234", signature))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("挑战");
    }

    @Test
    void 错误签名被拒绝() {
        var challenge = service.issue();
        assertThatThrownBy(() -> service.verifyAndConsume(
                challenge.challengeId(), challenge.nonce(), challenge.timestamp(),
                "tester", "password1234", "00".repeat(32)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("签名");
    }

    @Test
    void 签名载荷绑定用户名与密码() {
        var challenge = service.issue();
        String signature = LoginChallengeService.sign(
                challenge.signKey(), "tester", "password1234", challenge.nonce(), challenge.timestamp());

        assertThat(signature).hasSize(64);
        assertThat(LoginChallengeService.sign(
                challenge.signKey(), "other", "password1234", challenge.nonce(), challenge.timestamp()))
                .isNotEqualTo(signature);
    }
}
