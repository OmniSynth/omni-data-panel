package com.omni.panel.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.entity.CollectionEntity;
import com.omni.panel.entity.SysUser;
import com.omni.panel.mapper.ChartMapper;
import com.omni.panel.mapper.CollectionMapper;
import com.omni.panel.mapper.DashboardMapper;
import com.omni.panel.mapper.DatasetMapper;
import com.omni.panel.mapper.MetricMapper;
import com.omni.panel.mapper.UserMapper;
import com.omni.panel.service.CollectionService;
import com.omni.panel.service.PermissionService;

class CollectionPermissionTreeTest {
    @AfterEach
    void 清理上下文() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @SuppressWarnings("unchecked")
    void 共享集合出现在树中() {
        CollectionMapper collectionMapper = mock(CollectionMapper.class);
        PermissionService permissionService = mock(PermissionService.class);
        CollectionService service = new CollectionService(
                collectionMapper,
                mock(ChartMapper.class),
                mock(DashboardMapper.class),
                mock(DatasetMapper.class),
                mock(MetricMapper.class),
                mock(UserMapper.class),
                permissionService);

        CollectionEntity personal = new CollectionEntity();
        personal.setId(1L);
        personal.setName("你的个人集合");
        personal.setOwnerId(7L);
        personal.setPersonalOwnerId(7L);
        personal.setArchived(false);

        CollectionEntity shared = new CollectionEntity();
        shared.setId(2L);
        shared.setName("业务集合");
        shared.setOwnerId(9L);
        shared.setArchived(false);

        when(collectionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(personal);
        when(collectionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(personal, shared));
        when(permissionService.canRead(eq("COLLECTION"), eq(1L), eq(7L))).thenReturn(true);
        when(permissionService.canRead(eq("COLLECTION"), eq(2L), eq(9L))).thenReturn(true);

        AuthenticatedUser user = new AuthenticatedUser(7, "tester", false, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));

        List<CollectionService.CollectionNode> tree = service.tree();
        assertThat(tree).extracting(CollectionService.CollectionNode::id).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 他人个人集合展示为所有者名称() {
        CollectionMapper collectionMapper = mock(CollectionMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        PermissionService permissionService = mock(PermissionService.class);
        CollectionService service = new CollectionService(
                collectionMapper,
                mock(ChartMapper.class),
                mock(DashboardMapper.class),
                mock(DatasetMapper.class),
                mock(MetricMapper.class),
                userMapper,
                permissionService);

        CollectionEntity mine = new CollectionEntity();
        mine.setId(1L);
        mine.setName("你的个人集合");
        mine.setOwnerId(7L);
        mine.setPersonalOwnerId(7L);
        mine.setArchived(false);

        CollectionEntity theirs = new CollectionEntity();
        theirs.setId(2L);
        theirs.setName("你的个人集合");
        theirs.setOwnerId(9L);
        theirs.setPersonalOwnerId(9L);
        theirs.setArchived(false);

        SysUser other = new SysUser();
        other.setId(9L);
        other.setUsername("alice");
        other.setDisplayName("爱丽丝");

        when(collectionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mine);
        when(collectionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(mine, theirs));
        when(permissionService.canRead(eq("COLLECTION"), eq(1L), eq(7L))).thenReturn(true);
        when(permissionService.canRead(eq("COLLECTION"), eq(2L), eq(9L))).thenReturn(true);
        when(userMapper.selectById(9L)).thenReturn(other);

        AuthenticatedUser user = new AuthenticatedUser(7, "tester", true, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));

        List<CollectionService.CollectionNode> tree = service.tree();
        assertThat(tree).extracting(CollectionService.CollectionNode::name)
                .containsExactlyInAnyOrder("你的个人集合", "爱丽丝 的个人集合");
    }
}
