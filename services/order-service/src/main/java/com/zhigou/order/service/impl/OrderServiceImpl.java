package com.zhigou.order.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhigou.common.BizException;
import com.zhigou.order.dto.CreateOrderRequest;
import com.zhigou.order.dto.OrderResponse;
import com.zhigou.order.entity.OrderItem;
import com.zhigou.order.entity.OrderMain;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j @Service @RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMainMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final OutboxMapper outboxMapper;
    private final RestTemplate restTemplate;

    @Value("${product-service.url}") private String productServiceUrl;

    /** 占位价（product-service 不可用时的回退值，与历史实现一致） */
    private static final long FALLBACK_PRICE = 100L;

    @Override @Transactional
    public OrderResponse create(Long userId, CreateOrderRequest req) {
        // 1. 幂等检查
        OrderMain existing = orderMapper.selectOne(new LambdaQueryWrapper<OrderMain>()
                .eq(OrderMain::getUserId, userId).eq(OrderMain::getRequestId, req.getRequestId()));
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
        orderMapper.insert(order);

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
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
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

        // 写 outbox 回滚消息
        Outbox outbox = new Outbox();
        outbox.setMessageId(IdUtil.fastSimpleUUID());
        outbox.setTopic("ORDER_CLOSED"); outbox.setTag("CLOSE");
        outbox.setPayload(JSONUtil.toJsonStr(order));
        outboxMapper.insert(outbox);

        log.info("订单取消: orderId={}", orderId);
    }

    @Override @Transactional
    public void payCallback(Long orderId) {
        OrderMain order = requireOrder(orderId);
        OrderState current = OrderState.from(order.getOrderStatus());
        OrderState.validateTransition(current, OrderState.PAID);
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
        List<OrderMain> orders = orderMapper.selectList(new LambdaQueryWrapper<OrderMain>()
                .eq(OrderMain::getUserId, userId)
                .orderByDesc(OrderMain::getCreateTime));
        return orders.stream().map(this::buildResponse).collect(Collectors.toList());
    }

    private OrderMain requireOrder(Long orderId) {
        OrderMain o = orderMapper.selectOne(new LambdaQueryWrapper<OrderMain>().eq(OrderMain::getOrderId, orderId));
        if (o == null) throw new BizException(404, "订单不存在");
        return o;
    }

    private OrderResponse buildResponse(OrderMain order) {
        List<OrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getOrderId()));
        return OrderResponse.builder()
                .orderId(order.getOrderId()).userId(order.getUserId()).orderStatus(order.getOrderStatus())
                .totalAmount(order.getTotalAmount()).payAmount(order.getPayAmount())
                .items(items.stream().map(i -> OrderResponse.Item.builder()
                        .skuId(i.getSkuId()).skuName(i.getSkuName()).price(i.getPrice()).count(i.getCount()).build())
                        .collect(Collectors.toList()))
                .build();
    }
}