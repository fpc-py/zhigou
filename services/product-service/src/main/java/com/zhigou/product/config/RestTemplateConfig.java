package com.zhigou.product.config;

import com.zhigou.common.trace.TraceIdClientHttpRequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/** 跨服务 HTTP 调用（AI 省钱助手 C 块：省钱汇总聚合 cart/marketing） */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(500);
        factory.setReadTimeout(2000);
        // 工作流3 纪律：出站请求带 x-trace-id 头，全链路可追踪
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.setInterceptors(List.of(new TraceIdClientHttpRequestInterceptor()));
        return restTemplate;
    }
}
