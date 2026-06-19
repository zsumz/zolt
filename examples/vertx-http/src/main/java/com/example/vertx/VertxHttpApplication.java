package com.example.vertx;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;

public final class VertxHttpApplication {
    private static final int DEFAULT_PORT = 18091;

    private VertxHttpApplication() {
    }

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        int port = port(args);
        vertx.createHttpServer()
                .requestHandler(VertxHttpApplication::handle)
                .listen(port, "127.0.0.1")
                .onSuccess(server -> System.out.println("Vert.x HTTP server listening on " + server.actualPort()))
                .onFailure(error -> {
                    error.printStackTrace(System.err);
                    vertx.close();
                    System.exit(1);
                });
    }

    static void handle(HttpServerRequest request) {
        if ("/hello".equals(request.path())) {
            request.response()
                    .putHeader("content-type", "text/plain")
                    .end("Hello from Vert.x via Zolt!");
            return;
        }
        if ("/health".equals(request.path())) {
            request.response()
                    .putHeader("content-type", "text/plain")
                    .end("ok");
            return;
        }
        request.response()
                .setStatusCode(404)
                .putHeader("content-type", "text/plain")
                .end("not found");
    }

    private static int port(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--port=")) {
                return Integer.parseInt(arg.substring("--port=".length()));
            }
        }
        String configured = System.getenv("ZOLT_VERTX_HTTP_PORT");
        if (configured == null || configured.isBlank()) {
            configured = System.getProperty("zolt.vertx.http.port");
        }
        if (configured == null || configured.isBlank()) {
            return DEFAULT_PORT;
        }
        return Integer.parseInt(configured);
    }
}
