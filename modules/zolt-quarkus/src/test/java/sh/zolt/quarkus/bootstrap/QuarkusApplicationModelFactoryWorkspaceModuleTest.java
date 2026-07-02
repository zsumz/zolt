package sh.zolt.quarkus.bootstrap;

import static sh.zolt.quarkus.bootstrap.QuarkusApplicationModelFactoryTestSupport.fakeApi;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.quarkus.bootstrap.QuarkusApplicationModelFactoryArtifactDoubles.FakeArtifactKey;
import sh.zolt.quarkus.bootstrap.QuarkusApplicationModelFactoryModelDoubles.FakeApplicationModel;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuarkusApplicationModelFactoryWorkspaceModuleTest {
    @Test
    void addsWorkspaceModuleOutputsAsAdditionalTestClasspathElements() {
        QuarkusApplicationModelFactory factory = new QuarkusApplicationModelFactory(fakeApi());

        QuarkusApplicationModelHandle handle = factory.create(
                QuarkusApplicationModelFactoryOptionsTest.descriptor(),
                java.util.Optional.of(new QuarkusWorkspaceModuleInputs(
                        Path.of("/repo"),
                        Path.of("/repo/target"),
                        Path.of("/repo/src/main/java"),
                        Path.of("/repo/src/main/resources"),
                        Path.of("/repo/target/classes"),
                        Path.of("/repo/src/test/java"),
                        Path.of("/repo/src/test/resources"),
                        Path.of("/repo/target/test-classes"))));

        FakeApplicationModel model = assertInstanceOf(FakeApplicationModel.class, handle.applicationModel());
        io.quarkus.bootstrap.workspace.FakeWorkspaceModule module =
                assertInstanceOf(io.quarkus.bootstrap.workspace.FakeWorkspaceModule.class,
                        model.appArtifact().workspaceModule());
        assertEquals(Path.of("/repo/zolt.toml"), module.buildFile());
        assertEquals(
                List.of("/repo/target/classes", "/repo/target/test-classes"),
                module.additionalTestClasspathElements());
        assertEquals(
                List.of("", "tests"),
                module.artifactSources().stream()
                        .map(io.quarkus.bootstrap.workspace.FakeArtifactSources::classifier)
                        .toList());
        assertTrue(model.appArtifact().workspaceModuleFlag());
        assertTrue(model.appArtifact().reloadable());
        assertEquals(
                List.of(new FakeArtifactKey("com.example", "demo", "", "jar")),
                model.reloadableWorkspaceModules());
    }
}
