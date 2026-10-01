package com.zhigou.file.config;

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
                        .title("智购 · 文件服务")
                        .description("文件上传到 MinIO，对外返回签名 URL")
                        .version("0.1.0")
                        .contact(new Contact().name("智购团队")));
    }
}