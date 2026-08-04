package com.omni.panel.datasource;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.omni.panel.auth.AuthenticatedUser;
import com.omni.panel.common.ApiResponse;
import com.omni.panel.datasource.dialect.DialectInfo;
import com.omni.panel.datasource.dialect.DialectRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

/**
 * 提供数据源查询、维护与连接测试接口。
 */
@RestController
@RequestMapping("/api/data-sources")
public class DataSourceController {
    private final DataSourceService service;
    private final DialectRegistry dialectRegistry;

    public DataSourceController(DataSourceService service, DialectRegistry dialectRegistry) {
        this.service = service;
        this.dialectRegistry = dialectRegistry;
    }

    /**
     * 查询当前用户可读取的数据源。
     *
     * @return 数据源视图列表
     */
    @GetMapping
    public ApiResponse<List<View>> list() {
        return ApiResponse.ok(service.listReadable().stream().map(this::toView).toList());
    }

    /**
     * 返回当前已注册、可创建连接的分析数据源方言。
     */
    @GetMapping("/dialects")
    public ApiResponse<List<DialectInfo>> dialects() {
        return ApiResponse.ok(dialectRegistry.list());
    }

    /**
     * 创建数据源并验证连接。
     *
     * @param request 数据源创建参数
     * @return 已创建的数据源视图
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<View> create(@Valid @RequestBody CreateRequest request) {
        return ApiResponse.ok(toView(service.create(
            request.name(), request.host(), request.port(), request.defaultDatabase(),
            request.username(), request.password(), request.dialect())));
    }

    /**
     * 更新指定数据源并替换其运行时连接池。
     *
     * @param id 数据源标识
     * @param request 数据源保存参数
     * @return 已更新的数据源视图
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<View> update(@PathVariable long id, @Valid @RequestBody SaveRequest request) {
        return ApiResponse.ok(toView(service.update(
            id, request.name(), request.host(), request.port(), request.defaultDatabase(),
            request.username(), request.password(), request.dialect())));
    }

    /**
     * 删除指定数据源。
     *
     * @param id 数据源标识
     * @return 无数据的成功响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    /**
     * 测试指定数据源的连接可用性。
     *
     * @param id 数据源标识
     * @return 无数据的成功响应
     */
    @PostMapping("/{id}/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> test(@PathVariable long id) {
        service.test(id);
        return ApiResponse.ok();
    }

    private View toView(DataSourceEntity entity) {
        String dialect = entity.getDialect() == null || entity.getDialect().isBlank()
            ? dialectRegistry.detectCodeOrMysql(entity.getJdbcUrl()) : entity.getDialect();
        AuthenticatedUser user = AuthenticatedUser.current();
        if (user.admin()) {
            return new View(entity.getId(), entity.getName(), entity.getHost(), entity.getPort(),
                entity.getDefaultDatabase(), entity.getJdbcUrl(), dialect,
                entity.getUsername(), entity.getStatus(), entity.getOwnerId());
        }
        return new View(entity.getId(), entity.getName(), null, null,
            entity.getDefaultDatabase(), null, dialect, null,
            entity.getStatus(), entity.getOwnerId());
    }

    /**
     * 数据源创建请求。
     *
     * @param name 数据源名称
     * @param host 主机
     * @param port 端口
     * @param defaultDatabase 可选默认库名
     * @param username 数据库用户名
     * @param password 数据库密码
     * @param dialect SQL 方言；为空时默认 MYSQL
     */
    public record CreateRequest(
        @NotBlank String name,
        @NotBlank String host,
        @NotNull @Min(1) @Max(65535) Integer port,
        String defaultDatabase,
        @NotBlank String username,
        @NotBlank String password,
        String dialect
    ) {}

    /**
     * 数据源更新请求。
     *
     * @param name 数据源名称
     * @param host 主机
     * @param port 端口
     * @param defaultDatabase 可选默认库名
     * @param username 数据库用户名
     * @param password 新数据库密码，为空时保留原密码
     * @param dialect SQL 方言；为空时默认 MYSQL
     */
    public record SaveRequest(
        @NotBlank String name,
        @NotBlank String host,
        @NotNull @Min(1) @Max(65535) Integer port,
        String defaultDatabase,
        @NotBlank String username,
        String password,
        String dialect
    ) {}

    /**
     * 不暴露加密凭据的数据源视图。
     *
     * @param id 数据源标识
     * @param name 数据源名称
     * @param host 主机（仅管理员）
     * @param port 端口（仅管理员）
     * @param defaultDatabase 默认库名
     * @param jdbcUrl 组装后的 JDBC 地址（仅管理员）
     * @param dialect SQL 方言
     * @param username 数据库用户名（仅管理员）
     * @param status 数据源状态
     * @param ownerId 所有者用户标识
     */
    public record View(
        @JsonSerialize(using = ToStringSerializer.class) long id,
        String name,
        String host,
        Integer port,
        String defaultDatabase,
        String jdbcUrl,
        String dialect,
        String username,
        String status,
        @JsonSerialize(using = ToStringSerializer.class) long ownerId
    ) {}
}
