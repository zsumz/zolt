package com.zolt.cli;

import java.io.PrintWriter;
import java.io.StringWriter;
import picocli.CommandLine;

final class CliTestSupport {
    private CliTestSupport() {
    }

    static CommandResult execute(String... args) {
        CommandLine commandLine = ZoltCli.newCommandLine();
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();
        commandLine.setOut(new PrintWriter(stdout));
        commandLine.setErr(new PrintWriter(stderr));

        int exitCode = commandLine.execute(args);

        return new CommandResult(exitCode, stdout.toString(), stderr.toString());
    }

    static String memberConfig(String name) {
        return """
                [project]
                name = "%s"
                version = "0.1.0"
                group = "com.example"
                java = "%s"
                """.formatted(name, currentJavaMajorVersion());
    }

    static String generatedSourceConfig(
            String scope,
            String id,
            String output,
            String input,
            boolean required) {
        return """

                [generated.%s.%s]
                kind = "declared-root"
                language = "java"
                output = "%s"
                inputs = ["%s"]
                required = %s
                """.formatted(scope, id, output, input, required);
    }

    private static String currentJavaMajorVersion() {
        String version = System.getProperty("java.version");
        String[] parts = version.split("[._+-]", -1);
        if (parts.length >= 2 && "1".equals(parts[0])) {
            return parts[1];
        }
        return parts[0];
    }

    record CommandResult(int exitCode, String stdout, String stderr) {
    }
}
