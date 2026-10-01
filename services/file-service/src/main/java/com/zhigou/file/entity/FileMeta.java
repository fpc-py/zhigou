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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;
}