package com.acme.app;

import com.acme.http.Http;

/** Consumes {@code acme-http}, whose version is supplied by the imported {@code acme-bom} platform. */
public final class Main {
    public static void main(String[] args) {
        System.out.println(new Http().describe());
    }
}
