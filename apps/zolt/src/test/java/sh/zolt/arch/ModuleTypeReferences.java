package sh.zolt.arch;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/*
 * Ground-truth cross-lib type-edge index, derived from compiled bytecode.
 *
 * The import-declaration guardrail historically resolved cross-lib edges from the
 * `import` lines of each source file (see WorkspaceDependencyDeclarations.zoltImports).
 * That is text-level, not type-level: a type referenced by fully-qualified name with
 * no `import`, reached through a wildcard `import sh.zolt.x.*;`, or pulled in via a
 * `import static sh.zolt.x.Foo.BAR;` never appears as a resolvable `import sh.zolt.x.Foo;`
 * line, so the edge crossed a lib boundary invisibly to the guard.
 *
 * The workspace is compiled in the same bootstrap run, so each module's `.class` output
 * sits under <module>/target/classes. javac has already resolved every type reference --
 * FQN usage, wildcard expansions, and static imports alike -- into ordinary constant-pool
 * entries (CONSTANT_Class names and L...; type descriptors). Reading those gives the real
 * set of sh.zolt.* types a module's bytecode depends on, with no new parser dependency and
 * no blind spots from the source-text holes above.
 *
 * For each module this records (module, referencedType, exampleClassFile) sites; callers
 * resolve referencedType to its owning module via the source-derived FQ-type owner index
 * (WorkspaceDependencyDeclarations.typeOwners) and flag undeclared cross-module owners.
 */
final class ModuleTypeReferences {
    private static final int CONSTANT_UTF8 = 1;
    private static final int CONSTANT_INTEGER = 3;
    private static final int CONSTANT_FLOAT = 4;
    private static final int CONSTANT_LONG = 5;
    private static final int CONSTANT_DOUBLE = 6;
    private static final int CONSTANT_CLASS = 7;
    private static final int CONSTANT_STRING = 8;
    private static final int CONSTANT_FIELDREF = 9;
    private static final int CONSTANT_METHODREF = 10;
    private static final int CONSTANT_INTERFACE_METHODREF = 11;
    private static final int CONSTANT_NAME_AND_TYPE = 12;
    private static final int CONSTANT_METHOD_HANDLE = 15;
    private static final int CONSTANT_METHOD_TYPE = 16;
    private static final int CONSTANT_DYNAMIC = 17;
    private static final int CONSTANT_INVOKE_DYNAMIC = 18;
    private static final int CONSTANT_MODULE = 19;
    private static final int CONSTANT_PACKAGE = 20;

    private ModuleTypeReferences() {
    }

    /**
     * Collects every sh.zolt.* type each module's compiled bytecode references, grouped so
     * that an undeclared edge can be reported with a concrete example class file. The class
     * output is read from {@code <module>/target/classes}; a module whose output is absent
     * (not yet compiled) is skipped, so callers that need ground truth should assert the
     * workspace is built (see {@link #compiledModules(List)}).
     */
    static List<TypeReferenceSite> typeReferences(List<Path> sourceRoots) throws IOException {
        List<TypeReferenceSite> sites = new ArrayList<>();
        for (Path sourceRoot : sourceRoots) {
            String module = WorkspaceDependencyDeclarations.moduleName(sourceRoot);
            Path classesRoot = classesRoot(sourceRoot);
            if (!Files.isDirectory(classesRoot)) {
                continue;
            }
            for (Path classFile : classFiles(classesRoot)) {
                for (String referencedType : referencedZoltTypes(classFile)) {
                    sites.add(new TypeReferenceSite(module, referencedType, classFile));
                }
            }
        }
        return List.copyOf(sites);
    }

    /**
     * The modules (by source root) whose compiled {@code target/classes} output is present.
     * Lets callers verify the workspace was compiled before trusting bytecode ground truth.
     */
    static Set<String> compiledModules(List<Path> sourceRoots) {
        Set<String> compiled = new TreeSet<>();
        for (Path sourceRoot : sourceRoots) {
            if (Files.isDirectory(classesRoot(sourceRoot))) {
                compiled.add(WorkspaceDependencyDeclarations.moduleName(sourceRoot));
            }
        }
        return compiled;
    }

    /** Maps a {@code <module>/src/main/java} source root to its {@code <module>/target/classes} output. */
    static Path classesRoot(Path sourceRoot) {
        return WorkspaceDependencyDeclarations.moduleRoot(sourceRoot).resolve("target/classes");
    }

    private static List<Path> classFiles(Path classesRoot) throws IOException {
        try (Stream<Path> paths = Files.walk(classesRoot)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".class"))
                    .sorted()
                    .toList();
        }
    }

    /**
     * Reads a single class file's constant pool and returns the set of fully-qualified
     * sh.zolt.* types it references. Both CONSTANT_Class binary names (e.g.
     * {@code sh/zolt/x/Foo}) and L-descriptors embedded in field/method/signature Utf8
     * entries (e.g. {@code Lsh/zolt/x/Foo;}) are scanned, so a type reached only as a
     * parameter, return, field, generic-signature, or array element type is captured even
     * when it has no CONSTANT_Class entry of its own. Inner-class references collapse to
     * their outer type ({@code sh.zolt.x.Outer$Inner} -> {@code sh.zolt.x.Outer}); the
     * owner index resolves the rest.
     */
    static Set<String> referencedZoltTypes(Path classFile) throws IOException {
        Set<String> types = new TreeSet<>();
        try (DataInputStream in = new DataInputStream(Files.newInputStream(classFile))) {
            int magic = in.readInt();
            if (magic != 0xCAFEBABE) {
                throw new IOException("Not a class file (bad magic): " + classFile);
            }
            in.readUnsignedShort(); // minor version
            in.readUnsignedShort(); // major version
            int constantPoolCount = in.readUnsignedShort();
            for (int index = 1; index < constantPoolCount; index++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case CONSTANT_UTF8 -> collectFromUtf8(in.readUTF(), types);
                    case CONSTANT_INTEGER, CONSTANT_FLOAT -> in.readInt();
                    case CONSTANT_LONG, CONSTANT_DOUBLE -> {
                        in.readLong();
                        index++; // long/double occupy two constant-pool slots
                    }
                    case CONSTANT_CLASS,
                            CONSTANT_STRING,
                            CONSTANT_METHOD_TYPE,
                            CONSTANT_MODULE,
                            CONSTANT_PACKAGE -> in.readUnsignedShort();
                    case CONSTANT_FIELDREF,
                            CONSTANT_METHODREF,
                            CONSTANT_INTERFACE_METHODREF,
                            CONSTANT_NAME_AND_TYPE,
                            CONSTANT_DYNAMIC,
                            CONSTANT_INVOKE_DYNAMIC -> {
                        in.readUnsignedShort();
                        in.readUnsignedShort();
                    }
                    case CONSTANT_METHOD_HANDLE -> {
                        in.readUnsignedByte();
                        in.readUnsignedShort();
                    }
                    default -> throw new IOException(
                            "Unknown constant pool tag " + tag + " at index " + index + " in " + classFile);
                }
            }
        } catch (EOFException exception) {
            throw new IOException("Truncated class file: " + classFile, exception);
        }
        return types;
    }

    /**
     * Extracts every {@code sh/zolt/...} type from a single Utf8 constant, which may be a
     * bare binary class name, a field/method type descriptor, or a generic signature. The
     * scan walks the string character by character; whenever it sees the literal
     * {@code sh/zolt/} it reads the run of name characters that follow, so descriptors that
     * pack several types together (e.g. {@code (Lsh/zolt/a/A;)Lsh/zolt/b/B;}) yield each.
     */
    private static void collectFromUtf8(String value, Set<String> types) {
        int from = 0;
        while (true) {
            int start = value.indexOf("sh/zolt/", from);
            if (start < 0) {
                return;
            }
            int end = start;
            while (end < value.length() && isInternalNameChar(value.charAt(end))) {
                end++;
            }
            String internalName = value.substring(start, end);
            from = end;
            normalizeOwnerType(internalName).ifPresent(types::add);
        }
    }

    private static boolean isInternalNameChar(char character) {
        return character == '/'
                || character == '$'
                || character == '_'
                || Character.isLetterOrDigit(character);
    }

    /**
     * Turns an internal binary name ({@code sh/zolt/x/Outer$Inner}) into the fully-qualified
     * top-level owner type ({@code sh.zolt.x.Outer}). A run that stops before a class segment
     * (e.g. a bare {@code sh/zolt} package fragment) yields empty.
     */
    private static Optional<String> normalizeOwnerType(String internalName) {
        String dollarTrimmed = internalName;
        int dollar = dollarTrimmed.indexOf('$');
        if (dollar >= 0) {
            dollarTrimmed = dollarTrimmed.substring(0, dollar);
        }
        String fullyQualified = dollarTrimmed.replace('/', '.');
        // Require at least sh.zolt.<package>.<Type>: a package-only fragment has no owner.
        if (fullyQualified.chars().filter(character -> character == '.').count() < 3) {
            return Optional.empty();
        }
        return Optional.of(fullyQualified);
    }

    record TypeReferenceSite(String module, String referencedType, Path classFile) {
    }
}
