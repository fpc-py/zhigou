package com.zhigou.closet.controller;

import com.zhigou.common.Result;
import com.zhigou.closet.entity.ClosetItem;
import com.zhigou.closet.entity.HomeAsset;
import com.zhigou.closet.service.ClosetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(produces = "application/json")
@RequiredArgsConstructor
public class ClosetController {

    private final ClosetService closetService;

    /** 我的衣物（分类/季节过滤） */
    @GetMapping("/closet/items")
    public Result<List<ClosetItem>> items(@RequestParam Long userId,
                                          @RequestParam(required = false) String category,
                                          @RequestParam(required = false) String season) {
        return Result.ok(closetService.myItems(userId, category, season));
    }

    /** 添加衣物 */
    @PostMapping("/closet/item")
    public Result<ClosetItem> add(@RequestBody Map<String, Object> body) {
        return Result.ok(closetService.addItem(
                Long.valueOf(String.valueOf(body.get("userId"))),
                (String) body.get("name"),
                (String) body.get("category"),
                (String) body.get("season"),
                (String) body.get("color"),
                (String) body.get("imageUrl"),
                (String) body.get("tags")));
    }

    /** 穿着打卡 */
    @PostMapping("/closet/item/{id}/wear")
    public Result<ClosetItem> wear(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.ok(closetService.wear(Long.valueOf(String.valueOf(body.get("userId"))), id));
    }

    /** 删除衣物（逻辑删） */
    @DeleteMapping("/closet/item/{id}")
    public Result<ClosetItem> remove(@PathVariable Long id, @RequestParam Long userId) {
        return Result.ok(closetService.removeItem(userId, id));
    }

    /** 穿搭推荐（规则引擎） */
    @GetMapping("/closet/outfit/recommend")
    public Result<Map<String, Object>> recommend(@RequestParam Long userId,
                                                 @RequestParam(required = false) String occasion) {
        return Result.ok(closetService.recommendOutfit(userId, occasion));
    }

    /** 家居盘点 */
    @GetMapping("/closet/home/list")
    public Result<List<HomeAsset>> assets(@RequestParam Long userId,
                                          @RequestParam(required = false) String category) {
        return Result.ok(closetService.myAssets(userId, category));
    }

    /** 添加家居物品 */
    @PostMapping("/closet/home/item")
    public Result<HomeAsset> addAsset(@RequestBody Map<String, Object> body) {
        return Result.ok(closetService.addAsset(
                Long.valueOf(String.valueOf(body.get("userId"))),
                (String) body.get("name"),
                (String) body.get("category"),
                body.get("quantity") == null ? null : Integer.valueOf(String.valueOf(body.get("quantity"))),
                (String) body.get("unit"),
                (String) body.get("expireAt")));
    }

    /** 补货清单（低量/临期） */
    @GetMapping("/closet/home/replenish")
    public Result<List<HomeAsset>> replenish(@RequestParam Long userId) {
        return Result.ok(closetService.replenishList(userId));
    }
}
