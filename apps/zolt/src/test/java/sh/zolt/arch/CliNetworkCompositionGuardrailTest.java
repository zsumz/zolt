package sh.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Guards the CLI's outbound-network composition boundary. Every {@code apps/zolt} command must obtain
 * its repository client and transport from {@link sh.zolt.cli.net.CommandNetwork}, which reads the
 * user-global {@code [network]} config (proxy, proxy credentials, CA bundle, toolchain mirror).
 *
 * <p>Constructing a bare {@code new MavenRepositoryClient()} or calling {@code
 * NetworkTransport.fromEnvironment()} anywhere in {@code apps/zolt/src/main} bypasses that config and
 * reintroduces the split-brain bug this fix removed: {@code zolt resolve} honoring the corporate CA
 * while {@code zolt outdated}/{@code zolt update} silently do not. The library convenience defaults
 * stay legal in their own modules (for tests and embedding); only the CLI is constrained here. The
 * configured, argument-bearing form {@code new MavenRepositoryClient(transport)} is unaffected.
 */
final class CliNetworkCompositionGuardrailTest {
    private static final Pattern BARE_REPOSITORY_CLIENT =
            Pattern.compile("new\\s+MavenRepositoryClient\\s*\\(\\s*\\)");
    private static final Pattern TRANSPORT_FROM_ENVIRONMENT =
            Pattern.compile("NetworkTransport\\s*\\.\\s*fromEnvironment\\s*\\(");

    @Test
    void cliMainSourcesRouteOutboundNetworkThroughCommandNetwork() throws IOException {
        Path cliMain = RepositoryPaths.appRoot().resolve("src/main/java");
        List<String> violations = new ArrayList<>();
        for (Path javaFile : ArchitectureSourceFiles.javaFiles(List.of(cliMain))) {
            String source = Files.readString(javaFile);
            String display = RepositoryPaths.displayPath(javaFile);
            if (BARE_REPOSITORY_CLIENT.matcher(source).find()) {
                violations.add(display + " constructs a bare `new MavenRepositoryClient()`; use "
                        + "CommandNetwork.repositoryClient() so the corporate proxy and CA bundle are honored.");
            }
            if (TRANSPORT_FROM_ENVIRONMENT.matcher(source).find()) {
                violations.add(display + " calls NetworkTransport.fromEnvironment(); use "
                        + "CommandNetwork.defaultTransport()/transport(path) so the [network] config is honored.");
            }
        }
        assertTrue(
                violations.isEmpty(),
                () -> "CLI outbound-network composition guardrail violations:\n  " + String.join("\n  ", violations));
    }

    @Test
    void scannerFlagsBareConstructionButNotTheConfiguredForm(@TempDir Path tempDir) throws IOException {
        Path offender = tempDir.resolve("Bad.java");
        Files.writeString(
                offender,
                "class Bad {\n"
                        + "  Object a = new MavenRepositoryClient();\n"
                        + "  Object t = NetworkTransport.fromEnvironment();\n"
                        + "}\n");
        String flagged = Files.readString(offender);

        assertTrue(BARE_REPOSITORY_CLIENT.matcher(flagged).find());
        assertTrue(TRANSPORT_FROM_ENVIRONMENT.matcher(flagged).find());
        assertFalse(BARE_REPOSITORY_CLIENT.matcher("new MavenRepositoryClient(defaultTransport())").find());
    }
}
