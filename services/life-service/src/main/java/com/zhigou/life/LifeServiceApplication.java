package com.zhigou.life;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(scanBasePackages = "com.zhigou")
public class LifeServiceApplication {
    public static void main(String[] args) { SpringApplication.run(LifeServiceApplication.class, args); }
}
