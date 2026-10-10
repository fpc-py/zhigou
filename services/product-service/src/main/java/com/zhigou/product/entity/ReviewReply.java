package com.zhigou.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 商家评论回复（P2 3.3 AI 客服与营销一期）。reviewId 为 Snowflake（19 位），对外转字符串（CLAUDE.md 红线）。 */
@Data
@TableName("review_reply")
public class ReviewReply {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 被回复的评价 ID（Snowflake） */
    private Long reviewId;
    /** 回复商家（演示：平台聚合商家） */
    private Long merchantId;
    /** 商家回复正文 */
    private String replyContent;
    /** REPLIED=已回复 */
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic private Integer deleted;
}
