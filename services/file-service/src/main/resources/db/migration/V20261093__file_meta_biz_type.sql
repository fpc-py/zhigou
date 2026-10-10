-- P0 文件服务增强：file_meta 增加业务分类与图片尺寸元数据
ALTER TABLE `file_meta`
    ADD COLUMN `biz_type` VARCHAR(32) NOT NULL DEFAULT 'other' COMMENT '业务分类：product/aftersale/chat/other' AFTER `mime_type`,
    ADD COLUMN `width` INT NULL COMMENT '处理后图片宽（原样直存时为原始宽）' AFTER `biz_type`,
    ADD COLUMN `height` INT NULL COMMENT '处理后图片高（原样直存时为原始高）' AFTER `width`;
