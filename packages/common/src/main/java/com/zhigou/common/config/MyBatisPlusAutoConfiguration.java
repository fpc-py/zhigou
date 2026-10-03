package com.zhigou.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 自动配置
 *
 * 通过 Spring Boot AutoConfiguration 机制注册 MyMetaObjectHandler，
 * 而不是 @ComponentScan 扫描。@ConditionalOnClass 用 name 字符串形式
 * 评估，确保 cart-service 等不依赖 MyBatis-Plus 的服务在条件评估阶段
 * 就直接短路跳过整个配置类，不会触发 MetaObjectHandler 类加载。
 */
@Configuration
@ConditionalOnClass(name = "com.baomidou.mybatisplus.core.handlers.MetaObjectHandler")
public class MyBatisPlusAutoConfiguration {

    @Bean
    public MetaObjectHandler myMetaObjectHandler() {
        return new MyMetaObjectHandler();
    }
}
