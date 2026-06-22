package com.example.vertx.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.json.JsonObject;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class VertxPostgresCrudApplicationTest {
    @Test
    void parsesPostgresAndHttpConfiguration() {
        VertxPostgresCrudApplication.AppConfig config = VertxPostgresCrudApplication.AppConfig.from(
                new String[] {"--port=18100"},
                Map.of(
                        "PGHOST", "127.0.0.1",
                        "PGPORT", "15432",
                        "PGDATABASE", "zolt_vertx",
                        "PGUSER", "zolt",
                        "PGPASSWORD", "secret"));

        assertEquals(18100, config.httpPort());
        assertEquals("127.0.0.1", config.pgHost());
        assertEquals(15432, config.pgPort());
        assertEquals("zolt_vertx", config.pgDatabase());
        assertEquals("zolt", config.pgUser());
        assertEquals("secret", config.pgPassword());
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
}
