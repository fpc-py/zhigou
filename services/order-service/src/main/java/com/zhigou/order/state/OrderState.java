package com.zhigou.order.state;

import com.zhigou.common.BizException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public enum OrderState {
    INIT, PAID, SHIPPED, COMPLETED, CLOSED, REFUNDING, REFUNDED;

    private static final Map<OrderState, Set<OrderState>> ALLOWED = new EnumMap<>(OrderState.class);

    static {
        ALLOWED.put(INIT,      Set.of(PAID, CLOSED));
        ALLOWED.put(PAID,      Set.of(SHIPPED, REFUNDING));
        ALLOWED.put(SHIPPED,   Set.of(COMPLETED));
        ALLOWED.put(COMPLETED, Set.of());
        ALLOWED.put(CLOSED,    Set.of());
        ALLOWED.put(REFUNDING, Set.of(REFUNDED));
        ALLOWED.put(REFUNDED,  Set.of());
    }

    public static void validateTransition(OrderState from, OrderState to) {
        Set<OrderState> targets = ALLOWED.get(from);
        if (targets == null || !targets.contains(to)) {
            throw new BizException(40050, "非法状态跃迁: " + from + " -> " + to);
        }
    }

    public static OrderState from(String s) {
        try { return valueOf(s.toUpperCase()); }
        catch (Exception e) { throw new BizException(40050, "无效订单状态: " + s); }
    }
}