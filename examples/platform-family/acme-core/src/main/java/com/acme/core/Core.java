package com.acme.core;

/** Core utility exposed to the rest of the Acme platform. */
public final class Core {
    private Core() {
    }

    public static String platform() {
        return "acme-platform";
    }
}
