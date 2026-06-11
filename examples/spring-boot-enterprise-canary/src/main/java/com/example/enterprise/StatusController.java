package com.example.enterprise;

import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class StatusController {
    private final CanaryProperties properties;

    public StatusController(CanaryProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/status")
    Status status() {
        return new Status(
                "ok",
                properties.version(),
                properties.platform(),
                properties.greeting());
    }

    public record Status(
            @NotBlank String state,
            @NotBlank String version,
            @NotBlank String platform,
            @NotBlank String greeting) {
    }
}
