package com.zhigou.file.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponse {

    private String fileId;
    private String url;
    private Long size;
    private String mimeType;
    private String originalName;

    /** 处理后图片尺寸（非图片或原样直存时为 null） */
    private Integer width;
    private Integer height;

    /** 是否发生了压缩/缩放/格式转换 */
    private Boolean processed;

    /** 处理前字节数（processed=false 时等于 size） */
    private Long originalSize;
    private Integer originalWidth;
    private Integer originalHeight;
}
