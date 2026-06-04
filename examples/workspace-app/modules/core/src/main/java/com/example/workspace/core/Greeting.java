package com.example.workspace.core;

public final class Greeting {
    private Greeting() {
    }

    public static String message(String name) {
        return "Hello, " + name + ", from workspace core!";
    }
}
