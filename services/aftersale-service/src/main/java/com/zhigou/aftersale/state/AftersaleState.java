package com.zhigou.aftersale.state;

import com.zhigou.common.BizException;
import java.util.*;

public enum AftersaleState {
    APPLYING, SELLER_APPROVED, REFUNDING, REFUNDED, REJECTED, CANCELED;

    private static final Map<AftersaleState, Set<AftersaleState>> ALLOWED = new EnumMap<>(AftersaleState.class);
    static {
        ALLOWED.put(APPLYING, Set.of(SELLER_APPROVED, REJECTED, CANCELED));
        ALLOWED.put(SELLER_APPROVED, Set.of(REFUNDING));
        ALLOWED.put(REFUNDING, Set.of(REFUNDED));
        ALLOWED.put(REFUNDED, Set.of());
        ALLOWED.put(REJECTED, Set.of());
        ALLOWED.put(CANCELED, Set.of());
    }

    public static void validate(AftersaleState from, AftersaleState to) {
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to))
            throw new BizException(40050, "非法状态跃迁: " + from + " -> " + to);
    }

    public static AftersaleState from(String s) {
        try { return valueOf(s.toUpperCase()); }
        catch (Exception e) { throw new BizException(40050, "无效状态: " + s); }
    }
}