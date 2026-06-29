package com.zolt.build.testruntime;

public final class TestRunException extends RuntimeException {
    public TestRunException(String message) {
        super(message);
    }

    public TestRunException(String message, Throwable cause) {
        super(message, cause);
    }
}
