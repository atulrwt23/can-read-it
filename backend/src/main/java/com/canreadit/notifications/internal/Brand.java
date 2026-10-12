package com.canreadit.notifications.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** The product name used in emails ({@code app.brand.name}). */
@ConfigurationProperties("app.brand")
record Brand(@DefaultValue("CanReadIt") String name) {}
