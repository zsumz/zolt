package sh.zolt.sbom;

/**
 * CycloneDX component {@code scope}. Compile/runtime dependencies are {@code required}; every
 * optionally-included scope (provided, dev, test, tools) is {@code optional}.
 */
public enum SbomComponentScope {
    REQUIRED("required"),
    OPTIONAL("optional");

    private final String jsonValue;

    SbomComponentScope(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    public String jsonValue() {
        return jsonValue;
    }
}
