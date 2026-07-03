package sh.zolt.classpath;

public record ClasspathSet(
        Classpath compile,
        Classpath runtime,
        Classpath test,
        Classpath testCompile,
        Classpath processor,
        Classpath testProcessor,
        Classpath quarkusDeployment) {
    public ClasspathSet(
            Classpath compile,
            Classpath runtime,
            Classpath test,
            Classpath processor,
            Classpath testProcessor,
            Classpath quarkusDeployment) {
        this(compile, runtime, test, test, processor, testProcessor, quarkusDeployment);
    }
}
