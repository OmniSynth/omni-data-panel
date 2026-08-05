package com.omni.panel.chart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import com.omni.panel.entity.ChartEntity;
import com.omni.panel.mapper.DataPolicyMapper;
import com.omni.panel.query.JdbcQueryExecutor;
import com.omni.panel.query.QueryProperties;
import com.omni.panel.service.ChartResultCache;
import com.omni.panel.service.SettingService;

class ChartResultCacheTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final SettingService settingService = mock(SettingService.class);
    private final DataPolicyMapper dataPolicyMapper = mock(DataPolicyMapper.class);
    private final QueryProperties properties = new QueryProperties(30, 1000, 3, 5, 524288);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 关闭时不读写() {
        when(settingService.queryCacheEnabled()).thenReturn(false);
        ChartResultCache cache = newCache(redis);

        assertThat(cache.get(sampleChart())).isEmpty();
        cache.put(sampleChart(), new JdbcQueryExecutor.QueryResult(List.of("a"), List.of()));
        verify(redis, never()).opsForValue();
    }

    @Test
    void 开启时按TTL写入() throws Exception {
        when(settingService.queryCacheEnabled()).thenReturn(true);
        when(settingService.queryCacheTtlSeconds()).thenReturn(120);
        when(redis.opsForValue()).thenReturn(values);
        when(dataPolicyMapper.fieldRuleCount(2L, 9L)).thenReturn(0);
        when(dataPolicyMapper.allowedFields(2L, 9L)).thenReturn(List.of());
        when(dataPolicyMapper.rowRules(2L, 9L)).thenReturn(List.of());
        ChartResultCache cache = newCache(redis);
        ChartEntity chart = sampleChart();
        JdbcQueryExecutor.QueryResult result =
                new JdbcQueryExecutor.QueryResult(List.of("c"), List.of(Map.of("c", 1)));

        cache.put(chart, result);

        verify(values).set(anyString(), anyString(), eq(Duration.ofSeconds(120)));
    }

    @Test
    void 命中时返回结果() throws Exception {
        when(settingService.queryCacheEnabled()).thenReturn(true);
        when(redis.opsForValue()).thenReturn(values);
        when(dataPolicyMapper.fieldRuleCount(2L, 9L)).thenReturn(0);
        when(dataPolicyMapper.allowedFields(2L, 9L)).thenReturn(List.of());
        when(dataPolicyMapper.rowRules(2L, 9L)).thenReturn(List.of());
        ChartEntity chart = sampleChart();
        ChartResultCache cache = newCache(redis);
        String json = objectMapper.writeValueAsString(
                new JdbcQueryExecutor.QueryResult(List.of("c"), List.of(Map.of("c", 1))));
        when(values.get(cache.key(chart))).thenReturn(json);

        Optional<JdbcQueryExecutor.QueryResult> hit = cache.get(chart);

        assertThat(hit).isPresent();
        assertThat(hit.get().columns()).containsExactly("c");
    }

    private ChartResultCache newCache(StringRedisTemplate template) {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(template);
        return new ChartResultCache(provider, objectMapper, settingService, properties, dataPolicyMapper);
    }

    private ChartEntity sampleChart() {
        ChartEntity chart = new ChartEntity();
        chart.setId(1L);
        chart.setOwnerId(9L);
        chart.setDatasetId(2L);
        chart.setQueryJson("{\"mode\":\"VISUAL\"}");
        chart.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return chart;
    }
}
