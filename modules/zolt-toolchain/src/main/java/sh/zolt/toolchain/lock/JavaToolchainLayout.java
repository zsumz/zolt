package sh.zolt.toolchain.lock;

public record JavaToolchainLayout(
        String javaHome,
        String java,
        String javac,
        String jar,
        String nativeImage) {
    public JavaToolchainLayout {
        javaHome = clean(javaHome, ".");
        java = clean(java, "bin/java");
        javac = clean(javac, "bin/javac");
        jar = clean(jar, "bin/jar");
        nativeImage = clean(nativeImage, "");
    }

    public static JavaToolchainLayout standard(boolean nativeImage) {
        return new JavaToolchainLayout(
                ".",
                "bin/java",
                "bin/javac",
                "bin/jar",
                nativeImage ? "bin/native-image" : "");
    }

    private static String clean(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.strip();
    }
}
