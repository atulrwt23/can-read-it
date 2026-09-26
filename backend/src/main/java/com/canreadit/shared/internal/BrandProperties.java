package com.canreadit.shared.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Display name of the product, kept in one place so renaming is a config change. */
@ConfigurationProperties("app.brand")
public record BrandProperties(@DefaultValue("CanReadIt") String name) {}
