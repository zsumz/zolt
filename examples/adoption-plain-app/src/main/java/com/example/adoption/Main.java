package com.example.adoption;

import com.google.common.base.Joiner;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public final class Main {
    private Main() {
    }

    public static String greeting(String name) {
        String template = resource("message.txt").trim();
        String normalized = StringUtils.defaultIfBlank(name, "Zolt");
        return template.replace("${name}", Joiner.on(" ").join(List.of("from", normalized)));
    }

    public static void main(String[] args) {
        String name = args.length == 0 ? "Zolt" : args[0];
        System.out.println(greeting(name));
    }

    private static String resource(String name) {
        try (InputStream input = Main.class.getClassLoader().getResourceAsStream(name)) {
            if (input == null) {
                throw new IllegalStateException("Missing resource `" + name + "`.");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read resource `" + name + "`.", exception);
        }
    }
}
