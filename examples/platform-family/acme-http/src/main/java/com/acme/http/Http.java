package com.acme.http;

import com.acme.core.Core;

/** HTTP entry point that depends on the sibling {@code acme-core} member. */
public final class Http {
    public String describe() {
        return "http@" + Core.platform();
    }
}
