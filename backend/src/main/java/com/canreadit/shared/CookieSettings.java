package com.canreadit.shared;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How the API sets cookies. {@code secure} is off only for local development over plain HTTP.
 *
 * @param secure whether cookies carry the {@code Secure} attribute
 */
@ConfigurationProperties("app.cookies")
public record CookieSettings(boolean secure) {}
