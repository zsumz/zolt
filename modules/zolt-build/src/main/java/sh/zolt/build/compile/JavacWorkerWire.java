package sh.zolt.build.compile;

import sh.zolt.build.incremental.GeneratedOutputAttribution;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Client half of the javac worker wire protocol (version 2), shared by both transports. A request is
 * a kind discriminator followed by the argument vector; a response is the exit code, the diagnostics
 * blob, and an attribution section that is empty for legacy compiles and carries the generated-output
 * map for attributed compiles.
 */
final class JavacWorkerWire {
    static final int MAGIC = 0x5a4f4c54;
    static final int PROTOCOL_VERSION = 2;
    static final int KIND_COMPILE = 0;
    static final int KIND_COMPILE_ATTRIBUTED = 1;

    private static final int MAX_RESPONSE_BYTES = 64 * 1024 * 1024;
    private static final int MAX_ENTRIES = 100_000_000;

    private JavacWorkerWire() {
    }

    static void writeRequest(DataOutputStream output, int kind, List<String> arguments) throws IOException {
        output.writeInt(kind);
        output.writeInt(arguments.size());
        for (String argument : arguments) {
            writeString(output, argument);
        }
    }

    static JavacRunner.ProcessResult readResponse(DataInputStream input) throws IOException {
        int exitCode = input.readInt();
        int outputLength = input.readInt();
        if (outputLength < 0 || outputLength > MAX_RESPONSE_BYTES) {
            throw new IOException("invalid javac worker response length " + outputLength);
        }
        byte[] outputBytes = input.readNBytes(outputLength);
        if (outputBytes.length != outputLength) {
            throw new IOException("incomplete javac worker response");
        }
        String output = new String(outputBytes, StandardCharsets.UTF_8);
        return new JavacRunner.ProcessResult(exitCode, output, readAttribution(input));
    }

    private static GeneratedOutputAttribution readAttribution(DataInputStream input) throws IOException {
        if (input.readInt() == 0) {
            return GeneratedOutputAttribution.absent();
        }
        boolean unattributed = input.readInt() == 1;
        int entryCount = readCount(input);
        List<GeneratedOutputAttribution.Entry> entries = new ArrayList<>(entryCount);
        for (int index = 0; index < entryCount; index++) {
            Path path = Path.of(readString(input));
            int kind = input.readInt();
            String createdType = readString(input);
            int originatingCount = readCount(input);
            List<String> originating = new ArrayList<>(originatingCount);
            for (int origin = 0; origin < originatingCount; origin++) {
                originating.add(readString(input));
            }
            entries.add(new GeneratedOutputAttribution.Entry(path, kind, createdType, originating));
        }
        return new GeneratedOutputAttribution(true, unattributed, entries);
    }

    private static int readCount(DataInputStream input) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new IOException("invalid attribution count " + count);
        }
        return count;
    }

    static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_RESPONSE_BYTES) {
            throw new IOException("invalid string length " + length);
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("incomplete string payload");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
