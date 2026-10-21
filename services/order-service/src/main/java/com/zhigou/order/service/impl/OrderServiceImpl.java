package com.zhigou.order.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhigou.common.BizException;
import com.zhigou.order.dto.CreateOrderRequest;
import com.zhigou.order.dto.OrderResponse;
import com.zhigou.order.dto.OrderStatsOverview;
import com.zhigou.order.entity.OrderItem;
import com.zhigou.order.entity.OrderMain;
import com.zhigou.order.entity.OrderShipment;
import com.zhigou.order.entity.Outbox;
import com.zhigou.order.mapper.OrderItemMapper;
import com.zhigou.order.mapper.OrderMainMapper;
import com.zhigou.order.mapper.OutboxMapper;
import com.zhigou.order.service.OrderService;
import com.zhigou.order.state.OrderState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j @Service @RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMainMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final OutboxMapper outboxMapper;
    private final com.zhigou.order.mapper.FulfillmentActionMapper fulfillmentActionMapper;
    private final com.zhigou.order.mapper.OrderShipmentMapper shipmentMapper;
    private final RestTemplate restTemplate;
    private final com.zhigou.common.resilience.ResilientHttp resilientHttp;

    @Value("${product-service.url}") private String productServiceUrl;
    @Value("${inventory-service.url}") private String inventoryServiceUrl;

    /** 占位价（product-service 不可用时的回退值，与历史实现一致） */
    private static final long FALLBACK_PRICE = 100L;

    /**
     * create 事务显式用 READ COMMITTED（而非 MySQL 默认 REPEATABLE READ），解决并发幂等兜底回查：
     * REPEATABLE READ 下事务内普通 SELECT 是快照读（读事务首次读建立的快照），catch 回查看不到并发线程刚提交的订单，
     * 返回 null 只能 rethrow，幂等退化为 500；READ COMMITTED 每语句重建快照，回查能读到最新已提交数据。
     * 备选方案均弃用：FOR UPDATE 当前读 → 唯一键冲突的 S 锁升级 X 锁与并发互持 S 锁死锁；REQUIRES_NEW 独立事务 → 连接占用翻倍，30 并发打爆 HikariCP 默认池。
     */
    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderResponse create(Long userId, CreateOrderRequest req) {
        // 1. 幂等检查
        OrderMain existing = orderMapper.selectFullByRequestId(req.getRequestId());
        if (existing != null) {
            log.info("重复下单: requestId={}", req.getRequestId());
            return buildResponse(existing);
        }

        // 2. 拉取商品真实价格/名称（失败自动回退占位值）
        Map<String, Long> priceMap = new HashMap<>();
        Map<String, String> nameMap = new HashMap<>();
        fetchSkuInfo(userId, priceMap, nameMap);

        // 3. 创建订单
        long orderId = IdUtil.getSnowflakeNextId();
        long totalAmount = 0;
        List<OrderItem> items = new ArrayList<>();

        for (CreateOrderRequest.SkuItem si : req.getSkuItems()) {
            String key = String.valueOf(si.getSkuId());
            Long price = priceMap.get(key);
            if (price == null) price = FALLBACK_PRICE;
            String skuName = nameMap.getOrDefault(key, "SKU-" + si.getSkuId());

            OrderItem item = new OrderItem();
            item.setOrderId(orderId); item.setSkuId(si.getSkuId()); item.setCount(si.getCount());
            item.setPrice(price); item.setSkuName(skuName);
            totalAmount += item.getPrice() * item.getCount();
            items.add(item);
        }

        OrderMain order = new OrderMain();
        order.setOrderId(orderId); order.setUserId(userId);
        order.setRequestId(req.getRequestId());
        order.setOrderStatus(OrderState.INIT.name());
        order.setTotalAmount(totalAmount); order.setPayAmount(totalAmount);
        order.setCouponId(req.getCouponId());
        try {
            orderMapper.insert(order);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发兜底：先查后插存在竞态窗口，唯一索引 uk_user_request(user_id, request_id) 是最后防线。
            // 撞索引说明请求已被并发线程成功落库，转幂等返回既有订单，避免把 500 抛给用户。
            log.info("重复下单(唯一索引兜底): requestId={}", req.getRequestId());
            // create 事务为 READ COMMITTED，此处普通 SELECT 每语句重建快照，能读到刚提交的并发订单。
            OrderMain dup = orderMapper.selectFullByRequestId(req.getRequestId());
            if (dup != null) return buildResponse(dup);
            throw e;
        }

        for (OrderItem item : items) itemMapper.insert(item);

        // 4. outbox
        Outbox outbox = new Outbox();
        outbox.setMessageId(IdUtil.fastSimpleUUID());
        outbox.setTopic("ORDER_CREATED"); outbox.setTag("CREATE");
        outbox.setPayload(JSONUtil.toJsonStr(new CreateOrderRequest()));
        outboxMapper.insert(outbox);

        log.info("订单创建: orderId={}, userId={}, amount={}", orderId, userId, totalAmount);
        return buildResponse(order);
    }

    /**
     * 从 product-service 拉取商品信息（真实价格 + SPU 名称）。
     * 一次 /product/page 即可覆盖全部 SKU（演示环境商品量小）；
     * 任一环节失败仅告警，由调用方回退占位值，不影响下单主流程。
     */
    private void fetchSkuInfo(Long userId, Map<String, Long> priceMap, Map<String, String> nameMap) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", String.valueOf(userId));
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            String url = productServiceUrl + "/product/page?pageNum=1&pageSize=100";
            // 工作流3 #7：熔断（orderFetchSku）——product 故障时快速失败，由 catch 降级占位值
            ResponseEntity<String> resp = resilientHttp.execute("orderFetchSku",
                    () -> restTemplate.exchange(url, HttpMethod.GET, entity, String.class));
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.warn("product-service /product/page 非 2xx: {}", resp.getStatusCode());
                return;
            }
            JSONObject body = JSONUtil.parseObj(resp.getBody());
            JSONArray records = body.getJSONObject("data").getJSONArray("records");
            if (records == null) return;
            for (Object o : records) {
                JSONObject spu = (JSONObject) o;
                String name = spu.getStr("name");
                JSONArray skus = spu.getJSONArray("skus");
                if (skus == null) continue;
                for (Object s : skus) {
                    JSONObject sku = (JSONObject) s;
                    Long skuId = sku.getLong("skuId");
                    Long price = sku.getLong("price");
                    if (skuId != null && price != null) {
                        priceMap.put(String.valueOf(skuId), price);
                        if (name != null) nameMap.put(String.valueOf(skuId), name);
                    }
                }
            }
            log.info("拉取商品信息: sku 数={}", priceMap.size());
        } catch (Exception e) {
            log.warn("拉取商品信息失败，回退占位价格: {}", e.getMessage());
        }
    }

    @Override @Transactional
    public void cancel(Long userId, Long orderId) {
        OrderMain order = requireOrder(orderId);
        if (!order.getUserId().equals(userId)) throw new BizException(403, "无权操作");
        OrderState current = OrderState.from(order.getOrderStatus());
        OrderState.validateTransition(current, OrderState.CLOSED);
        order.setOrderStatus(OrderState.CLOSED.name());
        order.setCloseReason("用户取消");
        orderMapper.updateById(order);

            // 写 outbox 回滚消息（含 items，供 MQ 消费者释放库存）
            writeClosedOutbox(order);
            log.info("订单取消: orderId={}", orderId);
    }

    @Override @Transactional
    public void payCallback(Long orderId) {
        OrderMain order = requireOrder(orderId);
        if (OrderState.PAID.equals(OrderState.from(order.getOrderStatus()))) {
            log.info("重复支付回调，幂等返回: orderId={}", orderId);
            return;
        }
        OrderState.validateTransition(OrderState.from(order.getOrderStatus()), OrderState.PAID);
        order.setOrderStatus(OrderState.PAID.name());
        orderMapper.updateById(order);
        log.info("订单支付: orderId={}", orderId);
    }

    @Override
    public OrderResponse getByOrderId(Long orderId) {
        return buildResponse(requireOrder(orderId));
    }

    @Override
    public List<OrderResponse> mine(Long userId) {
        List<OrderMain> orders = orderMapper.selectFullByUserId(userId);
        return orders.stream().map(this::buildResponse).collect(Collectors.toList());
    }

    @Override @Transactional
    public int closeExpired(int minutes) {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(minutes);
        List<OrderMain> expired = orderMapper.selectFullByStatusBefore(OrderState.INIT.name(), deadline);
        if (expired.isEmpty()) {
            log.info("超时关单: 无超时未支付订单 (threshold={}min)", minutes);
            return 0;
        }
        int closed = 0;
        for (OrderMain order : expired) {
            OrderState.validateTransition(OrderState.from(order.getOrderStatus()), OrderState.CLOSED);
            order.setOrderStatus(OrderState.CLOSED.name());
            order.setCloseReason("超时未支付自动关单");
            orderMapper.updateById(order);

            // outbox：超时关单事件（含 items，供 MQ 消费者释放库存兜底）
            writeClosedOutbox(order);

            // 释放预占库存（失败仅告警，由对账/重试兜底，不阻断关单）
            releaseInventory(order);
            closed++;
        }
        log.info("超时关单: 关闭 {} 笔超时未支付订单 (threshold={}min)", closed, minutes);
        return closed;
    }

    /** 关单/取消时调 inventory-service 释放预占库存（演示环境预扣逻辑简单，一次 /rollback 释放该单全部 SKU 数量） */
    private void releaseInventory(OrderMain order) {
        try {
            List<OrderItem> items = itemMapper.selectList(
                    new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getOrderId()));
            List<Map<String, Object>> skuList = items.stream().map(i -> {
                Map<String, Object> m = new HashMap<>();
                m.put("skuId", i.getSkuId());
                m.put("count", i.getCount());
                return m;
            }).collect(Collectors.toList());
            Map<String, Object> body = new HashMap<>();
            body.put("orderId", String.valueOf(order.getOrderId()));
            body.put("items", skuList);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", String.valueOf(order.getUserId()));
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            // 工作流3 #7：熔断（orderReleaseInventory）——inventory 故障时快速失败，留待对账/MQ 兜底
            ResponseEntity<String> resp = resilientHttp.execute("orderReleaseInventory",
                    () -> restTemplate.exchange(
                            inventoryServiceUrl + "/inventory/rollback", HttpMethod.POST,
                            new HttpEntity<>(body, headers), String.class));
            if (resp.getStatusCode().is2xxSuccessful()) {
                log.info("释放库存成功: orderId={}, items={}", order.getOrderId(), skuList.size());
            } else {
                log.warn("释放库存非 2xx: orderId={}, status={}", order.getOrderId(), resp.getStatusCode());
            }
        } catch (Exception e) {
            log.warn("释放库存失败（留待对账兜底）: orderId={}, err={}", order.getOrderId(), e.getMessage());
        }
    }

    /** 超时关单事件体：含 items 明细，供 MQ 消费者（inventory-service）释放库存 */
    private String buildClosedEventPayload(OrderMain order) {
        List<OrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getOrderId()));
        JSONObject body = new JSONObject();
        body.set("orderId", String.valueOf(order.getOrderId()));
        body.set("userId", String.valueOf(order.getUserId()));
        body.set("closeReason", order.getCloseReason());
        JSONArray arr = new JSONArray();
        for (OrderItem it : items) {
            JSONObject item = new JSONObject();
            item.set("skuId", it.getSkuId());
            item.set("count", it.getCount());
            arr.add(item);
        }
        body.set("items", arr);
        return body.toString();
    }

    /** 统一写 ORDER_CLOSED outbox（超时关单 / 用户取消共用，事件体含 items） */
    private void writeClosedOutbox(OrderMain order) {
        Outbox outbox = new Outbox();
        outbox.setMessageId(IdUtil.fastSimpleUUID());
        outbox.setTopic("ORDER_CLOSED");
        outbox.setTag("CLOSE");
        outbox.setPayload(buildClosedEventPayload(order));
        outboxMapper.insert(outbox);
    }

    private OrderMain requireOrder(Long orderId) {
        OrderMain o = orderMapper.selectFullByOrderId(orderId);
        if (o == null) throw new BizException(404, "订单不存在");
        return o;
    }

    @Override
    public OrderStatsOverview overview() {
        List<OrderMain> orders = orderMapper.selectAllFull();
        List<OrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<OrderItem>());

        OrderStatsOverview ov = new OrderStatsOverview();
        ov.setTotalOrders((long) orders.size());

        Set<String> paidStates = Set.of("PAID", "SHIPPED", "COMPLETED");
        long sales = orders.stream()
                .filter(o -> paidStates.contains(o.getOrderStatus()))
                .mapToLong(o -> o.getPayAmount() == null ? 0 : o.getPayAmount())
                .sum();
        ov.setTotalSalesFen(sales);

        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        long todayOrders = orders.stream().filter(o -> o.getCreateTime() != null && o.getCreateTime().isAfter(todayStart)).count();
        long todaySales = orders.stream()
                .filter(o -> paidStates.contains(o.getOrderStatus()))
                .filter(o -> o.getCreateTime() != null && o.getCreateTime().isAfter(todayStart))
                .mapToLong(o -> o.getPayAmount() == null ? 0 : o.getPayAmount())
                .sum();
        ov.setTodayOrders(todayOrders);
        ov.setTodaySalesFen(todaySales);

        Map<String, Long> dist = orders.stream().collect(Collectors.groupingBy(OrderMain::getOrderStatus, Collectors.counting()));
        ov.setStatusDist(dist);

        Map<Long, OrderStatsOverview.HotSpu> acc = new HashMap<>();
        for (OrderItem it : items) {
            OrderStatsOverview.HotSpu h = acc.computeIfAbsent(it.getSpuId(), k -> {
                OrderStatsOverview.HotSpu x = new OrderStatsOverview.HotSpu();
                x.setSpuId(k);
                x.setSpuName(it.getSkuName());
                x.setSoldCount(0L);
                x.setSalesFen(0L);
                return x;
            });
            h.setSoldCount(h.getSoldCount() + it.getCount());
            h.setSalesFen(h.getSalesFen() + it.getPrice() * it.getCount());
        }
        List<OrderStatsOverview.HotSpu> hot = acc.values().stream()
                .sorted((a, b) -> Long.compare(b.getSoldCount(), a.getSoldCount()))
                .limit(5)
                .collect(Collectors.toList());
        ov.setHotSpus(hot);

        ov.setPendingAfterSale(orders.stream().filter(o -> "REFUNDING".equals(o.getOrderStatus())).count());
        ov.setGeneratedAt(LocalDateTime.now().toString());
        return ov;
    }

    @Override
    public List<OrderResponse> pendingFulfillment() {
        List<OrderMain> orders = orderMapper.selectFullByStatus(OrderState.PAID.name());
        // 已处理订单集合（存在 DONE 处理记录）
        Set<Long> handled = listFulfillmentActions(1000).stream()
                .map(com.zhigou.order.entity.FulfillmentAction::getOrderId).collect(Collectors.toSet());
        return orders.stream().map(o -> buildResponse(o, handled.contains(o.getOrderId()))).collect(Collectors.toList());
    }

    @Override @Transactional
    public int fulfillmentAction(String action, List<Long> orderIds, String reason) {
        if (orderIds == null || orderIds.isEmpty()) throw new BizException(40030, "未指定处理订单");
        java.util.Set<String> allowed = java.util.Set.of("SPLIT", "DELAY", "OFF_SHELF", "REPLENISH");
        if (!allowed.contains(action)) throw new BizException(40031, "不支持的处理动作: " + action);
        int done = 0;
        for (Long orderId : orderIds) {
            OrderMain order = requireOrder(orderId);
            if (!OrderState.PAID.name().equals(order.getOrderStatus())) {
                log.warn("跳过非 PAID 订单: orderId={}, status={}", orderId, order.getOrderStatus());
                continue;
            }
            com.zhigou.order.entity.FulfillmentAction rec = new com.zhigou.order.entity.FulfillmentAction();
            rec.setOrderId(orderId);
            rec.setAction(action);
            rec.setReason(reason == null || reason.isBlank() ? defaultReason(action) : reason);
            rec.setStatus("DONE");
            fulfillmentActionMapper.insert(rec);
            done++;
        }
        log.info("异常订单处理: action={}, orders={}, done={}", action, orderIds.size(), done);
        return done;
    }

    private String defaultReason(String action) {
        switch (action) {
            case "SPLIT": return "拆分发货：有货 SKU 先发，缺货 SKU 补货后追发";
            case "DELAY": return "延期发货：已与买家协商延期";
            case "OFF_SHELF": return "下架停单：SKU 缺货严重，暂停新单";
            case "REPLENISH": return "补货后发货：已发起补货，补到安全库存后履约";
            default: return "异常订单处理";
        }
    }

    @Override
    public List<com.zhigou.order.entity.FulfillmentAction> listFulfillmentActions(int limit) {
        return fulfillmentActionMapper.selectList(new LambdaQueryWrapper<com.zhigou.order.entity.FulfillmentAction>()
                .orderByDesc(com.zhigou.order.entity.FulfillmentAction::getCreateTime)
                .last("limit " + Math.max(1, Math.min(limit, 100))));
    }

    @Override @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> shipPartial(Long orderId, Long skuId, Integer count, String trackingNo) {
        OrderMain order = requireOrder(orderId);
        String status = order.getOrderStatus();
        if (!Set.of("PAID", "SHIPPING").contains(status))
            throw new BizException(40050, "当前状态不可发货: " + status);
        if (count == null || count < 1) throw new BizException(40051, "发货数量不合法");

        OrderItem item = itemMapper.selectOne(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId).eq(OrderItem::getSkuId, skuId));
        if (item == null) throw new BizException(404, "订单商品不存在: skuId=" + skuId);

        int shippedBefore = shippedCount(orderId, skuId);
        int remain = item.getCount() - shippedBefore;
        if (count > remain) throw new BizException(40052, "发货超量: 该SKU剩余可发 " + remain + " 件");

        OrderShipment s = new OrderShipment();
        s.setOrderId(orderId); s.setSkuId(skuId); s.setCount(count);
        s.setTrackingNo(trackingNo); s.setShipTime(LocalDateTime.now());
        shipmentMapper.insert(s);

        // 状态迁移：全部 SKU 发完 → SHIPPED；否则 PAID→SHIPPING
        String newStatus;
        if (allShipped(orderId)) {
            OrderState.validateTransition(OrderState.from(status), OrderState.SHIPPED);
            newStatus = "SHIPPED";
        } else {
            if ("PAID".equals(status)) {
                OrderState.validateTransition(OrderState.PAID, OrderState.SHIPPING);
            }
            newStatus = "SHIPPING";
        }
        if (!newStatus.equals(status)) {
            order.setOrderStatus(newStatus);
            orderMapper.updateById(order);
        }
        log.info("部分发货: orderId={}, skuId={}, count={}, 状态 {}->{}", orderId, skuId, count, status, newStatus);

        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("orderStatus", newStatus);
        out.put("skuId", skuId);
        out.put("shippedCount", shippedBefore + count);
        out.put("remainingCount", Math.max(0, item.getCount() - shippedBefore - count));
        out.put("shipments", shipments(orderId));
        return out;
    }

    @Override
    public List<OrderShipment> shipments(Long orderId) {
        return shipmentMapper.selectList(new LambdaQueryWrapper<OrderShipment>()
                .eq(OrderShipment::getOrderId, orderId).orderByAsc(OrderShipment::getId));
    }

    private int shippedCount(Long orderId, Long skuId) {
        return shipmentMapper.selectList(new LambdaQueryWrapper<OrderShipment>()
                .eq(OrderShipment::getOrderId, orderId).eq(OrderShipment::getSkuId, skuId))
                .stream().mapToInt(OrderShipment::getCount).sum();
    }

    /** 是否所有 SKU 均已全部发完 */
    private boolean allShipped(Long orderId) {
        List<OrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        if (items.isEmpty()) return false;
        for (OrderItem it : items) {
            if (shippedCount(orderId, it.getSkuId()) < it.getCount()) return false;
        }
        return true;
    }

    private OrderResponse buildResponse(OrderMain order) {
        return buildResponse(order, false);
    }

    private OrderResponse buildResponse(OrderMain order, boolean handled) {
        List<OrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getOrderId()));
        return OrderResponse.builder()
                .orderId(order.getOrderId()).userId(order.getUserId()).orderStatus(order.getOrderStatus())
                .totalAmount(order.getTotalAmount()).payAmount(order.getPayAmount())
                .handled(handled)
                .items(items.stream().map(i -> OrderResponse.Item.builder()
                        .skuId(i.getSkuId()).skuName(i.getSkuName()).price(i.getPrice()).count(i.getCount()).build())
                        .collect(Collectors.toList()))
                .build();
    }
}