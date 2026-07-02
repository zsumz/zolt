package sh.zolt.project;

public record BuildMetadataSettings(
        boolean buildInfo,
        boolean git,
        boolean reproducible) {
    public static BuildMetadataSettings defaults() {
        return new BuildMetadataSettings(false, false, false);
    }
}
