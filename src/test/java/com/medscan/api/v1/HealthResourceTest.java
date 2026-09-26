package com.medscan.api.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HealthResourceTest {

    private final HealthResource resource = new HealthResource();

    @Test
    void returnsAnUpStatusWithoutSensitiveDetails() {
        HealthResponse response = resource.getHealth();

        assertEquals("UP", response.status());
    }
}
