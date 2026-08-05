package com.omni.panel.metric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import com.omni.panel.common.BusinessException;

class MetricExpressionTest {
    @Test
    void 解析field字段() {
        assertThat(MetricExpression.requireFieldName("{\"field\":\"amount\"}")).isEqualTo("amount");
    }

    @Test
    void 缺少field时报错() {
        assertThatThrownBy(() -> MetricExpression.requireFieldName("{}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("field");
    }
}
