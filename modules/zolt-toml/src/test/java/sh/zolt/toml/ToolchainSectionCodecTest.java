package sh.zolt.toml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.project.toolchain.JavaDistribution;
import sh.zolt.project.toolchain.JavaFeature;
import sh.zolt.project.toolchain.JavaToolchainRequest;
import sh.zolt.project.toolchain.ToolchainPolicy;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.tomlj.Toml;

final class ToolchainSectionCodecTest {
    @Test
    void parsesJavaToolchainTable() {
        Optional<JavaToolchainRequest> request = ToolchainSectionCodec.parseJavaToolchain(Toml.parse("""
                [toolchain.zolt]
                version = "0.1.0"

                [toolchain.java]
                version = "21"
                distribution = "graalvm-community"
                features = ["native-image"]
                policy = "require-managed"
                """), "zolt.toml");

        JavaToolchainRequest java = request.orElseThrow();
        assertEquals("21", java.version());
        assertEquals(Optional.of(JavaDistribution.GRAALVM_COMMUNITY), java.distribution());
        assertEquals(Set.of(JavaFeature.NATIVE_IMAGE), java.features());
        assertEquals(ToolchainPolicy.REQUIRE_MANAGED, java.policy());
    }

    @Test
    void javaToolchainDoesNotBreakZoltVersionParsing() {
        Optional<String> version = ToolchainSectionCodec.parseZoltVersion(Toml.parse("""
                [toolchain.zolt]
                version = "0.1.0"

                [toolchain.java]
                version = "21"
                """), "zolt.toml");

        assertEquals(Optional.of("0.1.0"), version);
    }

    @Test
    void rejectsScalarJavaToolchain() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> ToolchainSectionCodec.parseJavaToolchain(Toml.parse("""
                        [toolchain]
                        java = "21"
                        """), "zolt.toml"));

        assertEquals("Invalid value for [toolchain].java in zolt.toml. Use a table.", exception.getMessage());
    }

    @Test
    void rejectsUnknownFeature() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> ToolchainSectionCodec.parseJavaToolchain(Toml.parse("""
                        [toolchain.java]
                        version = "21"
                        features = ["loom"]
                        """), "zolt.toml"));

        assertTrue(exception.getMessage().contains("Unsupported value for [toolchain.java].features"));
    }
}
