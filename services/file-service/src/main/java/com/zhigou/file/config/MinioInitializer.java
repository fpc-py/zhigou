package com.zhigou.file.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MinioInitializer {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    /**
     * A7 私有读：bucket 保持 MinIO 默认私有（不设公开读策略）。
     * 文件访问一律走 file-service 鉴权：
     *   1) GET /file/{fileId}/content —— JWT/X-User-Id 鉴权 + 归属校验（403），服务端流式私有读
     *   2) GET /file/{fileId}/url    —— 短时效（默认 1h）MinIO 预签名 URL，仅作图片直链分发
     * 匿名访问 bucket 直链 → MinIO 返回 403（无公开策略）。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(minioConfig.getBucket()).build());
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(minioConfig.getBucket()).build());
                log.info("Bucket 已创建: {}", minioConfig.getBucket());
            } else {
                log.info("Bucket 已存在: {}", minioConfig.getBucket());
            }
        } catch (Exception e) {
            log.error("MinIO bucket 初始化失败", e);
            throw new RuntimeException("MinIO bucket 初始化失败", e);
        }
    }
}
