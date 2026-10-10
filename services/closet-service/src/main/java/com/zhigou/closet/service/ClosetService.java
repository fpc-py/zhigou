package com.zhigou.closet.service;

import com.zhigou.closet.entity.ClosetItem;
import com.zhigou.closet.entity.HomeAsset;
import com.zhigou.closet.entity.OutfitPlan;

import java.util.List;
import java.util.Map;

public interface ClosetService {
    List<ClosetItem> myItems(Long userId, String category, String season);
    ClosetItem addItem(Long userId, String name, String category, String season, String color, String imageUrl, String tags);
    ClosetItem wear(Long userId, Long id);
    ClosetItem removeItem(Long userId, Long id);
    Map<String, Object> recommendOutfit(Long userId, String occasion);
    List<HomeAsset> myAssets(Long userId, String category);
    HomeAsset addAsset(Long userId, String name, String category, Integer quantity, String unit, String expireAt);
    List<HomeAsset> replenishList(Long userId);
}
