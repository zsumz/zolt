package sh.zolt.canary.execprocess;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;
import sh.zolt.canary.execprocess.generated.Greeting;

/**
 * Uses the exec-process-generated Java source ({@link Greeting}) and the exec-process-generated
 * resource, both produced by {@code sh} process steps and consumed by the compile and the package.
 */
public final class Main {
    private Main() {
    }

    public static String greeting() {
        return Greeting.text();
    }

    public static Properties resource() {
        Properties properties = new Properties();
        try (InputStream in = Main.class.getResourceAsStream("/exec-canary.properties")) {
            if (in == null) {
                throw new IllegalStateException("exec-canary.properties was not generated onto the classpath");
            }
            properties.load(in);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return properties;
    }

    public static void main(String[] args) {
        System.out.println(greeting() + " :: " + resource().getProperty("canary.source"));
    }
}
