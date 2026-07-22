package sh.zolt.javac;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.tools.ToolProvider;

final class JavacWorkerServer {
    private static final long DEFAULT_IDLE_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(5);

    private JavacWorkerServer() {
    }

    static int run(Path statePath, PrintStream error) {
        return run(
                statePath.toAbsolutePath().normalize(),
                Long.getLong("zolt.javac.worker.idleTimeoutMillis", DEFAULT_IDLE_TIMEOUT_MILLIS),
                error);
    }

    static int run(Path statePath, long idleTimeoutMillis, PrintStream error) {
        return run(statePath, idleTimeoutMillis, error, () -> { });
    }

    static int run(
            Path statePath,
            long idleTimeoutMillis,
            PrintStream error,
            Runnable started) {
        if (ToolProvider.getSystemJavaCompiler() == null) {
            error.println("error: Zolt javac worker requires a JDK with the system Java compiler.");
            return 2;
        }
        String token = token();
        AtomicInteger activeRequests = new AtomicInteger();
        AtomicLong lastActivity = new AtomicLong(System.nanoTime());
        ExecutorService clients = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "zolt-javac-worker-client");
            thread.setDaemon(true);
            return thread;
        });
        try (ServerSocket server = new ServerSocket()) {
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            server.setSoTimeout(1_000);
            writeState(statePath, server.getLocalPort(), token);
            started.run();
            while (!idle(activeRequests, lastActivity, idleTimeoutMillis)) {
                try {
                    Socket socket = server.accept();
                    activeRequests.incrementAndGet();
                    lastActivity.set(System.nanoTime());
                    clients.execute(() -> handle(socket, token, activeRequests, lastActivity, error));
                } catch (SocketTimeoutException ignored) {
                    // Recheck the idle deadline.
                }
            }
            return 0;
        } catch (IOException exception) {
            error.println("error: Zolt javac worker server failed: " + exception.getMessage());
            return 1;
        } finally {
            clients.shutdown();
            try {
                clients.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            deleteOwnedState(statePath, token);
        }
    }

    private static void handle(
            Socket socket,
            String token,
            AtomicInteger activeRequests,
            AtomicLong lastActivity,
            PrintStream error) {
        try (socket;
                DataInputStream request = new DataInputStream(socket.getInputStream());
                DataOutputStream response = new DataOutputStream(socket.getOutputStream())) {
            if (request.readInt() != WorkerCompileProtocol.MAGIC
                    || request.readInt() != WorkerCompileProtocol.PROTOCOL_VERSION) {
                throw new IOException("invalid protocol header");
            }
            if (!token.equals(WorkerCompileProtocol.readString(request))) {
                throw new IOException("invalid authentication token");
            }
            int kind = WorkerCompileProtocol.readKind(request);
            List<String> arguments = WorkerCompileProtocol.readArguments(request);
            WorkerCompile.run(kind, arguments, response);
            response.flush();
        } catch (IOException exception) {
            error.println("error: Zolt javac worker request failed: " + exception.getMessage());
        } finally {
            lastActivity.set(System.nanoTime());
            activeRequests.decrementAndGet();
        }
    }

    private static boolean idle(
            AtomicInteger activeRequests,
            AtomicLong lastActivity,
            long idleTimeoutMillis) {
        return activeRequests.get() == 0
                && System.nanoTime() - lastActivity.get() >= TimeUnit.MILLISECONDS.toNanos(idleTimeoutMillis);
    }

    private static String token() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static void writeState(Path statePath, int port, String token) throws IOException {
        Files.createDirectories(statePath.getParent());
        Path temporary = statePath.resolveSibling(statePath.getFileName() + "." + ProcessHandle.current().pid() + ".tmp");
        Files.writeString(temporary, """
                version=%d
                port=%d
                token=%s
                pid=%d
                """.formatted(WorkerCompileProtocol.PROTOCOL_VERSION, port, token, ProcessHandle.current().pid()));
        restrictPermissions(temporary, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        try {
            Files.move(temporary, statePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, statePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteOwnedState(Path statePath, String token) {
        try {
            if (Files.isRegularFile(statePath) && Files.readString(statePath).contains("token=" + token + "\n")) {
                Files.deleteIfExists(statePath);
            }
        } catch (IOException ignored) {
            // A later client will replace stale state.
        }
    }

    private static void restrictPermissions(Path path, Set<PosixFilePermission> permissions) {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (IOException | UnsupportedOperationException ignored) {
            // Windows and other non-POSIX file systems use their native user permissions.
        }
    }
}
