package com.example.large.core;

import com.google.common.base.Joiner;
import org.apache.commons.lang3.StringUtils;

public final class CoreMessage {
    private CoreMessage() {
    }

    public static String base() {
        return StringUtils.capitalize(Joiner.on("-").join("large", "workspace"));
    }
}
