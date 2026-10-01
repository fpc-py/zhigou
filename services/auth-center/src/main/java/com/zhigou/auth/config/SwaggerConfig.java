package com.zhigou.auth.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("智购 · 认证中心")
                        .description("手机号验证码登录 + JWT 签发")
                        .version("0.1.0")
                        .contact(new Contact()
                                .name("智购团队")));
    }
}