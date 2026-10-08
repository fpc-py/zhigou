package com.zhigou.common.mask;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * 日志脱敏自动配置：任意服务引入 zhigou-common 即生效，
 * 可通过 {@code zhigou.mask.enabled=false} 关闭。
 */
@AutoConfiguration
@EnableAspectJAutoProxy
@ConditionalOnProperty(prefix = "zhigou.mask", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MaskAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SensitiveLogAspect sensitiveLogAspect() {
        return new SensitiveLogAspect();
    }
}
