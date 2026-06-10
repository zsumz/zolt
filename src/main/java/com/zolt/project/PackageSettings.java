package com.zolt.project;

public record PackageSettings(
        PackageMode mode,
        boolean sources,
        boolean javadoc,
        boolean tests,
        PublicationMetadata metadata) {
    public PackageSettings {
        mode = mode == null ? PackageMode.THIN : mode;
        metadata = metadata == null ? PublicationMetadata.empty() : metadata;
    }

    public PackageSettings(PackageMode mode) {
        this(mode, false, false, false, PublicationMetadata.empty());
    }

    public static PackageSettings defaults() {
        return new PackageSettings(PackageMode.THIN, false, false, false, PublicationMetadata.empty());
    }
}
