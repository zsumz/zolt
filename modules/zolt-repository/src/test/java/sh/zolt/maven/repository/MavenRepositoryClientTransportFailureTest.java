package sh.zolt.maven.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.maven.Coordinate;
import sh.zolt.maven.CoordinateParser;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MavenRepositoryClientTransportFailureTest {
    private final CoordinateParser parser = new CoordinateParser();

    @TempDir
    private Path tempDir;

    @Test
    void fetchInterruptionRestoresInterruptFlagAndExplainsRetry() {
        clearInterruptFlag();
        Coordinate coordinate = parser.parse("com.google.guava:guava:33.4.0-jre");
        MavenRepositoryClient client = new MavenRepositoryClient(
                new InterruptingHttpClient(),
                new MavenRepositoryPathBuilder());

        try {
            RepositoryClientException exception = assertThrows(
                    RepositoryClientException.class,
                    () -> client.fetchPom(URI.create("https://repo.example.test/maven2/"), coordinate));

            assertTrue(Thread.currentThread().isInterrupted());
            assertTrue(exception.getMessage().contains("Download interrupted while fetching com.google.guava:guava:33.4.0-jre"));
            assertTrue(exception.getMessage().contains("Try again."));
        } finally {
            clearInterruptFlag();
        }
    }

    @Test
    void uploadInterruptionRestoresInterruptFlagAndExplainsRetry() throws IOException {
        clearInterruptFlag();
        Coordinate coordinate = parser.parse("com.google.guava:guava:33.4.0-jre");
        Path source = tempDir.resolve("guava-33.4.0-jre.pom");
        Files.writeString(source, "<project/>");
        MavenRepositoryClient client = new MavenRepositoryClient(
                new InterruptingHttpClient(),
                new MavenRepositoryPathBuilder());

        try {
            RepositoryClientException exception = assertThrows(
                    RepositoryClientException.class,
                    () -> client.uploadPom(URI.create("https://repo.example.test/maven2/"), coordinate, source));

            assertTrue(Thread.currentThread().isInterrupted());
            assertTrue(exception.getMessage().contains("Upload interrupted while publishing com.google.guava:guava:33.4.0-jre"));
            assertTrue(exception.getMessage().contains("Try again."));
        } finally {
            clearInterruptFlag();
        }
    }

    @Test
    void fetchIoFailureRetriesBoundedAttemptsAndExplainsRemediation() {
        Coordinate coordinate = parser.parse("com.google.guava:guava:33.4.0-jre");
        FailingHttpClient httpClient = new FailingHttpClient();
        MavenRepositoryClient client = new MavenRepositoryClient(
                httpClient,
                new MavenRepositoryPathBuilder(),
                new RepositoryHttpPolicy(Duration.ofSeconds(5), 2, Duration.ZERO));

        RepositoryClientException exception = assertThrows(
                RepositoryClientException.class,
                () -> client.fetchPom(URI.create("https://repo.example.test/maven2/"), coordinate));

        assertEquals(2, httpClient.sends());
        assertTrue(exception.getMessage().contains("Could not download com.google.guava:guava:33.4.0-jre"));
        assertTrue(exception.getMessage().contains("after 2 attempts"));
        assertTrue(exception.getMessage().contains("Check your network, proxy, or repository URL and try again."));
    }

    @Test
    void uploadIoFailureRetriesBoundedAttemptsAndExplainsRemediation() throws IOException {
        Coordinate coordinate = parser.parse("com.google.guava:guava:33.4.0-jre");
        Path source = tempDir.resolve("guava-33.4.0-jre.pom");
        Files.writeString(source, "<project/>");
        FailingHttpClient httpClient = new FailingHttpClient();
        MavenRepositoryClient client = new MavenRepositoryClient(
                httpClient,
                new MavenRepositoryPathBuilder(),
                new RepositoryHttpPolicy(Duration.ofSeconds(5), 2, Duration.ZERO));

        RepositoryClientException exception = assertThrows(
                RepositoryClientException.class,
                () -> client.uploadPom(URI.create("https://repo.example.test/maven2/"), coordinate, source));

        assertEquals(2, httpClient.sends());
        assertTrue(exception.getMessage().contains("Could not upload com.google.guava:guava:33.4.0-jre"));
        assertTrue(exception.getMessage().contains("after 2 attempts"));
        assertTrue(exception.getMessage().contains("Check your network, proxy, repository URL, and publish permissions"));
    }

    private static void clearInterruptFlag() {
        Thread.interrupted();
    }

    private static final class InterruptingHttpClient extends DelegatingHttpClient {
        @Override
        public <T> HttpResponse<T> send(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) throws InterruptedException {
            throw new InterruptedException("test interrupt");
        }
    }

    private static final class FailingHttpClient extends DelegatingHttpClient {
        private final AtomicInteger sends = new AtomicInteger();

        @Override
        public <T> HttpResponse<T> send(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
            sends.incrementAndGet();
            throw new IOException("test network failure");
        }

        int sends() {
            return sends.get();
        }
    }

    private abstract static class DelegatingHttpClient extends HttpClient {
        private final HttpClient delegate = HttpClient.newHttpClient();

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return delegate.cookieHandler();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return delegate.connectTimeout();
        }

        @Override
        public Redirect followRedirects() {
            return delegate.followRedirects();
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return delegate.proxy();
        }

        @Override
        public SSLContext sslContext() {
            return delegate.sslContext();
        }

        @Override
        public SSLParameters sslParameters() {
            return delegate.sslParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return delegate.authenticator();
        }

        @Override
        public Version version() {
            return delegate.version();
        }

        @Override
        public Optional<Executor> executor() {
            return delegate.executor();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException("sendAsync is not used by MavenRepositoryClient tests");
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException("sendAsync is not used by MavenRepositoryClient tests");
        }
    }
}
