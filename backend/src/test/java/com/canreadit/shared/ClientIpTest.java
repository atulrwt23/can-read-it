package com.canreadit.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpTest {

    @Test
    void prefersCloudflareHeader() {
        var request = new MockHttpServletRequest();
        request.addHeader("CF-Connecting-IP", " 203.0.113.7 ");
        request.addHeader("X-Forwarded-For", "198.51.100.1, 10.0.0.2");
        assertThat(ClientIp.of(request)).isEqualTo("203.0.113.7");
        assertThat(ClientIp.isForwarded(request)).isTrue();
    }

    @Test
    void fallsBackToFirstForwardedHop() {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "198.51.100.1, 10.0.0.2");
        assertThat(ClientIp.of(request)).isEqualTo("198.51.100.1");
    }

    @Test
    void usesTheSocketAddressForDirectCalls() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.5");
        assertThat(ClientIp.of(request)).isEqualTo("172.18.0.5");
        assertThat(ClientIp.isForwarded(request)).isFalse();
    }
}
