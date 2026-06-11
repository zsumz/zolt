package com.example.enterprise;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EnterpriseCanaryApplication {
    public static void main(String[] args) {
        SpringApplication.run(EnterpriseCanaryApplication.class, args);
    }
}
