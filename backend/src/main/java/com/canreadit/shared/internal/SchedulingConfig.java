package com.canreadit.shared.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled} jobs (publishing, view roll-ups). Integration tests switch it off with {@code
 * app.scheduling.enabled=false} and run the jobs themselves.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnBooleanProperty(name = "app.scheduling.enabled", matchIfMissing = true)
class SchedulingConfig {}
