package sh.zolt.maven.repository;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.maven.ArtifactDescriptor;
import sh.zolt.maven.Coordinate;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

final class CountingByteArrayBodyHandlerTest {
    private static final Coordinate APP = new Coordinate("com.example", "app", Optional.of("1.0.0"));
    private static final ArtifactDescriptor DESCRIPTOR = ArtifactDescriptor.jar(APP);

    @Test
    void knownContentLengthEmitsMonotonicByteEventsAndReturnsExactBytes() {
        List<ByteEvent> events = new ArrayList<>();
        HttpResponse.BodySubscriber<byte[]> subscriber = new CountingByteArrayBodyHandler(
                DESCRIPTOR,
                (descriptor, received, total) -> events.add(new ByteEvent(descriptor, received, total)))
                .apply(responseInfo(200, Map.of("Content-Length", List.of("5"))));
        RecordingSubscription subscription = new RecordingSubscription();

        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(
                ByteBuffer.wrap(new byte[] {1, 2}),
                ByteBuffer.wrap(new byte[] {3})));
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[] {4, 5})));
        subscriber.onComplete();

        assertEquals(Long.MAX_VALUE, subscription.requested());
        assertArrayEquals(new byte[] {1, 2, 3, 4, 5}, subscriber.getBody().toCompletableFuture().join());
        assertEquals(
                List.of(
                        new ByteEvent(DESCRIPTOR, 2L, 5L),
                        new ByteEvent(DESCRIPTOR, 3L, 5L),
                        new ByteEvent(DESCRIPTOR, 5L, 5L)),
                events);
    }

    @Test
    void absentContentLengthReturnsExactBytesWithoutByteEvents() {
        List<ByteEvent> events = new ArrayList<>();
        HttpResponse.BodySubscriber<byte[]> subscriber = new CountingByteArrayBodyHandler(
                DESCRIPTOR,
                (descriptor, received, total) -> events.add(new ByteEvent(descriptor, received, total)))
                .apply(responseInfo(200, Map.of()));

        subscriber.onSubscribe(new RecordingSubscription());
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[] {9, 8, 7})));
        subscriber.onComplete();

        assertArrayEquals(new byte[] {9, 8, 7}, subscriber.getBody().toCompletableFuture().join());
        assertTrue(events.isEmpty(), "unknown length stays indeterminate");
    }

    @Test
    void nonSuccessResponseBodyDoesNotEmitByteEvents() {
        List<ByteEvent> events = new ArrayList<>();
        HttpResponse.BodySubscriber<byte[]> subscriber = new CountingByteArrayBodyHandler(
                DESCRIPTOR,
                (descriptor, received, total) -> events.add(new ByteEvent(descriptor, received, total)))
                .apply(responseInfo(404, Map.of("Content-Length", List.of("7"))));

        subscriber.onSubscribe(new RecordingSubscription());
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[] {4, 0, 4})));
        subscriber.onComplete();

        assertArrayEquals(new byte[] {4, 0, 4}, subscriber.getBody().toCompletableFuture().join());
        assertTrue(events.isEmpty(), "error response bodies do not drive artifact progress");
    }

    private static HttpResponse.ResponseInfo responseInfo(int statusCode, Map<String, List<String>> headers) {
        return new TestResponseInfo(
                statusCode,
                HttpHeaders.of(headers, (name, value) -> true));
    }

    private record TestResponseInfo(int statusCode, HttpHeaders headers) implements HttpResponse.ResponseInfo {
        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }

    private static final class RecordingSubscription implements Flow.Subscription {
        private long requested;

        @Override
        public void request(long n) {
            requested += n;
        }

        @Override
        public void cancel() {
        }

        private long requested() {
            return requested;
        }
    }

    private record ByteEvent(ArtifactDescriptor descriptor, long received, long total) {
    }
}
