package com.canreadit.analytics.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param retention how long raw events are kept; daily counts stay */
@ConfigurationProperties("app.analytics")
record AnalyticsProperties(Duration retention) {}
