package sh.zolt.canary.execjvm;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/** Reads the exec-generated build-info resource from the packaged classpath. */
public final class Main {
    private Main() {
    }

    public static Properties buildInfo() {
        Properties properties = new Properties();
        try (InputStream in = Main.class.getResourceAsStream("/build-info.properties")) {
            if (in == null) {
                throw new IllegalStateException("build-info.properties was not generated onto the classpath");
            }
            properties.load(in);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return properties;
    }

    public static void main(String[] args) {
        Properties info = buildInfo();
        System.out.println("exec-jvm-canary version " + info.getProperty("canary.version")
                + " (" + info.getProperty("canary.generator") + ")");
    }
}
