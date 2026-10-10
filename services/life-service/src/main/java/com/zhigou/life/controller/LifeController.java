package com.zhigou.life.controller;

import com.zhigou.common.Result;
import com.zhigou.life.entity.LifeAppointment;
import com.zhigou.life.entity.LifeSku;
import com.zhigou.life.entity.PoiStore;
import com.zhigou.life.service.LifeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(produces = "application/json")
@RequiredArgsConstructor
public class LifeController {

    private final LifeService lifeService;

    /** 周边门店 / 商圈分页 */
    @GetMapping("/life/poi/page")
    public Result<List<PoiStore>> stores(@RequestParam(required = false) String category,
                                         @RequestParam(defaultValue = "1") int pageNum,
                                         @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(lifeService.pageStores(category, pageNum, pageSize));
    }

    /** 门店详情（含服务 SKU） */
    @GetMapping("/life/poi/{id}")
    public Result<Map<String, Object>> storeDetail(@PathVariable Long id) {
        return Result.ok(lifeService.storeDetail(id));
    }

    /** 服务 SKU 分页（外卖/生鲜/家政/到店券） */
    @GetMapping("/life/sku/page")
    public Result<List<LifeSku>> skus(@RequestParam(required = false) String category,
                                      @RequestParam(defaultValue = "1") int pageNum,
                                      @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(lifeService.pageSkus(category, pageNum, pageSize));
    }

    /** 创建到店 / 服务预约 */
    @PostMapping("/life/appointment")
    public Result<LifeAppointment> create(@RequestBody Map<String, Object> body) {
        return Result.ok(lifeService.createAppointment(
                body.get("userId") == null ? null : Long.valueOf(String.valueOf(body.get("userId"))),
                body.get("storeId") == null ? null : Long.valueOf(String.valueOf(body.get("storeId"))),
                body.get("skuId") == null ? null : Long.valueOf(String.valueOf(body.get("skuId"))),
                (String) body.get("appointmentTime"),
                (String) body.get("remark")));
    }

    /** 我的预约 */
    @GetMapping("/life/appointment/mine")
    public Result<List<LifeAppointment>> mine(@RequestParam Long userId) {
        return Result.ok(lifeService.myAppointments(userId));
    }

    /** 取消预约 */
    @PostMapping("/life/appointment/{id}/cancel")
    public Result<LifeAppointment> cancel(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.ok(lifeService.cancelAppointment(id,
                body.get("userId") == null ? null : Long.valueOf(String.valueOf(body.get("userId")))));
    }
}
