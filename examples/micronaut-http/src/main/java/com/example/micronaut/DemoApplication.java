package com.example.micronaut;

import io.micronaut.runtime.Micronaut;

public final class DemoApplication {
    private DemoApplication() {
    }

    public static void main(String[] args) {
        Micronaut.run(DemoApplication.class, args);
    }
}
