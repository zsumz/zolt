package com.example.workspace.api;

import com.example.workspace.core.Greeting;

public final class ApiApplication {
    private ApiApplication() {
    }

    public static void main(String[] args) {
        String name = args.length == 0 ? "workspace" : args[0];
        System.out.println(Greeting.message(name));
    }
}
