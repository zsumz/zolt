package com.zolt.toml;

import com.zolt.error.ActionableError;
import com.zolt.error.HasActionableError;

public final class ZoltConfigException extends RuntimeException implements HasActionableError {
    private final transient ActionableError error;

    public ZoltConfigException(String message) {
        super(message);
        this.error = null;
    }

    public ZoltConfigException(ActionableError error) {
        super(error.message(), error.cause().orElse(null));
        this.error = error;
    }

    @Override
    public ActionableError actionableError() {
        return error;
    }
}
