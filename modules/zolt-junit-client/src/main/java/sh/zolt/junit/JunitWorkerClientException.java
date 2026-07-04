package sh.zolt.junit;

public final class JunitWorkerClientException extends RuntimeException {
    public JunitWorkerClientException(String message) {
        super(message);
    }

    public JunitWorkerClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
