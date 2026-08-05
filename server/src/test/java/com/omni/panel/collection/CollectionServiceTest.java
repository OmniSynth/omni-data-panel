package com.omni.panel.collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omni.panel.common.BusinessException;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.entity.ChartEntity;
import com.omni.panel.entity.CollectionEntity;
import com.omni.panel.entity.DashboardEntity;
import com.omni.panel.entity.DatasetEntity;
import com.omni.panel.entity.MetricEntity;
import com.omni.panel.mapper.ChartMapper;
import com.omni.panel.mapper.CollectionMapper;
import com.omni.panel.mapper.DashboardMapper;
import com.omni.panel.mapper.DatasetMapper;
import com.omni.panel.mapper.MetricMapper;
import com.omni.panel.mapper.UserMapper;
import com.omni.panel.service.CollectionService;
import com.omni.panel.service.PermissionService;

class CollectionServiceTest {
    private final CollectionMapper collectionMapper = mock(CollectionMapper.class);
    private final ChartMapper chartMapper = mock(ChartMapper.class);
    private final DashboardMapper dashboardMapper = mock(DashboardMapper.class);
    private final DatasetMapper datasetMapper = mock(DatasetMapper.class);
    private final MetricMapper metricMapper = mock(MetricMapper.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final CollectionService service = new CollectionService(
            collectionMapper, chartMapper, dashboardMapper, datasetMapper, metricMapper,
            mock(UserMapper.class), permissionService);

    @BeforeAll
    static void 初始化MybatisPlus实体缓存() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, CollectionEntity.class);
        TableInfoHelper.initTableInfo(assistant, ChartEntity.class);
        TableInfoHelper.initTableInfo(assistant, DashboardEntity.class);
        TableInfoHelper.initTableInfo(assistant, DatasetEntity.class);
        TableInfoHelper.initTableInfo(assistant, MetricEntity.class);
    }

    @AfterEach
    void 清理() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 个人根集合已存在时直接返回() {
        authenticate(3L);
        CollectionEntity existing = new CollectionEntity();
        existing.setId(9L);
        existing.setPersonalOwnerId(3L);
        when(collectionMapper.selectOne(any())).thenReturn(existing);

        assertThat(service.ensurePersonalCollection(3L).getId()).isEqualTo(9L);
    }

    @Test
    void 删除个人根集合被拒绝() {
        authenticate(3L);
        CollectionEntity personal = new CollectionEntity();
        personal.setId(1L);
        personal.setOwnerId(3L);
        personal.setPersonalOwnerId(3L);
        personal.setArchived(false);
        when(collectionMapper.selectById(1L)).thenReturn(personal);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("个人根集合");
    }

    @Test
    void 删除空集合时会解除废纸篓资源引用() {
        authenticate(3L);
        CollectionEntity collection = new CollectionEntity();
        collection.setId(8L);
        collection.setOwnerId(3L);
        collection.setArchived(false);
        when(collectionMapper.selectById(8L)).thenReturn(collection);
        when(collectionMapper.selectCount(any())).thenReturn(0L);
        when(chartMapper.selectCount(any())).thenReturn(0L);
        when(dashboardMapper.selectCount(any())).thenReturn(0L);
        when(datasetMapper.selectCount(any())).thenReturn(0L);
        when(metricMapper.selectCount(any())).thenReturn(0L);

        service.delete(8L);

        verify(chartMapper).update(eq(null), any(Wrapper.class));
        verify(dashboardMapper).update(eq(null), any(Wrapper.class));
        verify(datasetMapper).update(eq(null), any(Wrapper.class));
        verify(metricMapper).update(eq(null), any(Wrapper.class));
        verify(permissionService).deleteResource("COLLECTION", 8L);
        verify(collectionMapper).deleteById(8L);
    }

    @Test
    void 删除仍有活跃内容的集合被拒绝() {
        authenticate(3L);
        CollectionEntity collection = new CollectionEntity();
        collection.setId(8L);
        collection.setOwnerId(3L);
        collection.setArchived(false);
        when(collectionMapper.selectById(8L)).thenReturn(collection);
        when(collectionMapper.selectCount(any())).thenReturn(0L);
        when(chartMapper.selectCount(any())).thenReturn(0L);
        when(dashboardMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(8L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仍有内容");
    }

    private void authenticate(long userId) {
        AuthenticatedUser user = new AuthenticatedUser(userId, "u", false, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
}
