package com.omni.panel.metadata;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.omni.panel.common.BusinessException;
import com.omni.panel.datasource.DataSourceEntity;
import com.omni.panel.datasource.DataSourceRegistry;
import com.omni.panel.datasource.DataSourceMapper;
import com.omni.panel.datasource.DataSourceService;
import com.omni.panel.datasource.dialect.DialectColumnInfo;
import com.omni.panel.datasource.dialect.DialectPlugin;
import com.omni.panel.datasource.dialect.DialectRegistry;
import com.omni.panel.datasource.dialect.DialectTableInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 同步并缓存数据源的模式、表和字段元数据快照。
 */
@Service
public class MetadataService {
    private static final Logger log = LoggerFactory.getLogger(MetadataService.class);

    private final MetadataMapper mapper;
    private final DataSourceService dataSourceService;
    private final DataSourceMapper dataSourceMapper;
    private final DataSourceRegistry registry;
    private final DialectRegistry dialectRegistry;
    private final Cache<String, Object> cache = Caffeine.newBuilder()
        .maximumSize(2000).expireAfterWrite(Duration.ofMinutes(5)).build();

    public MetadataService(MetadataMapper mapper, DataSourceService dataSourceService,
                           DataSourceMapper dataSourceMapper, DataSourceRegistry registry,
                           DialectRegistry dialectRegistry) {
        this.mapper = mapper;
        this.dataSourceService = dataSourceService;
        this.dataSourceMapper = dataSourceMapper;
        this.registry = registry;
        this.dialectRegistry = dialectRegistry;
    }

    /**
     * 在写权限校验后重建指定数据源的元数据快照，并清空查询缓存。
     *
     * @param sourceId 数据源标识
     * @throws BusinessException 数据源不存在、权限不足或元数据读取失败时抛出
     */
    @Transactional
    public void sync(long sourceId) {
        dataSourceService.requireAdministrator();
        DataSourceEntity source = dataSourceMapper.selectById(sourceId);
        if (source == null) {
            throw new BusinessException(404, "数据源不存在");
        }
        syncSource(dataSourceService.ensureConnectionFields(source));
    }

    /**
     * 供系统任务跳过用户权限上下文并重建元数据快照。
     *
     * @param sourceId 数据源标识
     * @throws BusinessException 数据源不存在或元数据读取失败时抛出
     */
    @Transactional
    public void syncSystem(long sourceId) {
        DataSourceEntity source = dataSourceMapper.selectById(sourceId);
        if (source == null) {
            throw new BusinessException(404, "数据源不存在");
        }
        syncSource(dataSourceService.ensureConnectionFields(source));
    }

    private void syncSource(DataSourceEntity source) {
        long sourceId = source.getId();
        DialectPlugin dialect = dialectRegistry.resolve(source);
        try (Connection connection = registry.get(source).getConnection()) {
            connection.setReadOnly(true);
            String originalCatalog = connection.getCatalog();
            DatabaseMetaData metadata = connection.getMetaData();
            List<String> catalogs = resolveCatalogs(source, connection, metadata, dialect);
            mapper.deleteColumns(sourceId);
            mapper.deleteTables(sourceId);
            mapper.deleteSchemas(sourceId);
            List<String> failures = new ArrayList<>();
            int synced = 0;
            try {
                for (String catalog : catalogs) {
                    try {
                        syncCatalog(connection, metadata, sourceId, catalog, dialect);
                        synced++;
                    } catch (SQLException exception) {
                        failures.add(catalog + "：" + exception.getMessage());
                        log.warn("同步库 {} 失败（数据源 {}）：{}", catalog, sourceId, exception.getMessage());
                    }
                }
            } finally {
                try {
                    dialect.restoreConnection(connection, source.getDefaultDatabase(), originalCatalog);
                } catch (SQLException exception) {
                    log.debug("归还连接时恢复命名空间失败：{}", exception.getMessage());
                }
            }
            cache.invalidateAll();
            if (synced == 0) {
                throw new BusinessException("元数据同步失败：" + String.join("；", failures));
            }
            if (!failures.isEmpty()) {
                log.warn("数据源 {} 部分库同步失败：{}", sourceId, failures);
            }
        } catch (SQLException exception) {
            throw new BusinessException("元数据同步失败：" + exception.getMessage());
        }
    }

    /**
     * 有默认库时仅同步该库；否则同步账号可见的全部业务库。
     */
    private List<String> resolveCatalogs(DataSourceEntity source, Connection connection,
                                         DatabaseMetaData metadata, DialectPlugin dialect)
        throws SQLException {
        String defaultDatabase = source.getDefaultDatabase();
        if (defaultDatabase != null && !defaultDatabase.isBlank()) {
            return List.of(defaultDatabase.trim());
        }
        List<String> catalogs = dialect.listNamespaces(connection, metadata);
        if (catalogs.isEmpty()) {
            throw new BusinessException("未发现可同步的业务库，请填写默认库名或检查账号元数据权限");
        }
        return catalogs;
    }

    private void syncCatalog(Connection connection, DatabaseMetaData metadata, long sourceId,
                             String catalog, DialectPlugin dialect) throws SQLException {
        mapper.insertSchema(sourceId, catalog);
        dialect.useNamespace(connection, catalog);
        for (TableRef table : listTables(connection, metadata, catalog, dialect)) {
            mapper.insertTable(sourceId, catalog, table.name(), table.comment());
            syncColumns(connection, metadata, sourceId, catalog, table.name(), dialect);
        }
    }

    private List<TableRef> listTables(Connection connection, DatabaseMetaData metadata, String catalog,
                                      DialectPlugin dialect) throws SQLException {
        List<TableRef> tables = new ArrayList<>();
        try (ResultSet resultSet = metadata.getTables(catalog, null, "%", new String[]{"TABLE", "VIEW"})) {
            while (resultSet.next()) {
                String table = resultSet.getString("TABLE_NAME");
                if (table != null && !table.isBlank()) {
                    tables.add(new TableRef(table, resultSet.getString("REMARKS")));
                }
            }
        }
        if (!tables.isEmpty()) {
            return tables;
        }
        for (DialectTableInfo table : dialect.listTablesFallback(connection, catalog)) {
            tables.add(new TableRef(table.name(), table.comment()));
        }
        return tables;
    }

    private void syncColumns(Connection connection, DatabaseMetaData metadata, long sourceId,
                             String schema, String table, DialectPlugin dialect) throws SQLException {
        dialect.useNamespace(connection, schema);
        Set<String> primaryKeys = new HashSet<>();
        try (ResultSet keys = metadata.getPrimaryKeys(schema, null, table)) {
            while (keys.next()) {
                primaryKeys.add(keys.getString("COLUMN_NAME"));
            }
        }
        Map<String, String[]> foreignKeys = new HashMap<>();
        try (ResultSet keys = metadata.getImportedKeys(schema, null, table)) {
            while (keys.next()) {
                foreignKeys.put(keys.getString("FKCOLUMN_NAME"),
                    new String[]{keys.getString("PKTABLE_NAME"), keys.getString("PKCOLUMN_NAME")});
            }
        }
        boolean inserted = false;
        try (ResultSet columns = metadata.getColumns(schema, null, table, "%")) {
            while (columns.next()) {
                insertColumnRow(sourceId, schema, table, primaryKeys, foreignKeys, columns);
                inserted = true;
            }
        }
        if (inserted) {
            return;
        }
        for (DialectColumnInfo column : dialect.listColumnsFallback(connection, schema, table)) {
            String[] foreign = foreignKeys.get(column.columnName());
            mapper.insertColumn(sourceId, schema, table, column.columnName(),
                java.sql.Types.OTHER, column.typeName(),
                column.columnSize(), column.decimalDigits(),
                column.nullable(),
                primaryKeys.contains(column.columnName()), foreign != null,
                foreign == null ? null : foreign[0], foreign == null ? null : foreign[1],
                column.position(), column.comment());
        }
    }

    private void insertColumnRow(long sourceId, String schema, String table, Set<String> primaryKeys,
                                 Map<String, String[]> foreignKeys, ResultSet columns) throws SQLException {
        String columnName = columns.getString("COLUMN_NAME");
        Integer columnSize = (Integer) columns.getObject("COLUMN_SIZE");
        Integer decimalDigits = (Integer) columns.getObject("DECIMAL_DIGITS");
        String[] foreign = foreignKeys.get(columnName);
        mapper.insertColumn(sourceId, schema, table, columnName,
            columns.getInt("DATA_TYPE"), columns.getString("TYPE_NAME"),
            columnSize, decimalDigits,
            columns.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
            primaryKeys.contains(columnName), foreign != null,
            foreign == null ? null : foreign[0], foreign == null ? null : foreign[1],
            columns.getInt("ORDINAL_POSITION"), columns.getString("REMARKS"));
    }

    /**
     * 查询当前用户可读数据源的模式名称，结果使用短期缓存。
     *
     * @param sourceId 数据源标识
     * @return 模式名称列表
     * @throws BusinessException 数据源不存在或当前用户权限不足时抛出
     */
    @SuppressWarnings("unchecked")
    public List<String> schemas(long sourceId) {
        dataSourceService.require(sourceId, "READ");
        return (List<String>) cache.get(sourceId + ":schemas", ignored -> List.copyOf(mapper.schemas(sourceId)));
    }

    /**
     * 查询当前用户可读数据源中指定模式的表与视图，结果使用短期缓存。
     *
     * @param sourceId 数据源标识
     * @param schema 模式名称
     * @return 表视图列表
     * @throws BusinessException 数据源不存在或当前用户权限不足时抛出
     */
    @SuppressWarnings("unchecked")
    public List<MetadataMapper.TableView> tables(long sourceId, String schema) {
        dataSourceService.require(sourceId, "READ");
        return (List<MetadataMapper.TableView>) cache.get(sourceId + ":tables:" + schema,
            ignored -> List.copyOf(mapper.tables(sourceId, schema)));
    }

    /**
     * 查询当前用户可读数据源中指定表的字段，结果使用短期缓存。
     *
     * @param sourceId 数据源标识
     * @param schema 模式名称
     * @param table 表名称
     * @return 字段视图列表
     * @throws BusinessException 数据源不存在或当前用户权限不足时抛出
     */
    @SuppressWarnings("unchecked")
    public List<MetadataMapper.ColumnView> columns(long sourceId, String schema, String table) {
        dataSourceService.require(sourceId, "READ");
        return (List<MetadataMapper.ColumnView>) cache.get(sourceId + ":columns:" + schema + ":" + table,
            ignored -> List.copyOf(mapper.columns(sourceId, schema, table)));
    }

    /**
     * 构建 SQL 编辑器补全用的方言与表字段目录。
     *
     * @param sourceId 数据源标识
     * @return 补全目录
     */
    @SuppressWarnings("unchecked")
    public CompletionSchema completionSchema(long sourceId) {
        DataSourceEntity source = dataSourceService.require(sourceId, "READ");
        return (CompletionSchema) cache.get(sourceId + ":completion-schema", ignored -> {
            Map<String, Map<String, List<String>>> schemas = new LinkedHashMap<>();
            for (MetadataMapper.CompletionColumn column : mapper.completionColumns(sourceId)) {
                schemas
                    .computeIfAbsent(column.schemaName(), key -> new LinkedHashMap<>())
                    .computeIfAbsent(column.tableName(), key -> new ArrayList<>())
                    .add(column.columnName());
            }
            String dialect = source.getDialect() == null || source.getDialect().isBlank()
                ? dialectRegistry.detectCodeOrMysql(source.getJdbcUrl()) : source.getDialect();
            Map<String, Map<String, List<String>>> frozen = new LinkedHashMap<>();
            schemas.forEach((schema, tables) -> {
                Map<String, List<String>> frozenTables = new LinkedHashMap<>();
                tables.forEach((table, columns) -> frozenTables.put(table, List.copyOf(columns)));
                frozen.put(schema, Map.copyOf(frozenTables));
            });
            return new CompletionSchema(dialect, Map.copyOf(frozen));
        });
    }

    /**
     * SQL 补全目录。
     *
     * @param dialect 数据源方言
     * @param schemas 模式 → 表 → 字段列表
     */
    public record CompletionSchema(String dialect, Map<String, Map<String, List<String>>> schemas) {}

    private record TableRef(String name, String comment) {}
}
