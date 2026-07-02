package sh.zolt.build;

public final class GroovyCompileException extends RuntimeException {
    public GroovyCompileException(String message) {
        super(message);
    }

    public GroovyCompileException(String message, Throwable cause) {
        super(message, cause);
    }
}
