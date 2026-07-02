package sh.zolt.build;

public record CompileDiagnostics(
        int sourcesAdded,
        int sourcesChanged,
        int sourcesDeleted,
        int sourcesRecompiled,
        int dependentSourcesRecompiled,
        int classesDeleted,
        int abiChangedClasses,
        int packagePrivateAbiChangedClasses) {
    public CompileDiagnostics {
        sourcesAdded = Math.max(0, sourcesAdded);
        sourcesChanged = Math.max(0, sourcesChanged);
        sourcesDeleted = Math.max(0, sourcesDeleted);
        sourcesRecompiled = Math.max(0, sourcesRecompiled);
        dependentSourcesRecompiled = Math.max(0, dependentSourcesRecompiled);
        classesDeleted = Math.max(0, classesDeleted);
        abiChangedClasses = Math.max(0, abiChangedClasses);
        packagePrivateAbiChangedClasses = Math.max(0, packagePrivateAbiChangedClasses);
    }

    public static CompileDiagnostics empty() {
        return new CompileDiagnostics(0, 0, 0, 0, 0, 0, 0, 0);
    }

    public static CompileDiagnostics legacy(int sourceCount, boolean skipped) {
        return new CompileDiagnostics(0, 0, 0, skipped ? 0 : sourceCount, 0, 0, 0, 0);
    }
}
