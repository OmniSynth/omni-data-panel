package com.omni.panel.export;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 异步导出使用的 MinIO 配置。
 * <p>
 * 各字段分别由 {@code MINIO_ENDPOINT}、{@code MINIO_ACCESS_KEY}、
 * {@code MINIO_SECRET_KEY} 和 {@code MINIO_BUCKET} 提供，桶名默认
 * {@code omni-exports}。端点或凭据缺失时异步导出与下载能力不可用，
 * 同步导出不受影响。
 *
 * @param endpoint MinIO 服务端点
 * @param accessKey 访问密钥
 * @param secretKey 私有密钥
 * @param bucket 导出文件存储桶
 */
@ConfigurationProperties("omni.minio")
public record MinioProperties(String endpoint, String accessKey, String secretKey, String bucket) {
    /**
     * 判断异步导出所需的 MinIO 端点与凭据是否完整。
     *
     * @return 配置完整时返回 {@code true}
     */
    public boolean configured() {
        return endpoint != null && !endpoint.isBlank()
            && accessKey != null && !accessKey.isBlank()
            && secretKey != null && !secretKey.isBlank();
    }
}
