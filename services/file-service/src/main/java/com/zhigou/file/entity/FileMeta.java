package com.zhigou.file.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("file_meta")
public class FileMeta {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String fileId;

    private Long userId;

    private String objectKey;

    private String originalName;

    private Long size;

    private String mimeType;

    /** 业务分类：product / aftersale / chat / other（统一接入口径，便于审计与清理） */
    private String bizType;

    /** 处理后图片宽高（原样存储时为原始值，非图片为 null） */
    private Integer width;

    private Integer height;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;
}