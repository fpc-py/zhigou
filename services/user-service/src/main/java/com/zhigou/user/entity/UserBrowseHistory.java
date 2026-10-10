package com.zhigou.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户浏览历史（同 SPU 聚合：次数累加、最近浏览时间刷新）
 */
@Data
@TableName("user_browse_history")
public class UserBrowseHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分片键 */
    private Long userId;

    /** 商品 SPU ID（Snowflake，序列化需转字符串） */
    private Long spuId;

    private Long skuId;

    /** 商品名快照 */
    private String spuName;

    /** 价格快照（分） */
    private Long price;

    private String imageUrl;

    /** 累计浏览次数 */
    private Integer browseCount;

    /** 最近浏览时间 */
    private LocalDateTime lastBrowseTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;
}
