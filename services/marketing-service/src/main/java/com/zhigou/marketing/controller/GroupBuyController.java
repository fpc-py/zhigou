package com.zhigou.marketing.controller;

import com.zhigou.common.Result;
import com.zhigou.marketing.dto.GroupBuyActivityVO;
import com.zhigou.marketing.entity.GroupBuyOrder;
import com.zhigou.marketing.service.GroupBuyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController @RequestMapping(produces = "application/json") @RequiredArgsConstructor
public class GroupBuyController {

    private final GroupBuyService groupBuyService;

    /** 拼团活动列表（含可加入团单） */
    @GetMapping("/group-buy/activities")
    public Result<List<GroupBuyActivityVO>> activities() {
        return Result.ok(groupBuyService.activities());
    }

    /** 开团 */
    @PostMapping("/group-buy/open")
    public Result<GroupBuyOrder> open(@RequestBody Map<String, Long> body) {
        return Result.ok(groupBuyService.open(body.get("activityId"), body.get("userId")));
    }

    /** 参团 */
    @PostMapping("/group-buy/join")
    public Result<GroupBuyOrder> join(@RequestBody Map<String, Long> body) {
        return Result.ok(groupBuyService.join(body.get("groupId"), body.get("userId")));
    }

    /** 我的团单 */
    @GetMapping("/group-buy/mine")
    public Result<List<Map<String, Object>>> mine(@RequestParam Long userId) {
        return Result.ok(groupBuyService.mine(userId));
    }

    /** 团单详情 */
    @GetMapping("/group-buy/group/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(groupBuyService.detail(id));
    }
}
