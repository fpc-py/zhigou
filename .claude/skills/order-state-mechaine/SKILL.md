# 订单状态机

定义和使用智购订单状态机。

## 状态枚举

INIT -> PAID -> SHIPPED -> COMPLETED
INIT -> CLOSED (未支付取消)
PAID -> REFUNDING -> REFUNDED

## 规则

1. 状态跃迁必须走显式 transition map，不允许直接 setStatus
2. 非法跃迁抛 BizException(ORDER_STATUS_ERROR, "当前状态不允许此操作")
3. 状态变更写本地消息表（outbox），MQ 通知下游
4. 状态机用 Spring StateMachine 或枚举 transition map（简单场景用枚举，复杂场景用 SM）

## 代码模板

```java
public enum OrderStatus {
    INIT, PAID, SHIPPED, COMPLETED, CLOSED, REFUNDING, REFUNDED;

    static {
        INIT.allowed = Set.of(PAID, CLOSED);
        PAID.allowed = Set.of(SHIPPED, REFUNDING);
        SHIPPED.allowed = Set.of(COMPLETED);
    }

    private Set<OrderStatus> allowed;

    public void validateTransition(OrderStatus target) {
        if (!allowed.contains(target)) {
            throw new BizException("ORDER_STATUS_ERROR",
                "不允许从 " + this + " 跃迁到 " + target);
        }
    }
}
```