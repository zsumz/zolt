package com.example.enterprise;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        classes = EnterpriseCanaryApplication.class,
        args = "--spring.main.web-application-type=none")
final class EnterpriseCanaryApplicationTest {
    @Autowired
    private CanaryProperties properties;

    @Autowired
    private StatusController controller;

    @Test
    void loadsFilteredConfigurationAndController() {
        assertThat(properties.version()).isEqualTo("0.1.0");
        assertThat(properties.platform()).isEqualTo("public-canary-2026.06");
        assertThat(properties.greeting()).contains("enterprise canary");

        StatusController.Status status = controller.status();
        assertThat(status.state()).isEqualTo("ok");
        assertThat(status.version()).isEqualTo("0.1.0");
        assertThat(status.platform()).isEqualTo("public-canary-2026.06");
    }
}
