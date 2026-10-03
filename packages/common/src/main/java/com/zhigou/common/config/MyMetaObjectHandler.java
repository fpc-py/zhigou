package com.zhigou.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器
 * 自动填充 createTime / updateTime 字段
 *
 * 注意：本类不使用 @Component，避免 cart-service 等纯 Redis 服务因
 * classpath 上缺少 MetaObjectHandler 接口导致 ConfigurationClassParser
 * 在读取类元数据时触发类加载而抛 NoClassDefFoundError。
 * 注册逻辑见 MyBatisPlusAutoConfiguration，仅在 classpath 上存在
 * MyBatis-Plus 时通过 Spring Boot AutoConfiguration 机制注册。
 */
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}