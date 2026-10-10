package com.zhigou.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户收藏（商品信息冗余快照，展示免联表）
 */
@Data
@TableName("user_favorite")
public class UserFavorite {

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

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;
}
