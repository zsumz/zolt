package com.zolt.project;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record PackageSettings(
        PackageMode mode,
        boolean sources,
        boolean javadoc,
        boolean tests,
        PublicationMetadata metadata,
        Map<String, String> manifestAttributes) {
    public PackageSettings {
        mode = mode == null ? PackageMode.THIN : mode;
        metadata = metadata == null ? PublicationMetadata.empty() : metadata;
        manifestAttributes = manifestAttributes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(manifestAttributes));
    }

    public PackageSettings(
            PackageMode mode,
            boolean sources,
            boolean javadoc,
            boolean tests,
            PublicationMetadata metadata) {
        this(mode, sources, javadoc, tests, metadata, Map.of());
    }

    public PackageSettings(PackageMode mode) {
        this(mode, false, false, false, PublicationMetadata.empty(), Map.of());
    }

    public static PackageSettings defaults() {
        return new PackageSettings(PackageMode.THIN, false, false, false, PublicationMetadata.empty(), Map.of());
    }
}
