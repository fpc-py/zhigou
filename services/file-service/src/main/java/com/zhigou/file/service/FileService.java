package com.zhigou.file.service;

import com.zhigou.file.dto.FileDownload;
import com.zhigou.file.dto.UploadResponse;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    UploadResponse upload(Long userId, MultipartFile file);

    /**
     * 上传（增强版）
     * @param compress   是否压缩（JPEG 质量压缩；PNG 无损不重编码）
     * @param convertTo  目标格式 jpeg/png（null=保持原格式；webp/gif 输入不支持转换）
     * @param bizType    业务分类 product/aftersale/chat/other
     * @param maxWidth   目标最大宽（null=配置默认 1280；只缩小不放大）
     * @param maxHeight  目标最大高
     */
    UploadResponse upload(Long userId, MultipartFile file,
                          boolean compress, String convertTo, String bizType,
                          Integer maxWidth, Integer maxHeight);

    String getSignedUrl(Long userId, String fileId);

    /**
     * 私有读（A7）：鉴权 + 归属校验后从 MinIO 读取文件字节（服务端流式），
     * 返回 内容字节/Content-Type/原始文件名/大小。
     */
    FileDownload download(Long userId, String fileId);

    void delete(Long userId, String fileId);
}