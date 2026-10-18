package com.zhigou.common.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 服务间调用的编程式熔断执行器（工作流3 #7）。
 *
 * 为什么不用 @CircuitBreaker 注解：
 * 1. 注解走 Spring AOP 代理，同类 private 方法 self-invocation 不经过代理 → 熔断失效（经典坑）；
 * 2. 本项目的服务间调用点（fetchSkuInfo/releaseInventory/validateSku）都是同类私有方法，
 *    注解方案需要把方法提 public + 自注入代理，侵入大。
 *
 * 编程式方案：CircuitBreakerRegistry 由 resilience4j-spring-boot3 自动提供，
 * 调用点用 `registry.circuitBreaker(name).executeSupplier(call)` 包住远程调用，
 * 失败抛异常由调用方既有 try/catch 降级逻辑接住（零行为变更，只加熔断）。
 *
 * 可观测：每个实例注册状态变迁事件日志（CLOSED→OPEN→HALF_OPEN→CLOSED），
 * 熔断开关全程留痕，供排障与告警消费（工作流3 #8 的埋点基础）。
 * 熔断参数（滑动窗口/阈值/半开探活）在服务 application.yml 的
 * resilience4j.circuitbreaker.instances.<name> 配置。
 */
@Slf4j
@Component
public class ResilientHttp {

    private final CircuitBreakerRegistry registry;
    /** 实例缓存：同一名字只注册一次事件监听 */
    private final ConcurrentHashMap<String, CircuitBreaker> cache = new ConcurrentHashMap<>();

    public ResilientHttp(CircuitBreakerRegistry registry) {
        this.registry = registry;
    }

    /**
     * 熔断执行远程调用。
     *
     * @param breakerName 熔断器实例名（与 application.yml 配置对齐）
     * @param call        远程调用（RestTemplate/HttpUtil 等）
     * @return 调用结果；熔断打开或调用失败时抛异常，由调用方 catch 降级
     */
    public <T> T execute(String breakerName, Supplier<T> call) {
        CircuitBreaker breaker = cache.computeIfAbsent(breakerName, this::register);
        return breaker.executeSupplier(call);
    }

    private CircuitBreaker register(String name) {
        CircuitBreaker cb = registry.circuitBreaker(name);
        cb.getEventPublisher().onStateTransition(e -> {
            String from = e.getStateTransition().getFromState().name();
            String to = e.getStateTransition().getToState().name();
            log.warn("[熔断] {} 状态变化: {} → {}", name, from, to);
        });
        return cb;
    }
}
