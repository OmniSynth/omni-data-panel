package com.omni.panel.export;

import com.omni.panel.auth.AuthenticatedUser;
import com.omni.panel.common.BusinessException;
import com.omni.panel.query.JdbcQueryExecutor;
import com.omni.panel.query.QueryService;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.GetObjectArgs;
import jakarta.annotation.PreDestroy;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 将成功查询结果导出为 CSV 或 XLSX，支持同步内存生成和基于 MinIO 的异步任务。
 */
@Service
public class ExportService {
    private final QueryService queryService;
    private final ExportTaskMapper mapper;
    private final MinioProperties properties;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ExportService(QueryService queryService, ExportTaskMapper mapper, MinioProperties properties) {
        this.queryService = queryService;
        this.mapper = mapper;
        this.properties = properties;
    }

    /**
     * 在当前请求内生成完整导出内容。
     * 仅接受当前用户可访问且已成功并保留结果的查询；格式非法或生成失败时抛出业务异常。
     *
     * @param queryId 查询标识
     * @param format 导出格式，支持 CSV 或 XLSX，不区分大小写
     * @return 完整导出文件字节
     */
    public byte[] synchronous(String queryId, String format) {
        var snapshot = queryService.get(queryId);
        if (!"SUCCEEDED".equals(snapshot.status()) || snapshot.result() == null) {
            throw new BusinessException("仅成功查询可以导出");
        }
        return generate(snapshot.result(), normalize(format));
    }

    /**
     * 创建异步导出任务并交由虚拟线程生成文件、上传 MinIO。
     * 调用时会校验 MinIO 配置及查询成功状态，任务所有者固定为当前用户；提交后立即返回，
     * 后台失败会将任务标记为 {@code FAILED} 并记录错误信息。
     *
     * @param queryId 查询标识
     * @param format 导出格式，支持 CSV 或 XLSX，不区分大小写
     * @return 新建的导出任务标识
     */
    public String asynchronous(String queryId, String format) {
        if (!properties.configured()) {
            throw new BusinessException(503, "MinIO 未配置，无法执行异步导出");
        }
        var snapshot = queryService.get(queryId);
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
        executor.submit(() -> upload(task, snapshot.result()));
        return task.getId();
    }

    /**
     * 查询导出任务，并执行所有者边界校验。
     * 仅任务所有者或管理员可访问，不存在与越权分别以 404、403 业务异常失败。
     *
     * @param taskId 导出任务标识
     * @return 可访问的导出任务
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
     * 读取前复用任务所有者校验，并拒绝未完成任务或未配置 MinIO 的请求；返回流的关闭责任由响应消费方承担。
     *
     * @param taskId 导出任务标识
     * @return 包含对象流、下载文件名和格式的下载描述
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
     * 在后台推进任务状态，生成文件并上传 MinIO；任何异常都收敛为失败状态并持久化错误信息。
     *
     * @param task 异步导出任务
     * @param result 提交任务时取得的查询结果
     */
    private void upload(ExportTaskEntity task, JdbcQueryExecutor.QueryResult result) {
        task.setStatus("RUNNING");
        mapper.updateById(task);
        try {
            byte[] content = generate(result, task.getFormat());
            String objectName = "exports/" + task.getId() + "." + task.getFormat().toLowerCase(Locale.ROOT);
            MinioClient client = client();
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
            }
            client.putObject(PutObjectArgs.builder().bucket(properties.bucket()).object(objectName)
                .stream(new ByteArrayInputStream(content), content.length, -1)
                .contentType("CSV".equals(task.getFormat()) ? "text/csv" :
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").build());
            task.setObjectName(objectName);
            task.setStatus("SUCCEEDED");
        } catch (Exception exception) {
            task.setStatus("FAILED");
            task.setErrorMessage("MinIO 异步导出失败：" + exception.getMessage());
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

    /**
     * 关闭异步导出执行器，并等待已提交任务按执行器语义结束。
     */
    @PreDestroy
    public void close() {
        executor.close();
    }

    /**
     * 可流式返回的导出文件描述。
     *
     * @param stream MinIO 对象输入流，由消费方关闭
     * @param filename 建议下载文件名
     * @param format 导出格式
     */
    public record Download(java.io.InputStream stream, String filename, String format) {}
}
