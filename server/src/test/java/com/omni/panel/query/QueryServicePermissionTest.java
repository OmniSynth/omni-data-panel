package com.omni.panel.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.panel.auth.AuthenticatedUser;
import com.omni.panel.common.BusinessException;
import com.omni.panel.dataset.DatasetFieldEntity;
import com.omni.panel.dataset.DatasetEntity;
import com.omni.panel.dataset.DatasetService;
import com.omni.panel.datasource.DataSourceEntity;
import com.omni.panel.datasource.DataSourceMapper;
import com.omni.panel.datasource.DataSourceService;
import com.omni.panel.datasource.dialect.DialectRegistry;
import com.omni.panel.datasource.dialect.MysqlDialectPlugin;
import com.omni.panel.permission.DataPolicyMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueryServicePermissionTest {
    private final DatasetService datasetService = mock(DatasetService.class);
    private final DataSourceService dataSourceService = mock(DataSourceService.class);
    private final DataSourceMapper dataSourceMapper = mock(DataSourceMapper.class);
    private final DataPolicyMapper dataPolicyMapper = mock(DataPolicyMapper.class);
    private final QueryCompiler compiler = mock(QueryCompiler.class);
    private final SqlPolicyGuard sqlPolicyGuard = mock(SqlPolicyGuard.class);
    private final JdbcQueryExecutor executor = mock(JdbcQueryExecutor.class);
    private final DialectRegistry dialectRegistry = new DialectRegistry(List.of(new MysqlDialectPlugin()));
    private final QueryStateStore stateStore = mock(QueryStateStore.class);
    private final QueryAuditMapper auditMapper = mock(QueryAuditMapper.class);
    private final QueryService service = new QueryService(datasetService, dataSourceService, dataSourceMapper,
        dataPolicyMapper, compiler, sqlPolicyGuard, executor, dialectRegistry, stateStore, auditMapper,
        new ObjectMapper());

    @AfterEach
    void 清理上下文() {
        SecurityContextHolder.clearContext();
        service.close();
    }

    @Test
    void 非管理员执行原生SQL必须具有明确权限() {
        AuthenticatedUser user = authenticate(false, List.of("query:execute"));
        QueryService.QuerySubmission submission =
            new QueryService.QuerySubmission(1L, "SELECT 1", List.of(), null);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "rawExecution", submission, user))
            .isInstanceOf(BusinessException.class).hasMessageContaining("原生 SQL");
        verify(dataSourceService, never()).require(1L, "READ");
    }

    @Test
    void 具有原生SQL权限时继续校验数据源访问权() {
        AuthenticatedUser user = authenticate(false, List.of("query:execute", "query:raw"));
        DataSourceEntity source = new DataSourceEntity();
        source.setId(1L);
        when(dataSourceService.require(1L, "READ")).thenReturn(source);

        ReflectionTestUtils.invokeMethod(service, "rawExecution",
            new QueryService.QuerySubmission(1L, "SELECT 1", List.of(), null), user);

        verify(dataSourceService).require(1L, "READ");
    }

    @Test
    void 语义查询在数据集校验后再次校验底层数据源() {
        AuthenticatedUser user = authenticate(false, List.of("query:execute"));
        DatasetEntity dataset = new DatasetEntity();
        dataset.setId(8L);
        dataset.setDataSourceId(1L);
        dataset.setSchemaName("app");
        dataset.setTableName("orders");
        QueryRequest request = new QueryRequest(8L, List.of(), List.of(), null, List.of(), 10);
        when(datasetService.require(8L, "READ")).thenReturn(dataset);
        when(datasetService.fields(8L)).thenReturn(List.of());
        when(dataPolicyMapper.fieldRuleCount(8L, user.id())).thenReturn(0);
        when(dataPolicyMapper.rowRules(8L, user.id())).thenReturn(List.of());
        DataSourceEntity source = new DataSourceEntity();
        source.setId(1L);
        source.setDialect("MYSQL");
        when(dataSourceService.require(1L, "READ")).thenReturn(source);
        when(compiler.compile(
            org.mockito.ArgumentMatchers.eq(request), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
            .thenReturn(new QueryCompiler.CompiledQuery("SELECT 1", List.of()));

        ReflectionTestUtils.invokeMethod(service, "semanticExecution", request, user);

        verify(datasetService).require(8L, "READ");
        verify(dataSourceService).require(1L, "READ");
    }

    @Test
    @SuppressWarnings("unchecked")
    void 字段规则采用存在即白名单语义且管理员不受限() {
        DatasetFieldEntity region = field("地区");
        DatasetFieldEntity amount = field("销售额");
        AuthenticatedUser user = authenticate(false, List.of());
        when(dataPolicyMapper.fieldRuleCount(8L, user.id())).thenReturn(1);
        when(dataPolicyMapper.allowedFields(8L, user.id())).thenReturn(List.of("地区"));

        Set<String> denied = (Set<String>) ReflectionTestUtils.invokeMethod(
            service, "deniedFields", 8L, user, List.of(region, amount));
        Set<String> adminDenied = (Set<String>) ReflectionTestUtils.invokeMethod(
            service, "deniedFields", 8L, authenticate(true, List.of()), List.of(region, amount));

        assertThat(denied).containsExactly("销售额");
        assertThat(adminDenied).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void 没有字段规则时默认允许全部字段() {
        AuthenticatedUser user = authenticate(false, List.of());
        when(dataPolicyMapper.fieldRuleCount(8L, user.id())).thenReturn(0);

        Set<String> denied = (Set<String>) ReflectionTestUtils.invokeMethod(
            service, "deniedFields", 8L, user, List.of(field("地区"), field("销售额")));

        assertThat(denied).isEmpty();
        verify(dataPolicyMapper, never()).allowedFields(8L, user.id());
    }

    @Test
    void 提交查询要求管理员或queryExecute权限() {
        authenticate(false, List.of());
        QueryService.QuerySubmission submission =
            new QueryService.QuerySubmission(1L, "SELECT 1", List.of(), null);

        assertThatThrownBy(() -> service.submit(submission, null))
            .isInstanceOf(BusinessException.class).hasMessageContaining("查询执行权限");
    }

    private AuthenticatedUser authenticate(boolean admin, List<String> permissions) {
        AuthenticatedUser user = new AuthenticatedUser(7L, "tester", admin, permissions);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, List.of()));
        return user;
    }

    private DatasetFieldEntity field(String name) {
        DatasetFieldEntity field = new DatasetFieldEntity();
        field.setName(name);
        return field;
    }
}
