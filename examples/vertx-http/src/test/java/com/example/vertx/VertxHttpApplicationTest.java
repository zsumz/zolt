package com.example.vertx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class VertxHttpApplicationTest {
    @Test
    void servesHelloEndpoint() throws Exception {
        Vertx vertx = Vertx.vertx();
        HttpServer server = vertx.createHttpServer()
                .requestHandler(VertxHttpApplication::handle)
                .listen(0, "127.0.0.1")
                .toCompletionStage()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
        try {
            Buffer body = vertx.createHttpClient()
                    .request(HttpMethod.GET, server.actualPort(), "127.0.0.1", "/hello")
                    .compose(request -> request.send()
                            .compose(response -> {
                                assertEquals(200, response.statusCode());
                                return response.body();
                            }))
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);

            assertEquals("Hello from Vert.x via Zolt!", body.toString());
        } finally {
            server.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
            vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
    }
}
