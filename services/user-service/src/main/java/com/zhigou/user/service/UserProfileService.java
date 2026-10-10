package com.zhigou.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhigou.user.dto.ProductTrackItem;
import com.zhigou.user.dto.ProductTrackRequest;
import com.zhigou.user.dto.UserInsightResponse;

import java.util.List;

/**
 * 用户画像底座：收藏 + 浏览历史 + 画像洞察
 */
public interface UserProfileService {

    /**
     * 收藏（幂等 upsert：已收藏则刷新商品快照）
     */
    ProductTrackItem addFavorite(Long userId, ProductTrackRequest request);

    /**
     * 取消收藏（逻辑删除）
     */
    void removeFavorite(Long userId, Long spuId);

    /**
     * 我的收藏（分页，时间倒序）
     */
    Page<ProductTrackItem> listFavorites(Long userId, int page, int size);

    /**
     * 已收藏 SPU ID 集合（详情页判断用）
     * 返回 String 列表：SPU ID 为 19 位 Snowflake，超出 JS Number 安全整数（2^53），
     * 序列化为数字会精度丢失（201→300），故统一以字符串传输（与 ProductTrackItem.spuId ToStringSerializer 口径一致）。
     */
    List<String> favoriteIds(Long userId);

    /**
     * 记录浏览（同 SPU 聚合：次数 +1、最近浏览时间刷新）
     */
    ProductTrackItem recordBrowse(Long userId, ProductTrackRequest request);

    /**
     * 最近浏览（时间倒序，limit 钳制 1-100）
     */
    List<ProductTrackItem> recentBrowse(Long userId, int limit);

    /**
     * 用户画像洞察（实时聚合收藏 + 浏览 + 资料，供 AI Agent 调用）
     */
    UserInsightResponse insight(Long userId);
}
