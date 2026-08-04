package com.omni.panel.datasource;

import com.omni.panel.auth.AuthenticatedUser;
import com.omni.panel.common.BusinessException;
import com.omni.panel.datasource.dialect.DialectPlugin;
import com.omni.panel.datasource.dialect.DialectRegistry;
import com.omni.panel.datasource.dialect.JdbcConnectionFields;
import com.omni.panel.datasource.dialect.ParsedJdbcUrl;
import com.omni.panel.permission.PermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理数据源配置、访问权限、凭据加密及运行时连接池。
 */
@Service
public class DataSourceService {
    private final DataSourceMapper mapper;
    private final CredentialCrypto crypto;
    private final DataSourceRegistry registry;
    private final DialectRegistry dialectRegistry;
    private final PermissionService permissionService;

    public DataSourceService(DataSourceMapper mapper, CredentialCrypto crypto, DataSourceRegistry registry,
                             DialectRegistry dialectRegistry, PermissionService permissionService) {
        this.mapper = mapper;
        this.crypto = crypto;
        this.registry = registry;
        this.dialectRegistry = dialectRegistry;
        this.permissionService = permissionService;
    }

    /**
     * 查询当前用户可读取的数据源。
     *
     * @return 可读数据源列表
     */
    public List<DataSourceEntity> listReadable() {
        return mapper.selectList(null).stream()
            .map(this::ensureConnectionFields)
            .filter(source -> permissionService.canRead("DATA_SOURCE", source.getId(), source.getOwnerId()))
            .toList();
    }

    /**
     * 获取数据源并校验当前用户具备指定权限。
     *
     * @param id 数据源标识
     * @param permission 所需权限
     * @return 已通过权限校验的数据源
     * @throws BusinessException 数据源不存在或当前用户权限不足时抛出
     */
    public DataSourceEntity require(long id, String permission) {
        DataSourceEntity source = ensureConnectionFields(requireExisting(id));
        permissionService.require("DATA_SOURCE", id, source.getOwnerId(), permission);
        return source;
    }

    /**
     * 创建数据源，验证连接后注册运行时连接池。
     *
     * @param name 数据源名称
     * @param host 主机名或 IP
     * @param port 端口
     * @param defaultDatabase 可选默认库名
     * @param username 数据库用户名
     * @param password 数据库密码明文
     * @param dialect SQL 方言
     * @return 已创建的数据源
     */
    @Transactional
    public DataSourceEntity create(String name, String host, Integer port, String defaultDatabase,
                                   String username, String password, String dialect) {
        requireAdministrator();
        DialectPlugin plugin = dialectRegistry.require(dialect);
        String normalizedHost = JdbcConnectionFields.requireHost(host);
        int normalizedPort = JdbcConnectionFields.requirePort(port);
        String database = JdbcConnectionFields.blankToNull(defaultDatabase);
        String jdbcUrl = plugin.buildJdbcUrl(normalizedHost, normalizedPort, database);
        plugin.validateJdbcUrl(jdbcUrl);
        DataSourceEntity source = new DataSourceEntity();
        source.setName(name);
        source.setHost(normalizedHost);
        source.setPort(normalizedPort);
        source.setDefaultDatabase(database);
        source.setJdbcUrl(jdbcUrl);
        source.setDialect(plugin.code());
        source.setUsername(username);
        source.setEncryptedPassword(crypto.encrypt(password));
        source.setOwnerId(AuthenticatedUser.current().id());
        source.setStatus("ACTIVE");
        source.setCreatedAt(LocalDateTime.now());
        source.setUpdatedAt(source.getCreatedAt());
        mapper.insert(source);
        test(source);
        registry.replace(source);
        registry.warmUp(source);
        return source;
    }

    /**
     * 更新数据源配置，验证新连接后替换运行时连接池；密码为空时保留原凭据。
     *
     * @param id 数据源标识
     * @param name 数据源名称
     * @param host 主机名或 IP
     * @param port 端口
     * @param defaultDatabase 可选默认库名
     * @param username 数据库用户名
     * @param password 新数据库密码明文，可为空
     * @param dialect SQL 方言
     * @return 已更新的数据源
     */
    @Transactional
    public DataSourceEntity update(long id, String name, String host, Integer port, String defaultDatabase,
                                   String username, String password, String dialect) {
        requireAdministrator();
        DataSourceEntity source = requireExisting(id);
        DialectPlugin plugin = dialectRegistry.require(dialect);
        String normalizedHost = JdbcConnectionFields.requireHost(host);
        int normalizedPort = JdbcConnectionFields.requirePort(port);
        String database = JdbcConnectionFields.blankToNull(defaultDatabase);
        String jdbcUrl = plugin.buildJdbcUrl(normalizedHost, normalizedPort, database);
        plugin.validateJdbcUrl(jdbcUrl);
        source.setName(name);
        source.setHost(normalizedHost);
        source.setPort(normalizedPort);
        source.setDefaultDatabase(database);
        source.setJdbcUrl(jdbcUrl);
        source.setDialect(plugin.code());
        source.setUsername(username);
        if (password != null && !password.isBlank()) {
            source.setEncryptedPassword(crypto.encrypt(password));
        }
        source.setUpdatedAt(LocalDateTime.now());
        test(source);
        mapper.updateById(source);
        registry.replace(source);
        registry.warmUp(source);
        return source;
    }

    /**
     * 删除数据源配置并关闭对应运行时连接池。
     *
     * @param id 数据源标识
     * @throws BusinessException 数据源不存在或当前用户权限不足时抛出
     */
    @Transactional
    public void delete(long id) {
        requireAdministrator();
        DataSourceEntity source = requireExisting(id);
        permissionService.deleteResource("DATA_SOURCE", id);
        mapper.deleteById(source.getId());
        registry.remove(id);
    }

    /**
     * 校验当前用户可读的数据源能否建立有效连接。
     *
     * @param id 数据源标识
     * @throws BusinessException 数据源不存在、权限不足或连接不可用时抛出
     */
    public void test(long id) {
        requireAdministrator();
        test(ensureConnectionFields(requireExisting(id)));
    }

    /**
     * 从旧 jdbc_url 回填缺失的 host/port/defaultDatabase。
     */
    public void backfillMissingConnectionFields() {
        for (DataSourceEntity source : mapper.selectList(null)) {
            ensureConnectionFields(source);
        }
    }

    /**
     * 若结构化连接字段缺失，则从 jdbc_url 解析并持久化。
     *
     * @param source 数据源实体
     * @return 同一实体（可能已回填）
     */
    public DataSourceEntity ensureConnectionFields(DataSourceEntity source) {
        if (source == null) {
            return null;
        }
        if (source.getHost() != null && !source.getHost().isBlank() && source.getPort() != null) {
            return source;
        }
        ParsedJdbcUrl parsed = dialectRegistry.parseAny(source.getJdbcUrl());
        if (parsed == null) {
            return source;
        }
        source.setHost(parsed.host());
        source.setPort(parsed.port());
        if (source.getDefaultDatabase() == null || source.getDefaultDatabase().isBlank()) {
            source.setDefaultDatabase(parsed.defaultDatabase());
        }
        if (source.getDialect() == null || source.getDialect().isBlank()) {
            source.setDialect(dialectRegistry.detectCodeOrMysql(source.getJdbcUrl()));
        }
        source.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(source);
        return source;
    }

    private void test(DataSourceEntity source) {
        registry.testConnection(source);
    }

    /**
     * 仅允许管理员执行数据源维护操作。
     */
    public void requireAdministrator() {
        if (!AuthenticatedUser.current().admin()) {
            throw new BusinessException(403, "仅管理员可管理数据源");
        }
    }

    private DataSourceEntity requireExisting(long id) {
        DataSourceEntity source = mapper.selectById(id);
        if (source == null) {
            throw new BusinessException(404, "数据源不存在");
        }
        return source;
    }
}
