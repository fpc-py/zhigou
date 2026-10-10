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

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
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

    @Value("${file.image.max-width:1280}")
    private int imageMaxWidth;

    @Value("${file.image.max-height:1280}")
    private int imageMaxHeight;

    @Value("${file.image.quality:0.8}")
    private float imageQuality;

    /** 像素上限（宽×高），超限拒绝，防超大图内存 DoS */
    @Value("${file.image.max-pixels:25000000}")
    private long imageMaxPixels;

    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    /** ImageIO 原生可解码+可编码的图片类型（可做压缩/缩放/转换） */
    private static final Set<String> PROCESSABLE_MIME = Set.of("image/jpeg", "image/png");

    /** 允许的目标转换格式（对应 mime 后缀） */
    private static final Set<String> CONVERT_TARGETS = Set.of("jpeg", "png");

    @Override
    public UploadResponse upload(Long userId, MultipartFile file) {
        return upload(userId, file, false, null, "other", null, null);
    }

    @Override
    public UploadResponse upload(Long userId, MultipartFile file,
                                 boolean compress, String convertTo, String bizType,
                                 Integer maxWidth, Integer maxHeight) {
        // 1. MIME 校验
        String mime = file.getContentType();
        if (mime == null || !ALLOWED_MIME.contains(mime)) {
            throw new BizException(40002, "FILE_TYPE_NOT_ALLOWED");
        }

        // 2. Magic bytes 校验
        validateMagicBytes(file, mime);

        // 3. 图片处理（压缩 / 缩放 / 格式转换）；webp/gif 无解码器时原样直存
        byte[] raw = readAllBytes(file);
        ProcessResult processed = PROCESSABLE_MIME.contains(mime)
                ? processImage(raw, mime, compress, convertTo, maxWidth, maxHeight)
                : ProcessResult.passthrough(raw, mime, null, null);

        byte[] toStore = processed.bytes;
        String storeMime = processed.mime;
        String ext = getExtension(storeMime);

        // 4. 构建 object key
        String fileId = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "/" + userId + "/" + IdUtil.fastSimpleUUID() + ext;
        String objectKey = fileId;

        // 5. 上传到 MinIO
        long start = System.currentTimeMillis();
        try (InputStream is = new ByteArrayInputStream(toStore)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(is, toStore.length, -1)
                    .contentType(storeMime)
                    .build());
        } catch (Exception e) {
            log.error("MinIO 上传失败: userId={}", userId, e);
            throw new BizException(500, "文件上传失败");
        }

        long elapsed = System.currentTimeMillis() - start;

        // 6. 生成签名 URL
        String url = generatePresignedUrl(objectKey);

        // 7. 写 file_meta
        FileMeta meta = new FileMeta();
        meta.setFileId(fileId);
        meta.setUserId(userId);
        meta.setObjectKey(objectKey);
        meta.setOriginalName(file.getOriginalFilename());
        meta.setSize((long) toStore.length);
        meta.setMimeType(storeMime);
        meta.setBizType(bizType == null || bizType.isBlank() ? "other" : bizType);
        meta.setWidth(processed.width);
        meta.setHeight(processed.height);
        fileMetaMapper.insert(meta);

        log.info("文件上传成功: fileId={}, size={}->{}, mime={}->{}, processed={}, bizType={}, elapsed={}ms",
                fileId, raw.length, toStore.length, mime, storeMime, processed.processed, meta.getBizType(), elapsed);

        return UploadResponse.builder()
                .fileId(fileId)
                .url(url)
                .size((long) toStore.length)
                .mimeType(storeMime)
                .originalName(file.getOriginalFilename())
                .width(processed.width)
                .height(processed.height)
                .processed(processed.processed)
                .originalSize((long) raw.length)
                .originalWidth(processed.originalWidth)
                .originalHeight(processed.originalHeight)
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

    /**
     * 图片处理管线：尺寸探测 → 像素上限防 DoS → 等比缩放（只缩小不放大）→ 压缩/格式转换。
     * ImageIO 原生支持 jpeg/png 解码与编码；webp/gif 输入无解码器，交由调用方直存。
     */
    private ProcessResult processImage(byte[] bytes, String sourceMime,
                                       boolean compress, String convertTo,
                                       Integer maxWidth, Integer maxHeight) {
        int targetMaxW = maxWidth != null && maxWidth > 0 ? maxWidth : imageMaxWidth;
        int targetMaxH = maxHeight != null && maxHeight > 0 ? maxHeight : imageMaxHeight;
        String targetFormat = normalizeTarget(convertTo);

        BufferedImage image;
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            image = ImageIO.read(is);
        } catch (Exception e) {
            log.warn("图片解码失败，原样直存: mime={}, err={}", sourceMime, e.getMessage());
            return ProcessResult.passthrough(bytes, sourceMime, null, null);
        }
        if (image == null) {
            log.warn("图片解码返回 null（可能为 webp/gif），原样直存: mime={}", sourceMime);
            return ProcessResult.passthrough(bytes, sourceMime, null, null);
        }

        int origW = image.getWidth();
        int origH = image.getHeight();
        long pixels = (long) origW * origH;
        if (pixels > imageMaxPixels) {
            throw new BizException(40003, "FILE_IMAGE_TOO_LARGE");
        }

        // 等比缩放（只缩小，不放大）
        BufferedImage scaled = image;
        if (origW > targetMaxW || origH > targetMaxH) {
            double scale = Math.min((double) targetMaxW / origW, (double) targetMaxH / origH);
            int newW = Math.max(1, (int) Math.round(origW * scale));
            int newH = Math.max(1, (int) Math.round(origH * scale));
            scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
            var g = scaled.createGraphics();
            try {
                g.drawImage(image, 0, 0, newW, newH, null);
            } finally {
                g.dispose();
            }
        }

        String targetMime = sourceMime;
        if (targetFormat != null) {
            targetMime = switch (targetFormat) {
                case "jpeg" -> "image/jpeg";
                case "png" -> "image/png";
                default -> sourceMime;
            };
        }

        boolean needConvert = targetFormat != null && !mimeOf(targetFormat).equals(sourceMime);

        // 无任何处理必要 → 原样直存
        if (scaled == image && !needConvert && !compress) {
            return ProcessResult.of(bytes, sourceMime, origW, origH, false, origW, origH);
        }
        // 仅压缩语义但已是 PNG（无损），不重编码 PNG，避免体积膨胀
        if (!needConvert && scaled == image && compress && sourceMime.equals("image/png")) {
            return ProcessResult.of(bytes, sourceMime, origW, origH, false, origW, origH);
        }

        byte[] out;
        try {
            out = encode(scaled, targetMime);
        } catch (Exception e) {
            log.warn("图片编码失败，原样直存: mime={}, err={}", targetMime, e.getMessage());
            return ProcessResult.of(bytes, sourceMime, origW, origH, false, origW, origH);
        }

        return ProcessResult.of(out, targetMime, scaled.getWidth(), scaled.getHeight(), true, origW, origH);
    }

    private byte[] encode(BufferedImage image, String targetMime) throws Exception {
        String format = targetMime.equals("image/png") ? "png" : "jpeg";
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) {
            throw new IllegalStateException("no ImageWriter for " + format);
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ImageOutputStream ios = ImageIO.createImageOutputStream(bos)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (format.equals("jpeg") && param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(imageQuality);
            }
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
            ios.flush();
            return bos.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private String normalizeTarget(String convertTo) {
        if (convertTo == null || convertTo.isBlank()) {
            return null;
        }
        String t = convertTo.toLowerCase();
        if ("jpg".equals(t)) {
            t = "jpeg";
        }
        return CONVERT_TARGETS.contains(t) ? t : null;
    }

    private String mimeOf(String format) {
        return format.equals("png") ? "image/png" : "image/jpeg";
    }

    private byte[] readAllBytes(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            return is.readAllBytes();
        } catch (Exception e) {
            log.error("读取上传文件失败", e);
            throw new BizException(500, "文件上传失败");
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

    /** 图片处理结果（bytes + 目标 mime + 尺寸 + 是否处理 + 原始尺寸） */
    private record ProcessResult(byte[] bytes, String mime, Integer width, Integer height,
                                 boolean processed, Integer originalWidth, Integer originalHeight) {
        static ProcessResult passthrough(byte[] bytes, String mime, Integer w, Integer h) {
            return new ProcessResult(bytes, mime, w, h, false, w, h);
        }

        static ProcessResult of(byte[] bytes, String mime, Integer w, Integer h,
                                boolean processed, Integer ow, Integer oh) {
            return new ProcessResult(bytes, mime, w, h, processed, ow, oh);
        }
    }
}
