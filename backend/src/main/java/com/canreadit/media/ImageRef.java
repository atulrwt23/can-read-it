package com.canreadit.media;

/** A resolved image: a URL the browser can load plus its intrinsic size (to avoid layout shift). */
public record ImageRef(String url, int width, int height) {}
