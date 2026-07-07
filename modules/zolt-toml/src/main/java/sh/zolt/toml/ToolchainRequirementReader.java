package sh.zolt.toml;

import sh.zolt.error.ActionableError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.tomlj.Toml;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;

public final class ToolchainRequirementReader {
    public Optional<ToolchainRequirement> find(Path startDirectory) {
        Path current = startDirectory.toAbsolutePath().normalize();
        if (Files.isRegularFile(current)) {
            current = current.getParent();
        }
        while (current != null) {
            Path config = current.resolve("zolt.toml");
            if (Files.isRegularFile(config)) {
                Optional<ToolchainRequirement> requirement = read(config);
                if (requirement.isPresent()) {
                    return requirement;
                }
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    public Optional<ToolchainRequirement> read(Path configPath) {
        Path normalized = configPath.toAbsolutePath().normalize();
        try {
            TomlParseResult result = Toml.parse(normalized);
            if (result.hasErrors()) {
                throw new ZoltConfigException(parseErrorMessage(result, normalized));
            }
            return ToolchainSectionCodec.parseZoltVersion(result, "zolt.toml")
                    .map(version -> new ToolchainRequirement(normalized, version));
        } catch (IOException exception) {
            throw new ZoltConfigException(ActionableError.of(
                    "Could not read zolt.toml at " + normalized + ".",
                    "Check that the file exists and is readable.",
                    exception));
        }
    }

    private static String parseErrorMessage(TomlParseResult result, Path configPath) {
        TomlParseError firstError = result.errors().getFirst();
        return "Could not parse zolt.toml at "
                + configPath
                + ". Fix the TOML syntax near "
                + firstError.position()
                + ": "
                + firstError.getMessage();
    }
}
