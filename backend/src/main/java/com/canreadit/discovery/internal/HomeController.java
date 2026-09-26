package com.canreadit.discovery.internal;

import com.canreadit.shared.PublicCache;
import java.time.Duration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HomeController {

    private final HomeService home;

    HomeController(HomeService home) {
        this.home = home;
    }

    @GetMapping("/api/v1/home")
    ResponseEntity<HomeResponse> home() {
        return ResponseEntity.ok()
                .cacheControl(PublicCache.sharedFor(Duration.ofSeconds(60)))
                .body(home.home());
    }
}
