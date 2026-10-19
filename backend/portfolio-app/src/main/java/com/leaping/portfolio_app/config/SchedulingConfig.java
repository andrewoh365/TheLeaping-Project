package com.leaping.portfolio_app.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableScheduling
@ConditionalOnProperty(
        name = "leap.scheduling.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SchedulingConfig {
}
