package com.learnink.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties("learnink.security")
public record SecurityProperties(String jwtSecret, Duration jwtValidity, List<String> allowedOrigins) {
}
