package sh.zolt.maven.repository;

import sh.zolt.maven.ArtifactDescriptor;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

final class CountingByteArrayBodyHandler implements HttpResponse.BodyHandler<byte[]> {
    private static final long UNKNOWN_LENGTH = -1L;

    private final ArtifactDescriptor descriptor;
    private final RepositoryDownloadListener listener;

    CountingByteArrayBodyHandler(ArtifactDescriptor descriptor, RepositoryDownloadListener listener) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.listener = listener == null ? RepositoryDownloadListener.NOOP : listener;
    }

    @Override
    public HttpResponse.BodySubscriber<byte[]> apply(HttpResponse.ResponseInfo responseInfo) {
        long total = successful(responseInfo.statusCode())
                ? contentLength(responseInfo.headers())
                : UNKNOWN_LENGTH;
        return new CountingByteArrayBodySubscriber(descriptor, listener, total);
    }

    private static boolean successful(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private static long contentLength(HttpHeaders headers) {
        return headers.firstValue("Content-Length")
                .flatMap(CountingByteArrayBodyHandler::parseNonNegativeLong)
                .orElse(UNKNOWN_LENGTH);
    }

    private static Optional<Long> parseNonNegativeLong(String value) {
        try {
            long parsed = Long.parseLong(value);
            return parsed >= 0 ? Optional.of(parsed) : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static final class CountingByteArrayBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final ArtifactDescriptor descriptor;
        private final RepositoryDownloadListener listener;
        private final long total;
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private long received;

        private CountingByteArrayBodySubscriber(
                ArtifactDescriptor descriptor,
                RepositoryDownloadListener listener,
                long total) {
            this.descriptor = descriptor;
            this.listener = listener;
            this.total = total;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            for (ByteBuffer item : items) {
                int length = item.remaining();
                if (length == 0) {
                    continue;
                }
                byte[] chunk = new byte[length];
                item.get(chunk);
                body.writeBytes(chunk);
                received += length;
                if (total != UNKNOWN_LENGTH) {
                    listener.onBytes(descriptor, received, total);
                }
            }
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            result.complete(body.toByteArray());
        }
    }
}
