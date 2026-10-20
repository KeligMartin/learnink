package com.learnink.startup;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/** Encadre les tâches de démarrage par le verrou partagé entre instances. */
@Configuration
public class StartupLockRunners {

    @Bean
    @Order(StartupOrder.ACQUIRE_LOCK)
    ApplicationRunner acquireStartupLock(StartupLock lock) {
        return args -> lock.acquire();
    }

    @Bean
    @Order(StartupOrder.RELEASE_LOCK)
    ApplicationRunner releaseStartupLock(StartupLock lock) {
        return args -> lock.release();
    }
}
