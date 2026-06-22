package com.example.vertx.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

final class VertxPostgresCrudApplicationTest {
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @Test
    void parsesPostgresAndHttpConfiguration() {
        VertxPostgresCrudApplication.AppConfig config = VertxPostgresCrudApplication.AppConfig.from(
                new String[] {"--port=18100"},
                Map.of(
                        "PGHOST", "127.0.0.1",
                        "PGPORT", "15432",
                        "PGDATABASE", "zolt_vertx",
                        "PGUSER", "zolt",
                        "PGPASSWORD", "secret",
                        "PGNOTES_TABLE", "zolt_notes_smoke"));

        assertEquals(18100, config.httpPort());
        assertEquals("127.0.0.1", config.pgHost());
        assertEquals(15432, config.pgPort());
        assertEquals("zolt_vertx", config.pgDatabase());
        assertEquals("zolt", config.pgUser());
        assertEquals("secret", config.pgPassword());
        assertEquals("zolt_notes_smoke", config.pgNotesTable());
    }

    @Test
    void reportsMissingPostgresSetting() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                VertxPostgresCrudApplication.AppConfig.from(
                        new String[0],
                        Map.of(
                                "PGPORT", "5432",
                                "PGDATABASE", "zolt_vertx",
                                "PGUSER", "zolt",
                                "PGPASSWORD", "secret")));

        assertTrue(exception.getMessage().contains("Missing required PostgreSQL setting PGHOST"));
        assertTrue(exception.getMessage().contains("examples/vertx-postgres-crud/README.md"));
    }

    @Test
    void rejectsInvalidPort() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                VertxPostgresCrudApplication.AppConfig.from(
                        new String[] {"--port=0"},
                        Map.of(
                                "PGHOST", "127.0.0.1",
                                "PGPORT", "5432",
                                "PGDATABASE", "zolt_vertx",
                                "PGUSER", "zolt",
                                "PGPASSWORD", "secret")));

        assertEquals("PORT must be between 1 and 65535", exception.getMessage());
    }

    @Test
    void rejectsUnsupportedArgument() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                VertxPostgresCrudApplication.AppConfig.from(
                        new String[] {"--por=18100"},
                        Map.of(
                                "PGHOST", "127.0.0.1",
                                "PGPORT", "5432",
                                "PGDATABASE", "zolt_vertx",
                                "PGUSER", "zolt",
                                "PGPASSWORD", "secret")));

        assertEquals("Unsupported argument --por=18100. Use --port=<port>.", exception.getMessage());
    }

    @Test
    void defaultsNotesTable() {
        VertxPostgresCrudApplication.AppConfig config = VertxPostgresCrudApplication.AppConfig.from(
                new String[0],
                Map.of(
                        "PGHOST", "127.0.0.1",
                        "PGPORT", "15432",
                        "PGDATABASE", "zolt_vertx",
                        "PGUSER", "zolt",
                        "PGPASSWORD", "secret"));

        assertEquals("zolt_notes", config.pgNotesTable());
    }

    @Test
    void rejectsUnsafeNotesTable() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                VertxPostgresCrudApplication.AppConfig.from(
                        new String[0],
                        Map.of(
                                "PGHOST", "127.0.0.1",
                                "PGPORT", "15432",
                                "PGDATABASE", "zolt_vertx",
                                "PGUSER", "zolt",
                                "PGPASSWORD", "secret",
                                "PGNOTES_TABLE", "zolt-notes")));

        assertEquals(
                "PGNOTES_TABLE must be a lowercase PostgreSQL identifier up to 63 characters",
                exception.getMessage());
    }

    @Test
    void trimsNoteInput() {
        VertxPostgresCrudApplication.NoteInput input = VertxPostgresCrudApplication.NoteInput.from(
                new JsonObject().put("title", "  first note  ").put("body", "  hello  "));

        assertEquals("first note", input.title());
        assertEquals("hello", input.body());
    }

    @Test
    void rejectsMalformedNoteInput() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                VertxPostgresCrudApplication.NoteInput.from(new JsonObject().put("title", "ok")));

        assertEquals("body must be a non-empty string", exception.getMessage());
    }

    @Test
    void serializesNoteResponse() {
        JsonObject json = new VertxPostgresCrudApplication.Note(42, "first", "hello").toJson();

        assertEquals(42L, json.getLong("id"));
        assertEquals("first", json.getString("title"));
        assertEquals("hello", json.getString("body"));
    }

    @Test
    void servesCrudRoutesThroughRepository() throws Exception {
        withServer(new FakeNotesRepository(), server -> {
            HttpResult health = request("GET", server.port(), "/health", null);
            assertEquals(200, health.status());
            assertJson(health);
            assertTrue(health.body().contains("\"status\":\"ok\""));

            HttpResult created = request(
                    "POST",
                    server.port(),
                    "/notes",
                    "{\"title\":\"first note\",\"body\":\"hello\"}");
            assertEquals(201, created.status());
            assertJson(created);
            assertTrue(created.body().contains("\"id\":1"));
            assertTrue(created.body().contains("\"title\":\"first note\""));
            assertTrue(created.body().contains("\"body\":\"hello\""));

            HttpResult listed = request("GET", server.port(), "/notes", null);
            assertEquals(200, listed.status());
            assertJson(listed);
            assertTrue(listed.body().contains("\"id\":1"));

            HttpResult found = request("GET", server.port(), "/notes/1", null);
            assertEquals(200, found.status());
            assertJson(found);
            assertTrue(found.body().contains("\"title\":\"first note\""));

            HttpResult updated = request(
                    "PUT",
                    server.port(),
                    "/notes/1",
                    "{\"title\":\"renamed\",\"body\":\"updated body\"}");
            assertEquals(200, updated.status());
            assertJson(updated);
            assertTrue(updated.body().contains("\"title\":\"renamed\""));
            assertTrue(updated.body().contains("\"body\":\"updated body\""));

            HttpResult updatedRead = request("GET", server.port(), "/notes/1", null);
            assertEquals(200, updatedRead.status());
            assertJson(updatedRead);
            assertTrue(updatedRead.body().contains("\"title\":\"renamed\""));
            assertTrue(updatedRead.body().contains("\"body\":\"updated body\""));

            HttpResult missingUpdate = request(
                    "PUT",
                    server.port(),
                    "/notes/999",
                    "{\"title\":\"missing\",\"body\":\"not found\"}");
            assertEquals(404, missingUpdate.status());
            assertJson(missingUpdate);
            assertTrue(missingUpdate.body().contains("note 999 was not found"));

            HttpResult missingDelete = request("DELETE", server.port(), "/notes/999", null);
            assertEquals(404, missingDelete.status());
            assertJson(missingDelete);
            assertTrue(missingDelete.body().contains("note 999 was not found"));

            HttpResult deleted = request("DELETE", server.port(), "/notes/1", null);
            assertEquals(204, deleted.status());

            HttpResult missing = request("GET", server.port(), "/notes/1", null);
            assertEquals(404, missing.status());
            assertJson(missing);
            assertTrue(missing.body().contains("note 1 was not found"));

            HttpResult listedAfterDelete = request("GET", server.port(), "/notes", null);
            assertEquals(200, listedAfterDelete.status());
            assertJson(listedAfterDelete);
            assertTrue(listedAfterDelete.body().contains("[]"));
        });
    }

    @Test
    void reportsRouteValidationErrors() throws Exception {
        withServer(new FakeNotesRepository(), server -> {
            HttpResult malformedBody = request(
                    "POST",
                    server.port(),
                    "/notes",
                    "{\"title\":\"missing body\"}");
            assertEquals(400, malformedBody.status());
            assertJson(malformedBody);
            assertTrue(malformedBody.body().contains("body must be a non-empty string"));

            HttpResult invalidJson = request(
                    "POST",
                    server.port(),
                    "/notes",
                    "{not-json");
            assertEquals(400, invalidJson.status());
            assertJson(invalidJson);
            assertTrue(invalidJson.body().contains("request body must be a JSON object"));

            HttpResult malformedUpdate = request(
                    "PUT",
                    server.port(),
                    "/notes/1",
                    "{\"title\":\"missing body\"}");
            assertEquals(400, malformedUpdate.status());
            assertJson(malformedUpdate);
            assertTrue(malformedUpdate.body().contains("body must be a non-empty string"));

            HttpResult invalidJsonUpdate = request(
                    "PUT",
                    server.port(),
                    "/notes/1",
                    "{not-json");
            assertEquals(400, invalidJsonUpdate.status());
            assertJson(invalidJsonUpdate);
            assertTrue(invalidJsonUpdate.body().contains("request body must be a JSON object"));

            HttpResult badId = request("GET", server.port(), "/notes/not-a-number", null);
            assertEquals(400, badId.status());
            assertJson(badId);
            assertTrue(badId.body().contains("note id must be a positive integer"));

            HttpResult zeroReadId = request("GET", server.port(), "/notes/0", null);
            assertEquals(400, zeroReadId.status());
            assertJson(zeroReadId);
            assertTrue(zeroReadId.body().contains("note id must be a positive integer"));

            HttpResult zeroUpdateId = request(
                    "PUT",
                    server.port(),
                    "/notes/0",
                    "{\"title\":\"zero\",\"body\":\"invalid\"}");
            assertEquals(400, zeroUpdateId.status());
            assertJson(zeroUpdateId);
            assertTrue(zeroUpdateId.body().contains("note id must be a positive integer"));

            HttpResult zeroDeleteId = request("DELETE", server.port(), "/notes/0", null);
            assertEquals(400, zeroDeleteId.status());
            assertJson(zeroDeleteId);
            assertTrue(zeroDeleteId.body().contains("note id must be a positive integer"));

            HttpResult negativeReadId = request("GET", server.port(), "/notes/-1", null);
            assertEquals(400, negativeReadId.status());
            assertJson(negativeReadId);
            assertTrue(negativeReadId.body().contains("note id must be a positive integer"));

            HttpResult negativeUpdateId = request(
                    "PUT",
                    server.port(),
                    "/notes/-1",
                    "{\"title\":\"negative\",\"body\":\"invalid\"}");
            assertEquals(400, negativeUpdateId.status());
            assertJson(negativeUpdateId);
            assertTrue(negativeUpdateId.body().contains("note id must be a positive integer"));

            HttpResult negativeDeleteId = request("DELETE", server.port(), "/notes/-1", null);
            assertEquals(400, negativeDeleteId.status());
            assertJson(negativeDeleteId);
            assertTrue(negativeDeleteId.body().contains("note id must be a positive integer"));
        });
    }

    @Test
    void reportsRepositoryFailuresAsJsonErrors() throws Exception {
        withServer(new FailingNotesRepository(), server -> {
            assertRepositoryFailure(request(
                    "POST",
                    server.port(),
                    "/notes",
                    "{\"title\":\"first note\",\"body\":\"hello\"}"));
            assertRepositoryFailure(request("GET", server.port(), "/notes", null));
            assertRepositoryFailure(request("GET", server.port(), "/notes/1", null));
            assertRepositoryFailure(request(
                    "PUT",
                    server.port(),
                    "/notes/1",
                    "{\"title\":\"renamed\",\"body\":\"updated body\"}"));
            assertRepositoryFailure(request("DELETE", server.port(), "/notes/1", null));
        });
    }

    @Test
    void failsBeforeListeningWhenRepositoryInitializationFails() throws Exception {
        Vertx vertx = Vertx.vertx();
        try {
            ExecutionException exception = assertThrows(ExecutionException.class, () ->
                    VertxPostgresCrudApplication.startInitialized(vertx, new FailingInitNotesRepository(), 0)
                            .toCompletionStage()
                            .toCompletableFuture()
                            .get(10, TimeUnit.SECONDS));

            assertTrue(exception.getCause().getMessage().contains("simulated init failure"));
        } finally {
            vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
    }

    private static void assertRepositoryFailure(HttpResult response) {
        assertEquals(500, response.status());
        assertJson(response);
        assertTrue(response.body().contains("database operation failed: simulated repository failure"));
    }

    private static void assertJson(HttpResult response) {
        assertTrue(
                response.contentType().contains("application/json"),
                "expected application/json response but got " + response.contentType());
    }

    private static void withServer(VertxPostgresCrudApplication.NotesRepository repository, ServerExercise exercise) throws Exception {
        Vertx vertx = Vertx.vertx();
        HttpServer server = null;
        try {
            try {
                server = VertxPostgresCrudApplication.start(vertx, repository, 0)
                        .toCompletionStage()
                        .toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);
            } catch (ExecutionException exception) {
                Assumptions.assumeTrue(false, "loopback sockets unavailable: " + exception.getCause());
                return;
            }
            exercise.run(new ServerContext(server.actualPort()));
        } finally {
            if (server != null) {
                server.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
            }
            vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
    }

    private static HttpResult request(String method, int port, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10));
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("content-type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        HttpResponse<String> response = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new HttpResult(
                response.statusCode(),
                response.body(),
                response.headers().firstValue("content-type").orElse(""));
    }

    private record ServerContext(int port) {
    }

    private record HttpResult(int status, String body, String contentType) {
    }

    @FunctionalInterface
    private interface ServerExercise {
        void run(ServerContext server) throws Exception;
    }

    private static class FakeNotesRepository implements VertxPostgresCrudApplication.NotesRepository {
        private final Map<Long, VertxPostgresCrudApplication.Note> notes = new LinkedHashMap<>();
        private long nextId = 1;

        @Override
        public Future<Void> init() {
            return Future.succeededFuture();
        }

        @Override
        public Future<VertxPostgresCrudApplication.Note> create(String title, String body) {
            VertxPostgresCrudApplication.Note note = new VertxPostgresCrudApplication.Note(nextId++, title, body);
            notes.put(note.id(), note);
            return Future.succeededFuture(note);
        }

        @Override
        public Future<List<VertxPostgresCrudApplication.Note>> list() {
            return Future.succeededFuture(new ArrayList<>(notes.values()));
        }

        @Override
        public Future<Optional<VertxPostgresCrudApplication.Note>> find(long id) {
            return Future.succeededFuture(Optional.ofNullable(notes.get(id)));
        }

        @Override
        public Future<Optional<VertxPostgresCrudApplication.Note>> update(long id, String title, String body) {
            if (!notes.containsKey(id)) {
                return Future.succeededFuture(Optional.empty());
            }
            VertxPostgresCrudApplication.Note note = new VertxPostgresCrudApplication.Note(id, title, body);
            notes.put(id, note);
            return Future.succeededFuture(Optional.of(note));
        }

        @Override
        public Future<Boolean> delete(long id) {
            return Future.succeededFuture(notes.remove(id) != null);
        }
    }

    private static final class FailingInitNotesRepository extends FakeNotesRepository {
        @Override
        public Future<Void> init() {
            return Future.failedFuture(new IllegalStateException("simulated init failure"));
        }
    }

    private static final class FailingNotesRepository implements VertxPostgresCrudApplication.NotesRepository {
        @Override
        public Future<Void> init() {
            return Future.succeededFuture();
        }

        @Override
        public Future<VertxPostgresCrudApplication.Note> create(String title, String body) {
            return failed();
        }

        @Override
        public Future<List<VertxPostgresCrudApplication.Note>> list() {
            return failed();
        }

        @Override
        public Future<Optional<VertxPostgresCrudApplication.Note>> find(long id) {
            return failed();
        }

        @Override
        public Future<Optional<VertxPostgresCrudApplication.Note>> update(long id, String title, String body) {
            return failed();
        }

        @Override
        public Future<Boolean> delete(long id) {
            return failed();
        }

        private static <T> Future<T> failed() {
            return Future.failedFuture(new IllegalStateException("simulated repository failure"));
        }
    }
}
