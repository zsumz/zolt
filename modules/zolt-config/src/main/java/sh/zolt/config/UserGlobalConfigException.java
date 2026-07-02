package sh.zolt.config;

public final class UserGlobalConfigException extends RuntimeException {
    public UserGlobalConfigException(String message) {
        super(message);
    }

    public UserGlobalConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
