package sh.zolt.sbom;

/** CycloneDX component {@code type} values Zolt emits. */
public enum SbomComponentType {
    LIBRARY("library"),
    APPLICATION("application");

    private final String jsonValue;

    SbomComponentType(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    public String jsonValue() {
        return jsonValue;
    }
}
