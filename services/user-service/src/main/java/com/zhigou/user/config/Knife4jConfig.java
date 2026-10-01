package com.zhigou.user.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("智购 · 用户服务")
                        .description("用户资料 + 收货地址簿 + 会员等级")
                        .version("0.1.0")
                        .contact(new Contact().name("智购团队")));
    }
}