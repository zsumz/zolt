package com.zolt.build;

import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public final class ManifestGenerator {
    public GeneratedManifest generate(ProjectConfig config) {
        return generate(config.project());
    }

    public GeneratedManifest generate(ProjectMetadata project) {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        project.main().ifPresent(mainClass -> attributes.put(Attributes.Name.MAIN_CLASS, mainClass));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            manifest.write(output);
        } catch (IOException exception) {
            throw new ManifestGenerationException(
                    "Could not generate MANIFEST.MF. Check [project].main in zolt.toml and try again.",
                    exception);
        }

        return new GeneratedManifest(GeneratedManifest.DEFAULT_PATH, output.toByteArray(), project.main());
    }
}
