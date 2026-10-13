package com.medcore.api.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableCaching
@EnableScheduling
@ConfigurationPropertiesScan("com.medcore.api.config")
public class ApplicationConfig {

    /** Injected wherever "now" matters, so time-dependent logic is testable. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
