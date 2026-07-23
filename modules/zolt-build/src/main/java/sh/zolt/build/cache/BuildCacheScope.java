package sh.zolt.build.cache;

/** Compile scope a build-output cache entry belongs to. Main and test are stored as separate entries. */
public enum BuildCacheScope {
    MAIN("main"),
    TEST("test");

    private final String id;

    BuildCacheScope(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
