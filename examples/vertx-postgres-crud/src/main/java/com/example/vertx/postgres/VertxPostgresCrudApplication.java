package com.example.vertx.postgres;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class VertxPostgresCrudApplication {
    private static final int DEFAULT_HTTP_PORT = 18092;
    private static final String DEFAULT_NOTES_TABLE = "zolt_notes";
    private static final int MAX_TITLE_LENGTH = 120;
    private static final int MAX_BODY_LENGTH = 4_000;
    private static final long MAX_REQUEST_BODY_BYTES = 8_192;
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 10;

    private VertxPostgresCrudApplication() {
    }

    public static void main(String[] args) {
        AppConfig config;
        try {
            config = AppConfig.from(args, System.getenv());
        } catch (IllegalArgumentException exception) {
            System.err.println(exception.getMessage());
            System.exit(2);
            return;
        }

        Vertx vertx = Vertx.vertx();
        PgPool pool = PgPool.pool(vertx, config.pgConnectOptions(), new PoolOptions().setMaxSize(5));
        PgNotesRepository repository = new PgNotesRepository(pool, config.pgNotesTable());
        AppLifecycle lifecycle = new AppLifecycle(vertx, pool);
        Runtime.getRuntime().addShutdownHook(lifecycle.shutdownHook());
        startInitialized(vertx, repository, config.httpPort())
                .onSuccess(server -> {
                    lifecycle.server(server);
                    System.out.println("Vert.x PostgreSQL CRUD API listening on " + server.actualPort());
                })
                .onFailure(error -> {
                    error.printStackTrace(System.err);
                    lifecycle.closeBlocking();
                    System.exit(1);
                });
    }

    static Future<HttpServer> startInitialized(Vertx vertx, NotesRepository repository, int port) {
        return repository.init()
                .compose(ignored -> start(vertx, repository, port));
    }

    static Future<HttpServer> start(Vertx vertx, NotesRepository repository, int port) {
        return vertx.createHttpServer()
                .requestHandler(router(vertx, repository))
                .listen(port, "127.0.0.1");
    }

    static Router router(Vertx vertx, NotesRepository repository) {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create().setBodyLimit(MAX_REQUEST_BODY_BYTES));
        router.route().failureHandler(VertxPostgresCrudApplication::routeFailure);
        router.get("/health").handler(VertxPostgresCrudApplication::health);
        router.route("/health").handler(context -> methodNotAllowed(context, "GET"));
        router.get("/ready").handler(context -> ready(context, repository));
        router.route("/ready").handler(context -> methodNotAllowed(context, "GET"));
        router.post("/notes").handler(context -> createNote(context, repository));
        router.get("/notes").handler(context -> listNotes(context, repository));
        router.route("/notes").handler(context -> methodNotAllowed(context, "GET, POST"));
        router.get("/notes/:id").handler(context -> findNote(context, repository));
        router.put("/notes/:id").handler(context -> updateNote(context, repository));
        router.delete("/notes/:id").handler(context -> deleteNote(context, repository));
        router.route("/notes/:id").handler(context -> methodNotAllowed(context, "DELETE, GET, PUT"));
        router.route().handler(VertxPostgresCrudApplication::unknownRoute);
        return router;
    }

    private static void health(RoutingContext context) {
        json(context, 200, new JsonObject().put("status", "ok"));
    }

    private static void ready(RoutingContext context, NotesRepository repository) {
        repository.ready()
                .onSuccess(ignored -> json(context, 200, new JsonObject().put("status", "ready")))
                .onFailure(error -> readinessFailure(context, error));
    }

    private static void createNote(RoutingContext context, NotesRepository repository) {
        Optional<NoteInput> input = noteInput(context);
        if (input.isEmpty()) {
            return;
        }
        NoteInput noteInput = input.orElseThrow();
        repository.create(noteInput.title(), noteInput.body())
                .onSuccess(note -> {
                    context.response().putHeader("location", "/notes/" + note.id());
                    json(context, 201, note.toJson());
                })
                .onFailure(error -> serverError(context, error));
    }

    private static void listNotes(RoutingContext context, NotesRepository repository) {
        repository.list()
                .onSuccess(notes -> {
                    JsonArray body = new JsonArray();
                    for (Note note : notes) {
                        body.add(note.toJson());
                    }
                    json(context, 200, body);
                })
                .onFailure(error -> serverError(context, error));
    }

    private static void findNote(RoutingContext context, NotesRepository repository) {
        Optional<Long> id = noteId(context);
        if (id.isEmpty()) {
            return;
        }
        repository.find(id.orElseThrow())
                .onSuccess(note -> note.ifPresentOrElse(
                        value -> json(context, 200, value.toJson()),
                        () -> notFound(context, id.orElseThrow())))
                .onFailure(error -> serverError(context, error));
    }

    private static void updateNote(RoutingContext context, NotesRepository repository) {
        Optional<Long> id = noteId(context);
        if (id.isEmpty()) {
            return;
        }
        Optional<NoteInput> input = noteInput(context);
        if (input.isEmpty()) {
            return;
        }
        NoteInput noteInput = input.orElseThrow();
        repository.update(id.orElseThrow(), noteInput.title(), noteInput.body())
                .onSuccess(note -> note.ifPresentOrElse(
                        value -> json(context, 200, value.toJson()),
                        () -> notFound(context, id.orElseThrow())))
                .onFailure(error -> serverError(context, error));
    }

    private static void deleteNote(RoutingContext context, NotesRepository repository) {
        Optional<Long> id = noteId(context);
        if (id.isEmpty()) {
            return;
        }
        repository.delete(id.orElseThrow())
                .onSuccess(deleted -> {
                    if (deleted) {
                        context.response().setStatusCode(204).end();
                    } else {
                        notFound(context, id.orElseThrow());
                    }
                })
                .onFailure(error -> serverError(context, error));
    }

    private static Optional<Long> noteId(RoutingContext context) {
        String value = context.pathParam("id");
        try {
            long id = Long.parseLong(value);
            if (id < 1) {
                badRequest(context, "note id must be a positive integer");
                return Optional.empty();
            }
            return Optional.of(id);
        } catch (NumberFormatException exception) {
            badRequest(context, "note id must be a positive integer");
            return Optional.empty();
        }
    }

    private static Optional<NoteInput> noteInput(RoutingContext context) {
        if (!hasJsonContentType(context)) {
            unsupportedMediaType(context, "content-type must be application/json");
            return Optional.empty();
        }
        try {
            return Optional.of(NoteInput.from(context.getBodyAsJson()));
        } catch (DecodeException | ClassCastException exception) {
            badRequest(context, "request body must be a JSON object");
            return Optional.empty();
        } catch (IllegalArgumentException exception) {
            badRequest(context, exception.getMessage());
            return Optional.empty();
        }
    }

    private static boolean hasJsonContentType(RoutingContext context) {
        String contentType = context.request().getHeader("content-type");
        if (contentType == null) {
            return false;
        }
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT).equals("application/json");
    }

    private static void json(RoutingContext context, int status, JsonObject body) {
        context.response()
                .setStatusCode(status)
                .putHeader("content-type", "application/json")
                .putHeader("cache-control", "no-store")
                .end(body.encode());
    }

    private static void json(RoutingContext context, int status, JsonArray body) {
        context.response()
                .setStatusCode(status)
                .putHeader("content-type", "application/json")
                .putHeader("cache-control", "no-store")
                .end(body.encode());
    }

    private static void badRequest(RoutingContext context, String message) {
        json(context, 400, new JsonObject().put("error", message));
    }

    private static void unsupportedMediaType(RoutingContext context, String message) {
        json(context, 415, new JsonObject().put("error", message));
    }

    private static void notFound(RoutingContext context, long id) {
        json(context, 404, new JsonObject().put("error", "note " + id + " was not found"));
    }

    private static void unknownRoute(RoutingContext context) {
        json(context, 404, new JsonObject().put("error", "route was not found"));
    }

    private static void methodNotAllowed(RoutingContext context, String allow) {
        context.response().putHeader("allow", allow);
        json(context, 405, new JsonObject().put("error", "method not allowed"));
    }

    private static void routeFailure(RoutingContext context) {
        if (context.statusCode() == 413) {
            json(context, 413, new JsonObject().put(
                    "error",
                    "request body must be at most " + MAX_REQUEST_BODY_BYTES + " bytes"));
            return;
        }
        Throwable failure = context.failure();
        if (failure == null) {
            json(context, 500, new JsonObject().put("error", "request failed"));
        } else if (failure instanceof DecodeException || failure instanceof ClassCastException) {
            badRequest(context, "request body must be a JSON object");
        } else {
            serverError(context, failure);
        }
    }

    private static void serverError(RoutingContext context, Throwable error) {
        json(context, 500, new JsonObject().put("error", "database operation failed: " + error.getMessage()));
    }

    private static void readinessFailure(RoutingContext context, Throwable error) {
        json(context, 503, new JsonObject().put("error", "database readiness check failed: " + error.getMessage()));
    }

    static final class AppLifecycle {
        private final Vertx vertx;
        private final PgPool pool;
        private final AtomicBoolean closing = new AtomicBoolean();
        private volatile HttpServer server;

        AppLifecycle(Vertx vertx, PgPool pool) {
            this.vertx = vertx;
            this.pool = pool;
        }

        void server(HttpServer server) {
            this.server = server;
        }

        Thread shutdownHook() {
            return new Thread(this::closeBlocking, "vertx-postgres-crud-shutdown");
        }

        Future<Void> close() {
            if (!closing.compareAndSet(false, true)) {
                return Future.succeededFuture();
            }
            return closeServer()
                    .compose(ignored -> closePool())
                    .compose(ignored -> vertx.close());
        }

        void closeBlocking() {
            try {
                close().toCompletionStage().toCompletableFuture().get(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                System.err.println("Vert.x PostgreSQL shutdown was interrupted.");
            } catch (ExecutionException | TimeoutException exception) {
                System.err.println("Vert.x PostgreSQL shutdown did not complete cleanly: " + exception.getMessage());
            }
        }

        private Future<Void> closeServer() {
            HttpServer currentServer = server;
            if (currentServer == null) {
                return Future.succeededFuture();
            }
            return currentServer.close();
        }

        private Future<Void> closePool() {
            Promise<Void> promise = Promise.promise();
            pool.close(result -> {
                if (result.succeeded()) {
                    promise.complete();
                } else {
                    promise.fail(result.cause());
                }
            });
            return promise.future();
        }
    }

    record AppConfig(
            int httpPort,
            String pgHost,
            int pgPort,
            String pgDatabase,
            String pgUser,
            String pgPassword,
            String pgNotesTable) {
        static AppConfig from(String[] args, Map<String, String> env) {
            int httpPort = parsePort("PORT", valueOrDefault(portArg(args), env.get("PORT"), Integer.toString(DEFAULT_HTTP_PORT)));
            String pgHost = required(env, "PGHOST");
            int pgPort = parsePort("PGPORT", required(env, "PGPORT"));
            String pgDatabase = required(env, "PGDATABASE");
            String pgUser = required(env, "PGUSER");
            String pgPassword = required(env, "PGPASSWORD");
            String pgNotesTable = parseSqlIdentifier(
                    "PGNOTES_TABLE",
                    valueOrDefault(env.get("PGNOTES_TABLE"), null, DEFAULT_NOTES_TABLE));
            return new AppConfig(httpPort, pgHost, pgPort, pgDatabase, pgUser, pgPassword, pgNotesTable);
        }

        PgConnectOptions pgConnectOptions() {
            return new PgConnectOptions()
                    .setHost(pgHost)
                    .setPort(pgPort)
                    .setDatabase(pgDatabase)
                    .setUser(pgUser)
                    .setPassword(pgPassword);
        }

        private static String portArg(String[] args) {
            String port = null;
            for (String arg : args) {
                if (arg.startsWith("--port=")) {
                    if (port != null) {
                        throw new IllegalArgumentException("Duplicate argument --port. Use --port=<port> once.");
                    }
                    port = arg.substring("--port=".length());
                    continue;
                }
                throw new IllegalArgumentException("Unsupported argument " + arg + ". Use --port=<port>.");
            }
            return port;
        }

        private static String required(Map<String, String> env, String key) {
            String value = env.get(key);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing required PostgreSQL setting " + key
                        + ". Set PGHOST, PGPORT, PGDATABASE, PGUSER, and PGPASSWORD. See examples/vertx-postgres-crud/README.md.");
            }
            return value;
        }

        private static int parsePort(String name, String value) {
            try {
                int port = Integer.parseInt(value);
                if (port < 1 || port > 65535) {
                    throw new IllegalArgumentException(name + " must be between 1 and 65535");
                }
                return port;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(name + " must be between 1 and 65535", exception);
            }
        }

        private static String parseSqlIdentifier(String name, String value) {
            if (!value.matches("[a-z][a-z0-9_]{0,62}")) {
                throw new IllegalArgumentException(name
                        + " must be a lowercase PostgreSQL identifier up to 63 characters");
            }
            return value;
        }

        private static String valueOrDefault(String primary, String fallback, String defaultValue) {
            if (primary != null && !primary.isBlank()) {
                return primary;
            }
            if (fallback != null && !fallback.isBlank()) {
                return fallback;
            }
            return defaultValue;
        }
    }

    record Note(long id, String title, String body) {
        JsonObject toJson() {
            return new JsonObject()
                    .put("id", id)
                    .put("title", title)
                    .put("body", body);
        }

        static Note from(Row row) {
            return new Note(
                    row.getLong("id"),
                    row.getString("title"),
                    row.getString("body"));
        }
    }

    record NoteInput(String title, String body) {
        static NoteInput from(JsonObject json) {
            if (json == null) {
                throw new IllegalArgumentException("request body must be a JSON object");
            }
            for (String field : json.fieldNames()) {
                if (!field.equals("title") && !field.equals("body")) {
                    throw new IllegalArgumentException("request body may only contain title and body");
                }
            }
            String title = stringField(json, "title", MAX_TITLE_LENGTH);
            String body = stringField(json, "body", MAX_BODY_LENGTH);
            return new NoteInput(title, body);
        }

        private static String stringField(JsonObject json, String field, int maxLength) {
            Object value = json.getValue(field);
            if (!(value instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException(field + " must be a non-empty string");
            }
            String trimmed = text.trim();
            if (trimmed.length() > maxLength) {
                throw new IllegalArgumentException(field + " must be at most " + maxLength + " characters");
            }
            return trimmed;
        }
    }

    interface NotesRepository {
        Future<Void> init();

        Future<Void> ready();

        Future<Note> create(String title, String body);

        Future<List<Note>> list();

        Future<Optional<Note>> find(long id);

        Future<Optional<Note>> update(long id, String title, String body);

        Future<Boolean> delete(long id);
    }

    static final class PgNotesRepository implements NotesRepository {
        private static final List<SchemaColumn> EXPECTED_SCHEMA = List.of(
                new SchemaColumn("id", "bigint", "NO"),
                new SchemaColumn("title", "text", "NO"),
                new SchemaColumn("body", "text", "NO"));

        private final PgPool pool;
        private final String tableName;

        PgNotesRepository(PgPool pool, String tableName) {
            this.pool = pool;
            this.tableName = tableName;
        }

        @Override
        public Future<Void> init() {
            return pool.query("""
                    CREATE TABLE IF NOT EXISTS %s (
                      id BIGSERIAL PRIMARY KEY,
                      title TEXT NOT NULL,
                      body TEXT NOT NULL
                    )
                    """.formatted(tableName))
                    .execute()
                    .compose(ignored -> validateSchema());
        }

        private Future<Void> validateSchema() {
            return pool.preparedQuery("""
                    SELECT column_name, data_type, is_nullable
                    FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = $1
                      AND column_name IN ('id', 'title', 'body')
                    """)
                    .execute(Tuple.of(tableName))
                    .map(rows -> {
                        List<SchemaColumn> columns = new ArrayList<>();
                        for (Row row : rows) {
                            columns.add(new SchemaColumn(
                                    row.getString("column_name"),
                                    row.getString("data_type"),
                                    row.getString("is_nullable")));
                        }
                        validateSchemaColumns(tableName, columns);
                        return null;
                    });
        }

        static void validateSchemaColumns(String tableName, List<SchemaColumn> columns) {
            Map<String, SchemaColumn> actualByName = new LinkedHashMap<>();
            for (SchemaColumn column : columns) {
                actualByName.put(column.name(), column);
            }

            List<String> mismatches = new ArrayList<>();
            for (SchemaColumn expected : EXPECTED_SCHEMA) {
                SchemaColumn actual = actualByName.get(expected.name());
                if (actual == null) {
                    mismatches.add("missing column " + expected.name());
                    continue;
                }
                if (!expected.dataType().equals(actual.dataType())) {
                    mismatches.add("column " + expected.name()
                            + " expected type " + expected.dataType()
                            + " but found " + actual.dataType());
                }
                if (!expected.isNullable().equals(actual.isNullable())) {
                    mismatches.add("column " + expected.name()
                            + " expected " + nullability(expected.isNullable())
                            + " but found " + nullability(actual.isNullable()));
                }
            }
            if (!mismatches.isEmpty()) {
                throw new IllegalStateException("PGNOTES_TABLE " + tableName
                        + " has incompatible schema: " + String.join("; ", mismatches)
                        + ". Choose a clean table or fix the schema.");
            }
        }

        private static String nullability(String value) {
            return switch (value) {
                case "NO" -> "NOT NULL";
                case "YES" -> "nullable";
                default -> value;
            };
        }

        @Override
        public Future<Void> ready() {
            return pool.query("SELECT 1").execute().mapEmpty();
        }

        @Override
        public Future<Note> create(String title, String body) {
            return pool.preparedQuery("INSERT INTO " + tableName + " (title, body) VALUES ($1, $2) RETURNING id, title, body")
                    .execute(Tuple.of(title, body))
                    .map(rows -> Note.from(rows.iterator().next()));
        }

        @Override
        public Future<List<Note>> list() {
            return pool.query("SELECT id, title, body FROM " + tableName + " ORDER BY id")
                    .execute()
                    .map(rows -> {
                        List<Note> notes = new ArrayList<>();
                        for (Row row : rows) {
                            notes.add(Note.from(row));
                        }
                        return notes;
                    });
        }

        @Override
        public Future<Optional<Note>> find(long id) {
            return pool.preparedQuery("SELECT id, title, body FROM " + tableName + " WHERE id = $1")
                    .execute(Tuple.of(id))
                    .map(rows -> rows.iterator().hasNext()
                            ? Optional.of(Note.from(rows.iterator().next()))
                            : Optional.empty());
        }

        @Override
        public Future<Optional<Note>> update(long id, String title, String body) {
            return pool.preparedQuery("UPDATE " + tableName + " SET title = $1, body = $2 WHERE id = $3 RETURNING id, title, body")
                    .execute(Tuple.of(title, body, id))
                    .map(rows -> rows.iterator().hasNext()
                            ? Optional.of(Note.from(rows.iterator().next()))
                            : Optional.empty());
        }

        @Override
        public Future<Boolean> delete(long id) {
            return pool.preparedQuery("DELETE FROM " + tableName + " WHERE id = $1")
                    .execute(Tuple.of(id))
                    .map(rows -> rows.rowCount() > 0);
        }

        record SchemaColumn(String name, String dataType, String isNullable) {
        }
    }
}
