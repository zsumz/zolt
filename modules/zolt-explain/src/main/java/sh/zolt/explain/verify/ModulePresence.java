package sh.zolt.explain.verify;

/** Whether a module (joined by {@code group:artifact}) was found on both sides or only one. */
public enum ModulePresence {
    BOTH("both"),
    MAVEN_ONLY("maven-only"),
    ZOLT_ONLY("zolt-only");

    private final String token;

    ModulePresence(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }
}
