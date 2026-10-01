package com.zhigou.file.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhigou.common.BizException;
import com.zhigou.file.dto.UploadResponse;
import com.zhigou.file.entity.FileMeta;
import com.zhigou.file.mapper.FileMetaMapper;
import com.zhigou.file.service.FileService;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final MinioClient minioClient;
    private final FileMetaMapper fileMetaMapper;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${file.url-expire-seconds:3600}")
    private long urlExpireSeconds;

    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    @Override
    public UploadResponse upload(Long userId, MultipartFile file) {
        // 1. MIME 校验
        String mime = file.getContentType();
        if (mime == null || !ALLOWED_MIME.contains(mime)) {
            throw new BizException(40002, "FILE_TYPE_NOT_ALLOWED");
        }

        // 2. Magic bytes 校验
        validateMagicBytes(file, mime);

        // 3. 构建 object key
        String ext = getExtension(mime);
        String fileId = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "/" + userId + "/" + IdUtil.fastSimpleUUID() + ext;
        String objectKey = fileId;

        // 4. 上传到 MinIO
        long start = System.currentTimeMillis();
        try (InputStream is = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(is, file.getSize(), -1)
                    .contentType(mime)
                    .build());
        } catch (Exception e) {
            log.error("MinIO 上传失败: userId={}", userId, e);
            throw new BizException(500, "文件上传失败");
        }

        long elapsed = System.currentTimeMillis() - start;

        // 5. 生成签名 URL
        String url = generatePresignedUrl(objectKey);

        // 6. 写 file_meta
        FileMeta meta = new FileMeta();
        meta.setFileId(fileId);
        meta.setUserId(userId);
        meta.setObjectKey(objectKey);
        meta.setOriginalName(file.getOriginalFilename());
        meta.setSize(file.getSize());
        meta.setMimeType(mime);
        fileMetaMapper.insert(meta);

        log.info("文件上传成功: fileId={}, size={}, mime={}, elapsed={}ms",
                fileId, file.getSize(), mime, elapsed);

        return UploadResponse.builder()
                .fileId(fileId)
                .url(url)
                .size(file.getSize())
                .mimeType(mime)
                .originalName(file.getOriginalFilename())
                .build();
    }

    @Override
    public String getSignedUrl(Long userId, String fileId) {
        FileMeta meta = requireOwnFile(userId, fileId);
        return generatePresignedUrl(meta.getObjectKey());
    }

    @Override
    public void delete(Long userId, String fileId) {
        FileMeta meta = requireOwnFile(userId, fileId);

        // 删 MinIO
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(meta.getObjectKey())
                    .build());
        } catch (Exception e) {
            log.error("MinIO 删除失败: fileId={}", fileId, e);
        }

        // 逻辑删 file_meta
        fileMetaMapper.deleteById(meta.getId());
        log.info("文件已删除: fileId={}, userId={}", fileId, userId);
    }

    // ==================== Private ====================

    private void validateMagicBytes(MultipartFile file, String mime) {
        try (InputStream is = new BufferedInputStream(file.getInputStream())) {
            byte[] header = new byte[12];
            int read = is.read(header);
            if (read < 4) {
                throw new BizException(40002, "FILE_TYPE_NOT_ALLOWED");
            }

            boolean valid = switch (mime) {
                case "image/jpeg" -> (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF;
                case "image/png"  -> header[0] == (byte) 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47;
                case "image/gif"  -> header[0] == 0x47 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x38;
                case "image/webp" -> header[0] == 0x52 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x46
                        && header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50;
                default -> false;
            };

            if (!valid) {
                throw new BizException(40002, "FILE_TYPE_NOT_ALLOWED");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(40002, "FILE_TYPE_NOT_ALLOWED");
        }
    }

    private String getExtension(String mime) {
        return switch (mime) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".bin";
        };
    }

    private String generatePresignedUrl(String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .method(Method.GET)
                    .expiry((int) urlExpireSeconds, TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            log.error("生成签名 URL 失败: objectKey={}", objectKey, e);
            throw new BizException(500, "生成下载链接失败");
        }
    }

    private FileMeta requireOwnFile(Long userId, String fileId) {
        FileMeta meta = fileMetaMapper.selectOne(
                new LambdaQueryWrapper<FileMeta>()
                        .eq(FileMeta::getFileId, fileId)
                        .eq(FileMeta::getUserId, userId)
        );
        if (meta == null) {
            throw new BizException(403, "无权操作该文件");
        }
        return meta;
    }
}