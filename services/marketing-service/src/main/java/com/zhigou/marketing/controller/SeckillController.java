package com.zhigou.marketing.controller;

import com.zhigou.common.Result;
import com.zhigou.marketing.entity.SeckillActivity;
import com.zhigou.marketing.service.SeckillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping(produces = "application/json") @RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    @GetMapping("/seckill/activities")
    public Result<List<SeckillActivity>> activities() {
        return Result.ok(seckillService.listActive());
    }

    @GetMapping("/seckill/activity/{id}")
    public Result<SeckillActivity> detail(@PathVariable Long id) {
        return Result.ok(seckillService.detail(id));
    }

    /** 秒杀预扣：userId 取自请求体（演示口径，正式版从网关 JWT 取；兼容数字/字符串两种入参） */
    @PostMapping("/seckill/buy")
    public Result<Map<String, Object>> buy(@RequestBody Map<String, Object> body) {
        Object uid = body.get("userId");
        Long userId = uid instanceof Number n ? n.longValue() : Long.valueOf(String.valueOf(uid));
        Long activityId = ((Number) body.get("activityId")).longValue();
        Integer count = body.get("count") == null ? 1 : ((Number) body.get("count")).intValue();
        return Result.ok(seckillService.buy(userId, activityId, count));
    }

    @PostMapping("/seckill/release")
    public Result<Void> release(@RequestBody Map<String, Object> body) {
        Long activityId = ((Number) body.get("activityId")).longValue();
        Integer count = body.get("count") == null ? 1 : ((Number) body.get("count")).intValue();
        seckillService.release(activityId, count);
        return Result.ok();
    }
}
