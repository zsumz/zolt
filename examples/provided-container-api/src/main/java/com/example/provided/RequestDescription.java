package com.example.provided;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestDescription {
    private RequestDescription() {
    }

    public static String method(HttpServletRequest request) {
        return request.getMethod();
    }
}
