package com.zhigou.aftersale;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
@SpringBootApplication(scanBasePackages = "com.zhigou") @EnableFeignClients
public class AftersaleServiceApplication {
    public static void main(String[] args) { SpringApplication.run(AftersaleServiceApplication.class, args); }
}