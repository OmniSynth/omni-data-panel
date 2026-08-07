package com.omni.panel.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import com.omni.panel.common.BusinessException;
import com.omni.panel.common.ClientRequestInfo;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.entity.ExportTaskEntity;
import com.omni.panel.export.MinioProperties;
import com.omni.panel.mapper.ExportTaskMapper;
import com.omni.panel.query.JdbcQueryExecutor;
import com.omni.panel.query.QueryStateStore;

/**
 * 将成功查询结果导出为 CSV 或 XLSX，支持同步内存生成和基于 MinIO 的异步任务。
 */
@Service
public class ExportService {
    private final QueryService queryService;
    private final ExportTaskMapper mapper;
    private final ExportAuditService exportAuditService;
    private final MinioProperties properties;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 注入查询、导出任务持久化、导出审计与 MinIO 配置。
     */
    public ExportService(QueryService queryService,
                         ExportTaskMapper mapper,
                         ExportAuditService exportAuditService,
                         MinioProperties properties) {
        this.queryService = queryService;
        this.mapper = mapper;
        this.exportAuditService = exportAuditService;
        this.properties = properties;
    }

    /**
     * 在当前请求内生成完整导出内容。
     */
    public byte[] synchronous(String queryId, String format, ClientRequestInfo.Info client) {
        QueryStateStore.QuerySnapshot snapshot = queryService.get(queryId);
        if (!"SUCCEEDED".equals(snapshot.status()) || snapshot.result() == null) {
            throw new BusinessException("仅成功查询可以导出");
        }
        String normalized = normalize(format);
        Long userId = AuthenticatedUser.current().id();
        Integer rowCount = snapshot.result().rows() == null ? 0 : snapshot.result().rows().size();
        try {
            byte[] content = generate(snapshot.result(), normalized);
            exportAuditService.record(
                    userId,
                    queryId,
                    snapshot.sourceId(),
                    normalized,
                    "SYNC",
                    "SUCCEEDED",
                    rowCount,
                    (long) content.length,
                    null,
                    client,
                    null);
            return content;
        } catch (RuntimeException ex) {
            exportAuditService.record(
                    userId,
                    queryId,
                    snapshot.sourceId(),
                    normalized,
                    "SYNC",
                    "FAILED",
                    rowCount,
                    null,
                    null,
                    client,
                    ex.getMessage());
            throw ex;
        }
    }

    /**
     * 创建异步导出任务并交由虚拟线程生成文件、上传 MinIO。
     */
    public String asynchronous(String queryId, String format, ClientRequestInfo.Info client) {
        if (!properties.configured()) {
            throw new BusinessException(503, "MinIO 未配置，无法执行异步导出");
        }
        QueryStateStore.QuerySnapshot snapshot = queryService.get(queryId);
        if (!"SUCCEEDED".equals(snapshot.status()) || snapshot.result() == null) {
            throw new BusinessException("仅成功查询可以导出");
        }
        String normalized = normalize(format);
        ExportTaskEntity task = new ExportTaskEntity();
        task.setId(UUID.randomUUID().toString());
        task.setOwnerId(AuthenticatedUser.current().id());
        task.setQueryId(queryId);
        task.setFormat(normalized);
        task.setStatus("QUEUED");
        mapper.insert(task);
        long sourceId = snapshot.sourceId();
        long ownerId = task.getOwnerId();
        Integer rowCount = snapshot.result().rows() == null ? 0 : snapshot.result().rows().size();
        executor.submit(() -> upload(task, snapshot.result(), ownerId, sourceId, rowCount, client));
        return task.getId();
    }

    /**
     * 查询导出任务，并执行所有者边界校验。
     */
    public ExportTaskEntity get(String taskId) {
        ExportTaskEntity task = mapper.selectById(taskId);
        AuthenticatedUser user = AuthenticatedUser.current();
        if (task == null) throw new BusinessException(404, "导出任务不存在");
        if (!user.admin() && task.getOwnerId() != user.id()) {
            throw new BusinessException(403, "无权访问该导出任务");
        }
        return task;
    }

    /**
     * 打开已成功导出文件的 MinIO 对象流。
     */
    public Download download(String taskId) {
        ExportTaskEntity task = get(taskId);
        if (!"SUCCEEDED".equals(task.getStatus()) || task.getObjectName() == null) {
            throw new BusinessException("导出任务尚未完成");
        }
        if (!properties.configured()) {
            throw new BusinessException(503, "MinIO 未配置，无法下载导出文件");
        }
        try {
            MinioClient client = client();
            var stream = client.getObject(GetObjectArgs.builder().bucket(properties.bucket())
                    .object(task.getObjectName()).build());
            return new Download(stream, "查询结果." + task.getFormat().toLowerCase(Locale.ROOT), task.getFormat());
        } catch (Exception exception) {
            throw new BusinessException(500, "读取导出文件失败：" + exception.getMessage());
        }
    }

    /**
     * 在后台推进任务状态，生成文件并上传 MinIO；完成后写入导出审计。
     */
    private void upload(ExportTaskEntity task,
                        JdbcQueryExecutor.QueryResult result,
                        long ownerId,
                        long sourceId,
                        Integer rowCount,
                        ClientRequestInfo.Info client) {
        task.setStatus("RUNNING");
        mapper.updateById(task);
        try {
            byte[] content = generate(result, task.getFormat());
            String objectName = "exports/" + task.getId() + "." + task.getFormat().toLowerCase(Locale.ROOT);
            MinioClient minio = client();
            if (!minio.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build())) {
                minio.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
            }
            minio.putObject(PutObjectArgs.builder().bucket(properties.bucket()).object(objectName)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType("CSV".equals(task.getFormat()) ? "text/csv" :
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").build());
            task.setObjectName(objectName);
            task.setStatus("SUCCEEDED");
            exportAuditService.record(
                    ownerId,
                    task.getQueryId(),
                    sourceId,
                    task.getFormat(),
                    "ASYNC",
                    "SUCCEEDED",
                    rowCount,
                    (long) content.length,
                    task.getId(),
                    client,
                    null);
        } catch (Exception exception) {
            task.setStatus("FAILED");
            task.setErrorMessage("MinIO 异步导出失败：" + exception.getMessage());
            exportAuditService.record(
                    ownerId,
                    task.getQueryId(),
                    sourceId,
                    task.getFormat(),
                    "ASYNC",
                    "FAILED",
                    rowCount,
                    null,
                    task.getId(),
                    client,
                    task.getErrorMessage());
        }
        mapper.updateById(task);
    }

    private byte[] generate(JdbcQueryExecutor.QueryResult result, String format) {
        return "CSV".equals(format) ? csv(result) : xlsx(result);
    }

    private byte[] csv(JdbcQueryExecutor.QueryResult result) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        appendCsvRow(csv, result.columns());
        result.rows().forEach(row -> appendCsvRow(csv, values(result.columns(), row)));
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void appendCsvRow(StringBuilder csv, List<?> values) {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) csv.append(',');
            String value = values.get(index) == null ? "" : values.get(index).toString();
            csv.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        csv.append("\r\n");
    }

    private byte[] xlsx(JdbcQueryExecutor.QueryResult result) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("查询结果");
            writeRow(sheet.createRow(0), result.columns());
            for (int index = 0; index < result.rows().size(); index++) {
                writeRow(sheet.createRow(index + 1), values(result.columns(), result.rows().get(index)));
            }
            workbook.write(output);
            workbook.dispose();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new BusinessException(500, "XLSX 导出失败");
        }
    }

    private void writeRow(Row row, List<?> values) {
        for (int index = 0; index < values.size(); index++) {
            Object value = values.get(index);
            row.createCell(index).setCellValue(value == null ? "" : value.toString());
        }
    }

    private List<Object> values(List<String> columns, Map<String, Object> row) {
        return columns.stream().map(row::get).toList();
    }

    private MinioClient client() {
        return MinioClient.builder().endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey()).build();
    }

    private String normalize(String format) {
        String normalized = format == null ? "" : format.toUpperCase(Locale.ROOT);
        if (!List.of("CSV", "XLSX").contains(normalized)) {
            throw new BusinessException("导出格式仅支持 CSV 或 XLSX");
        }
        return normalized;
    }

    @PreDestroy
    public void close() {
        executor.close();
    }

    /**
     * 可流式返回的导出文件描述。
     */
    public record Download(java.io.InputStream stream, String filename, String format) {
    }
}
