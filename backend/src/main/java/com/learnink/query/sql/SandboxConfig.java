package com.learnink.query.sql;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SandboxConfig {

    @Bean(destroyMethod = "close")
    SandboxDataSources sandboxDataSources(SandboxProperties properties) {
        return new SandboxDataSources(properties);
    }
}
