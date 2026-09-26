package com.canreadit;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(CanReadItApplication.class);

    @Test
    void modulesRespectTheirBoundaries() {
        modules.verify();
    }
}
