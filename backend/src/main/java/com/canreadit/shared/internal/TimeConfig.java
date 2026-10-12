package com.canreadit.shared.internal;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The application clock: UTC, like every timestamp (CLAUDE.md section 2). Day-based features such as view counts
 * use UTC days. TODO(product): a market's local day once launch markets are decided.
 */
@Configuration(proxyBeanMethods = false)
class TimeConfig {

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }
}
