package sh.zolt.toolchain.jvm;

public enum JavaToolchainSource {
    AMBIENT("ambient"),
    MANAGED("managed");

    private final String label;

    JavaToolchainSource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
