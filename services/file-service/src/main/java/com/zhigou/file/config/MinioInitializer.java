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
     * 公开只读策略：允许匿名 GetObject。
     * 上传接口返回的是对象直链（http://localhost:9000/{bucket}/{key}），
     * 若 bucket 无公开读权限，前端访问图片会返回 403。
     */
    private static final String PUBLIC_READ_POLICY = """
            {
              "Version": "2012-10-17",
              "Statement": [
                {
                  "Effect": "Allow",
                  "Principal": {"AWS": ["*"]},
                  "Action": ["s3:GetObject"],
                  "Resource": ["arn:aws:s3:::%s/*"]
                }
              ]
            }
            """;

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

            minioClient.setBucketPolicy(
                    SetBucketPolicyArgs.builder()
                            .bucket(minioConfig.getBucket())
                            .config(PUBLIC_READ_POLICY.formatted(minioConfig.getBucket()))
                            .build());
            log.info("Bucket 公开只读策略已设置: {}", minioConfig.getBucket());
        } catch (Exception e) {
            log.error("MinIO bucket 初始化失败", e);
            throw new RuntimeException("MinIO bucket 初始化失败", e);
        }
    }
}
