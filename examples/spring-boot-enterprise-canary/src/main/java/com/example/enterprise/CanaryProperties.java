package com.example.enterprise;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "enterprise.canary")
public record CanaryProperties(String version, String platform, String greeting) {
}
