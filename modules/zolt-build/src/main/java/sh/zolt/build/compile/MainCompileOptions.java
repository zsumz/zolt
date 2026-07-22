package sh.zolt.build.compile;

import sh.zolt.classpath.Classpath;
import sh.zolt.project.CompilerSettings;
import sh.zolt.project.ProjectConfig;
import java.nio.file.Path;
import java.util.List;

/** Builds the {@link JavacOptions} for a main compile from the project's compiler settings. */
final class MainCompileOptions {
    private MainCompileOptions() {
    }

    static JavacOptions forMainSources(
            ProjectConfig config, List<Path> mainSources, Classpath compileClasspath, boolean hostMode) {
        CompilerSettings compiler = config.compilerSettings();
        JavacOptions options = new JavacOptions(
                effectiveRelease(config),
                compiler.encoding(),
                compiler.args(),
                List.of(),
                hostMode);
        if (CompilerPlatformApi.isModularSourceSet(mainSources)) {
            return options.withModulePath(compileClasspath.entries());
        }
        return options;
    }

    static String effectiveRelease(ProjectConfig config) {
        String compilerRelease = config.compilerSettings().release();
        return compilerRelease.isBlank() ? config.project().java() : compilerRelease;
    }
}
