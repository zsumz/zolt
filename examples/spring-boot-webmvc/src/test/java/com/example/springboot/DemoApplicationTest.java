package com.example.springboot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        classes = DemoApplication.class,
        args = "--spring.main.web-application-type=none")
final class DemoApplicationTest {
    @Test
    void contextLoads() {
    }
}
