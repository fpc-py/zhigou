package com.zhigou.closet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhigou.common.BizException;
import com.zhigou.closet.entity.ClosetItem;
import com.zhigou.closet.entity.HomeAsset;
import com.zhigou.closet.entity.OutfitPlan;
import com.zhigou.closet.mapper.ClosetItemMapper;
import com.zhigou.closet.mapper.HomeAssetMapper;
import com.zhigou.closet.mapper.OutfitPlanMapper;
import com.zhigou.closet.service.ClosetService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClosetServiceImpl implements ClosetService {

    private final ClosetItemMapper itemMapper;
    private final OutfitPlanMapper planMapper;
    private final HomeAssetMapper assetMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 场合 → 需要的基础件类别（规则引擎，非 AI） */
    private static final Map<String, List<String>> OCCASION_NEEDS = new HashMap<>();
    static {
        OCCASION_NEEDS.put("通勤", List.of("上装", "下装", "外套", "鞋履"));
        OCCASION_NEEDS.put("休闲", List.of("上装", "下装", "鞋履"));
        OCCASION_NEEDS.put("运动", List.of("上装", "下装", "鞋履"));
        OCCASION_NEEDS.put("约会", List.of("上装", "下装", "外套", "鞋履", "配饰"));
    }

    @Override
    public List<ClosetItem> myItems(Long userId, String category, String season) {
        if (userId == null) throw new BizException(40001, "请先登录");
        LambdaQueryWrapper<ClosetItem> q = new LambdaQueryWrapper<ClosetItem>()
                .eq(ClosetItem::getUserId, userId)
                .eq(ClosetItem::getStatus, 1);
        if (category != null && !category.isBlank()) q.eq(ClosetItem::getCategory, category);
        if (season != null && !season.isBlank()) q.eq(ClosetItem::getSeason, season);
        q.orderByDesc(ClosetItem::getWearCount);
        return itemMapper.selectList(q);
    }

    @Override
    public ClosetItem addItem(Long userId, String name, String category, String season, String color, String imageUrl, String tags) {
        if (userId == null) throw new BizException(40001, "请先登录");
        if (name == null || name.isBlank()) throw new BizException(40021, "衣物名称不能为空");
        ClosetItem it = new ClosetItem();
        it.setUserId(userId);
        it.setName(name);
        it.setCategory(category == null || category.isBlank() ? "上装" : category);
        it.setSeason(season == null || season.isBlank() ? "四季" : season);
        it.setColor(color);
        it.setImageUrl(imageUrl);
        it.setTags(tags);
        it.setWearCount(0);
        it.setStatus(1);
        it.setCreatedAt(LocalDateTime.now());
        it.setUpdatedAt(LocalDateTime.now());
        itemMapper.insert(it);
        return it;
    }

    @Override
    public ClosetItem wear(Long userId, Long id) {
        ClosetItem it = itemMapper.selectById(id);
        if (it == null || !it.getUserId().equals(userId)) throw new BizException(40022, "衣物不存在");
        it.setWearCount(it.getWearCount() == null ? 1 : it.getWearCount() + 1);
        it.setLastWornAt(LocalDateTime.now());
        it.setUpdatedAt(LocalDateTime.now());
        itemMapper.updateById(it);
        return it;
    }

    @Override
    public ClosetItem removeItem(Long userId, Long id) {
        ClosetItem it = itemMapper.selectById(id);
        if (it == null || !it.getUserId().equals(userId)) throw new BizException(40022, "衣物不存在");
        it.setStatus(0);
        it.setUpdatedAt(LocalDateTime.now());
        itemMapper.updateById(it);
        return it;
    }

    @Override
    public Map<String, Object> recommendOutfit(Long userId, String occasion) {
        if (userId == null) throw new BizException(40001, "请先登录");
        String occ = occasion == null || occasion.isBlank() ? "通勤" : occasion;
        List<ClosetItem> items = myItems(userId, null, null);
        if (items.isEmpty()) throw new BizException(40023, "衣橱暂无衣物，先添加再生成穿搭");
        List<String> needs = OCCASION_NEEDS.getOrDefault(occ, OCCASION_NEEDS.get("通勤"));
        List<ClosetItem> picked = new ArrayList<>();
        for (String need : needs) {
            // 同类别挑穿着次数最少/最近未穿的（轮换公平）
            List<ClosetItem> cand = items.stream()
                    .filter(i -> need.equals(i.getCategory()) && i.getStatus() == 1)
                    .sorted((a, b) -> {
                        int c = Integer.compare(a.getWearCount() == null ? 0 : a.getWearCount(),
                                b.getWearCount() == null ? 0 : b.getWearCount());
                        if (c != 0) return c;
                        if (a.getLastWornAt() == null) return -1;
                        if (b.getLastWornAt() == null) return 1;
                        return a.getLastWornAt().compareTo(b.getLastWornAt());
                    })
                    .collect(Collectors.toList());
            if (!cand.isEmpty()) picked.add(cand.get(0));
        }
        if (picked.isEmpty()) throw new BizException(40023, "衣橱暂无衣物，先添加再生成穿搭");
        Map<String, Object> r = new HashMap<>();
        r.put("occasion", occ);
        r.put("items", picked);
        r.put("note", "规则引擎按场合+穿着次数轮换生成（演示，非 AI 模型）");
        return r;
    }

    @Override
    public List<HomeAsset> myAssets(Long userId, String category) {
        if (userId == null) throw new BizException(40001, "请先登录");
        LambdaQueryWrapper<HomeAsset> q = new LambdaQueryWrapper<HomeAsset>().eq(HomeAsset::getUserId, userId);
        if (category != null && !category.isBlank()) q.eq(HomeAsset::getCategory, category);
        q.orderByAsc(HomeAsset::getExpireAt);
        return assetMapper.selectList(q);
    }

    @Override
    public HomeAsset addAsset(Long userId, String name, String category, Integer quantity, String unit, String expireAt) {
        if (userId == null) throw new BizException(40001, "请先登录");
        if (name == null || name.isBlank()) throw new BizException(40031, "物品名称不能为空");
        HomeAsset a = new HomeAsset();
        a.setUserId(userId);
        a.setName(name);
        a.setCategory(category == null || category.isBlank() ? "日用品" : category);
        a.setQuantity(quantity == null ? 1 : quantity);
        a.setUnit(unit == null || unit.isBlank() ? "件" : unit);
        a.setExpireAt(expireAt == null || expireAt.isBlank() ? null : LocalDate.parse(expireAt.trim()));
        a.setReplenishAlert(0);
        a.setCreatedAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        assetMapper.insert(a);
        return a;
    }

    @Override
    public List<HomeAsset> replenishList(Long userId) {
        List<HomeAsset> all = myAssets(userId, null);
        return all.stream().filter(a -> {
            boolean low = a.getQuantity() != null && a.getQuantity() <= 1;
            boolean expiring = a.getExpireAt() != null && a.getExpireAt().isBefore(LocalDate.now().plusDays(7));
            return low || expiring;
        }).collect(Collectors.toList());
    }
}
