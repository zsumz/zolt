package sh.zolt.explain.verify;

/**
 * The incumbent build tool a {@link VerifyReport} compares Zolt against. The verifier's model is
 * shared: a Gradle-resolved dependency set flows through the same {@link ResolvedModule} /
 * {@link VerifyComparator} pipeline as a Maven one, occupying the "incumbent" side. This enum labels
 * which incumbent produced the report so the text renderer can name it honestly and the JSON writer
 * can declare it in an additive {@code buildTool} field (the JSON keeps its {@code maven*} field names
 * for schema stability across both tools).
 */
public enum BuildTool {
    MAVEN("maven", "Maven"),
    GRADLE("gradle", "Gradle");

    private final String token;
    private final String displayName;

    BuildTool(String token, String displayName) {
        this.token = token;
        this.displayName = displayName;
    }

    /** Lowercase machine token emitted in JSON ({@code "maven"} / {@code "gradle"}). */
    public String token() {
        return token;
    }

    /** Capitalized name for human text ({@code "Maven"} / {@code "Gradle"}). */
    public String displayName() {
        return displayName;
    }
}
