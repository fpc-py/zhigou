package com.zhigou.wallet;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(scanBasePackages = "com.zhigou")
public class WalletServiceApplication {
    public static void main(String[] args) { SpringApplication.run(WalletServiceApplication.class, args); }
}
