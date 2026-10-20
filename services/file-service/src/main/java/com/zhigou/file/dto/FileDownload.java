package com.zhigou.file.dto;

/**
 * 私有读结果（A7）：文件字节 + Content-Type + 原始文件名 + 大小。
 */
public record FileDownload(byte[] bytes, String contentType, String fileName, Long size) {
}
