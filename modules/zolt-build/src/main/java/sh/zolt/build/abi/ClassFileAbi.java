package sh.zolt.build.abi;

import sh.zolt.build.BuildException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public record ClassFileAbi(
        String binaryName,
        Path classFile,
        Optional<String> sourceFileName,
        int accessFlags,
        Optional<String> superName,
        List<String> interfaces,
        String abiHash,
        String packagePrivateAbiHash,
        List<String> referencedClasses) {
    public ClassFileAbi {
        if (binaryName == null || binaryName.isBlank()) {
            throw new BuildException("Class file ABI binary name is required.");
        }
        if (classFile == null) {
            throw new BuildException("Class file ABI path is required.");
        }
        classFile = classFile.toAbsolutePath().normalize();
        sourceFileName = sourceFileName == null ? Optional.empty() : sourceFileName;
        superName = superName == null ? Optional.empty() : superName;
        interfaces = interfaces == null ? List.of() : interfaces.stream().sorted().toList();
        referencedClasses = referencedClasses == null ? List.of() : referencedClasses.stream().sorted().distinct().toList();
        if (abiHash == null || abiHash.isBlank()) {
            throw new BuildException("Class file ABI hash is required.");
        }
        if (packagePrivateAbiHash == null || packagePrivateAbiHash.isBlank()) {
            throw new BuildException("Class file package-private ABI hash is required.");
        }
    }
}
