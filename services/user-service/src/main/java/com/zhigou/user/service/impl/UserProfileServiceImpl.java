package com.zhigou.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhigou.user.dto.ProductTrackItem;
import com.zhigou.user.dto.ProductTrackRequest;
import com.zhigou.user.dto.UserInsightResponse;
import com.zhigou.user.entity.UserBrowseHistory;
import com.zhigou.user.entity.UserFavorite;
import com.zhigou.user.mapper.UserBrowseHistoryMapper;
import com.zhigou.user.mapper.UserFavoriteMapper;
import com.zhigou.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户画像底座：收藏 + 浏览历史 + 画像洞察（实时聚合口径）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final UserFavoriteMapper favoriteMapper;
    private final UserBrowseHistoryMapper browseMapper;

    /** 偏好品类词表（演示口径，与 AI tools.py _CATEGORY_WORDS 对齐的轻量版） */
    private static final Map<String, String> CATEGORY_WORDS = Map.ofEntries(
            Map.entry("手机", "手机"), Map.entry("iphone", "手机"), Map.entry("phone", "手机"),
            Map.entry("电脑", "笔记本电脑"), Map.entry("笔记本", "笔记本电脑"), Map.entry("laptop", "笔记本电脑"),
            Map.entry("耳机", "耳机"), Map.entry("蓝牙耳机", "耳机"), Map.entry("降噪", "耳机"),
            Map.entry("鞋", "运动鞋"), Map.entry("跑鞋", "运动鞋"),
            Map.entry("外套", "外套"), Map.entry("羽绒服", "外套"), Map.entry("大衣", "外套"),
            Map.entry("衬衫", "服装"), Map.entry("T恤", "服装"), Map.entry("衣服", "服装"),
            Map.entry("手表", "手表"), Map.entry("智能手表", "手表"),
            Map.entry("音箱", "音箱"), Map.entry("音响", "音箱"),
            Map.entry("充电宝", "充电宝"), Map.entry("数据线", "配件"), Map.entry("手机壳", "配件"),
            Map.entry("平板", "平板电脑"), Map.entry("ipad", "平板电脑"),
            Map.entry("相机", "相机"), Map.entry("摄像头", "相机"),
            Map.entry("冰箱", "家电"), Map.entry("洗衣机", "家电"), Map.entry("空调", "家电"),
            Map.entry("扫地机", "扫地机器人"), Map.entry("吸尘器", "吸尘器"),
            Map.entry("护肤", "护肤品"), Map.entry("香水", "护肤品"),
            Map.entry("玩具", "玩具"), Map.entry("乐高", "玩具"),
            Map.entry("背包", "箱包"), Map.entry("行李箱", "箱包"),
            Map.entry("水杯", "水杯"), Map.entry("保温杯", "水杯")
    );

    @Override
    public ProductTrackItem addFavorite(Long userId, ProductTrackRequest request) {
        UserFavorite existing = favoriteMapper.selectOne(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getSpuId, request.getSpuId())
        );
        if (existing != null) {
            // 幂等：已收藏仅刷新商品快照
            existing.setSkuId(request.getSkuId());
            existing.setSpuName(request.getSpuName());
            existing.setPrice(request.getPrice());
            existing.setImageUrl(request.getImageUrl());
            favoriteMapper.updateById(existing);
            log.info("收藏已存在，刷新快照: userId={}, spuId={}", userId, request.getSpuId());
            return toFavoriteItem(existing);
        }
        UserFavorite fav = new UserFavorite();
        fav.setUserId(userId);
        fav.setSpuId(request.getSpuId());
        fav.setSkuId(request.getSkuId());
        fav.setSpuName(request.getSpuName());
        fav.setPrice(request.getPrice());
        fav.setImageUrl(request.getImageUrl());
        favoriteMapper.insert(fav);
        log.info("新增收藏: userId={}, spuId={}", userId, request.getSpuId());
        return toFavoriteItem(fav);
    }

    @Override
    public void removeFavorite(Long userId, Long spuId) {
        UserFavorite existing = favoriteMapper.selectOne(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getSpuId, spuId)
        );
        if (existing != null) {
            favoriteMapper.deleteById(existing.getId());
            log.info("取消收藏: userId={}, spuId={}", userId, spuId);
        }
    }

    @Override
    public Page<ProductTrackItem> listFavorites(Long userId, int page, int size) {
        LambdaQueryWrapper<UserFavorite> qw = new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .orderByDesc(UserFavorite::getCreateTime);
        Page<UserFavorite> result = favoriteMapper.selectPage(new Page<>(page, size), qw);
        Page<ProductTrackItem> out = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        out.setRecords(result.getRecords().stream().map(this::toFavoriteItem).collect(Collectors.toList()));
        return out;
    }

    @Override
    public List<String> favoriteIds(Long userId) {
        return favoriteMapper.selectList(
                        new LambdaQueryWrapper<UserFavorite>()
                                .eq(UserFavorite::getUserId, userId)
                                .orderByDesc(UserFavorite::getCreateTime)
                ).stream().map(f -> String.valueOf(f.getSpuId())).collect(Collectors.toList());
    }

    @Override
    public ProductTrackItem recordBrowse(Long userId, ProductTrackRequest request) {
        UserBrowseHistory existing = browseMapper.selectOne(
                new LambdaQueryWrapper<UserBrowseHistory>()
                        .eq(UserBrowseHistory::getUserId, userId)
                        .eq(UserBrowseHistory::getSpuId, request.getSpuId())
        );
        if (existing != null) {
            existing.setSkuId(request.getSkuId());
            existing.setSpuName(request.getSpuName());
            existing.setPrice(request.getPrice());
            existing.setImageUrl(request.getImageUrl());
            existing.setBrowseCount(existing.getBrowseCount() + 1);
            existing.setLastBrowseTime(LocalDateTime.now());
            browseMapper.updateById(existing);
            log.info("浏览记录刷新: userId={}, spuId={}, count={}", userId, request.getSpuId(), existing.getBrowseCount());
            return toBrowseItem(existing);
        }
        try {
            UserBrowseHistory history = new UserBrowseHistory();
            history.setUserId(userId);
            history.setSpuId(request.getSpuId());
            history.setSkuId(request.getSkuId());
            history.setSpuName(request.getSpuName());
            history.setPrice(request.getPrice());
            history.setImageUrl(request.getImageUrl());
            history.setBrowseCount(1);
            history.setLastBrowseTime(LocalDateTime.now());
            browseMapper.insert(history);
            log.info("新增浏览记录: userId={}, spuId={}", userId, request.getSpuId());
            return toBrowseItem(history);
        } catch (DuplicateKeyException e) {
            // 并发下 SPU 首次浏览撞唯一键：重查后刷新
            UserBrowseHistory dup = browseMapper.selectOne(
                    new LambdaQueryWrapper<UserBrowseHistory>()
                            .eq(UserBrowseHistory::getUserId, userId)
                            .eq(UserBrowseHistory::getSpuId, request.getSpuId())
            );
            if (dup != null) {
                dup.setBrowseCount(dup.getBrowseCount() + 1);
                dup.setLastBrowseTime(LocalDateTime.now());
                dup.setSpuName(request.getSpuName());
                dup.setPrice(request.getPrice());
                dup.setImageUrl(request.getImageUrl());
                browseMapper.updateById(dup);
                return toBrowseItem(dup);
            }
            return toBrowseItem(request);
        }
    }

    @Override
    public List<ProductTrackItem> recentBrowse(Long userId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        List<UserBrowseHistory> records = browseMapper.selectList(
                new LambdaQueryWrapper<UserBrowseHistory>()
                        .eq(UserBrowseHistory::getUserId, userId)
                        .orderByDesc(UserBrowseHistory::getLastBrowseTime)
                        .last("LIMIT " + safeLimit)
        );
        return records.stream().map(this::toBrowseItem).collect(Collectors.toList());
    }

    @Override
    public UserInsightResponse insight(Long userId) {
        List<UserFavorite> favorites = favoriteMapper.selectList(
                new LambdaQueryWrapper<UserFavorite>().eq(UserFavorite::getUserId, userId)
        );
        List<UserBrowseHistory> browses = browseMapper.selectList(
                new LambdaQueryWrapper<UserBrowseHistory>().eq(UserBrowseHistory::getUserId, userId)
        );

        // 品类偏好：收藏 + 浏览按词表归类聚合（演示口径）
        Map<String, Integer> categoryCount = new LinkedHashMap<>();
        for (UserFavorite f : favorites) {
            String cat = matchCategory(f.getSpuName());
            if (cat != null) categoryCount.merge(cat, 1, Integer::sum);
        }
        for (UserBrowseHistory b : browses) {
            String cat = matchCategory(b.getSpuName());
            if (cat != null) categoryCount.merge(cat, b.getBrowseCount(), Integer::sum);
        }
        List<String> topCategories = categoryCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // 价位带：收藏 + 浏览价格（分）众数分布
        List<Long> prices = new ArrayList<>();
        favorites.forEach(f -> { if (f.getPrice() != null) prices.add(f.getPrice()); });
        browses.forEach(b -> { if (b.getPrice() != null) prices.add(b.getPrice()); });
        String priceBand = dominantBand(prices);

        // 最近浏览 Top5
        List<UserBrowseHistory> recent = browseMapper.selectList(
                new LambdaQueryWrapper<UserBrowseHistory>()
                        .eq(UserBrowseHistory::getUserId, userId)
                        .orderByDesc(UserBrowseHistory::getLastBrowseTime)
                        .last("LIMIT 5")
        );

        long browseTotal = browses.stream().mapToLong(b -> b.getBrowseCount()).sum();
        return UserInsightResponse.builder()
                .favoriteCount((long) favorites.size())
                .browseCount((long) browses.size())
                .browseTotal(browseTotal)
                .topCategories(topCategories)
                .priceBand(priceBand)
                .recentBrowse(recent.stream().map(this::toBrowseItem).collect(Collectors.toList()))
                .note("画像为实时聚合口径（收藏+浏览+资料），数据量大后可落表离线化")
                .build();
    }

    // ── 私有工具 ──

    private String matchCategory(String name) {
        if (name == null || name.isEmpty()) return null;
        String lower = name.toLowerCase();
        String hit = CATEGORY_WORDS.entrySet().stream()
                .filter(e -> lower.contains(e.getKey().toLowerCase()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        return hit;
    }

    private String dominantBand(List<Long> prices) {
        if (prices.isEmpty()) return "暂无价格数据";
        Map<String, Integer> buckets = new LinkedHashMap<>();
        for (Long fen : prices) {
            buckets.merge(bucketOf(fen), 1, Integer::sum);
        }
        return buckets.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("暂无价格数据");
    }

    private String bucketOf(long fen) {
        double yuan = fen / 100.0;
        if (yuan < 100) return "百元档";
        if (yuan < 500) return "100-500档";
        if (yuan < 2000) return "500-2000档";
        if (yuan < 5000) return "2000-5000档";
        return "5000元以上";
    }

    private ProductTrackItem toFavoriteItem(UserFavorite f) {
        return ProductTrackItem.builder()
                .id(f.getId())
                .spuId(f.getSpuId())
                .skuId(f.getSkuId())
                .spuName(f.getSpuName())
                .price(f.getPrice())
                .imageUrl(f.getImageUrl())
                .browseCount(1)
                .createTime(f.getCreateTime())
                .lastBrowseTime(f.getUpdateTime())
                .build();
    }

    private ProductTrackItem toBrowseItem(UserBrowseHistory b) {
        return ProductTrackItem.builder()
                .id(b.getId())
                .spuId(b.getSpuId())
                .skuId(b.getSkuId())
                .spuName(b.getSpuName())
                .price(b.getPrice())
                .imageUrl(b.getImageUrl())
                .browseCount(b.getBrowseCount())
                .createTime(b.getCreateTime())
                .lastBrowseTime(b.getLastBrowseTime())
                .build();
    }

    private ProductTrackItem toBrowseItem(ProductTrackRequest request) {
        return ProductTrackItem.builder()
                .spuId(request.getSpuId())
                .skuId(request.getSkuId())
                .spuName(request.getSpuName())
                .price(request.getPrice())
                .imageUrl(request.getImageUrl())
                .browseCount(1)
                .lastBrowseTime(LocalDateTime.now())
                .build();
    }
}
