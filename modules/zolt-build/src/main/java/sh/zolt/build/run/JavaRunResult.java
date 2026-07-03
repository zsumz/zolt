package sh.zolt.build.run;

public record JavaRunResult(
        String mainClass,
        String output,
        int signal) {
    private static final int NOT_SIGNALLED = -1;

    public JavaRunResult(String mainClass, String output) {
        this(mainClass, output, NOT_SIGNALLED);
    }

    public boolean signalled() {
        return signal != NOT_SIGNALLED;
    }
}
