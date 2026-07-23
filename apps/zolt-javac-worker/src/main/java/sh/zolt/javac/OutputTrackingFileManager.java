package sh.zolt.javac;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

/**
 * Wraps the standard file manager and records the ground-truth set of outputs the compiler actually
 * created: generated sources under {@link StandardLocation#SOURCE_OUTPUT} (via
 * {@link #getJavaFileForOutput}) and generated resources under {@link StandardLocation#CLASS_OUTPUT}
 * or {@link StandardLocation#SOURCE_OUTPUT} (via {@link #getFileForOutput}, the {@code createResource}
 * path). This is independent of the Filer interception: anything created here that the Filer did not
 * also report is an unexplained generated output, which {@link AttributionCollector} treats as
 * unattributed. Resources typically land in {@code CLASS_OUTPUT}, so watching it is what makes an
 * unrecorded resource write (e.g. from a non-recording processor) force a full recompile.
 */
final class OutputTrackingFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
    private final AttributionCollector collector;

    OutputTrackingFileManager(StandardJavaFileManager fileManager, AttributionCollector collector) {
        super(fileManager);
        this.collector = collector;
    }

    @Override
    public JavaFileObject getJavaFileForOutput(
            Location location,
            String className,
            JavaFileObject.Kind kind,
            FileObject sibling) throws IOException {
        JavaFileObject fileObject = super.getJavaFileForOutput(location, className, kind, sibling);
        if (location == StandardLocation.SOURCE_OUTPUT) {
            collector.observeSourceOutput(pathOf(fileObject));
        }
        return fileObject;
    }

    @Override
    public FileObject getFileForOutput(
            Location location,
            String packageName,
            String relativeName,
            FileObject sibling) throws IOException {
        FileObject fileObject = super.getFileForOutput(location, packageName, relativeName, sibling);
        if (location == StandardLocation.CLASS_OUTPUT || location == StandardLocation.SOURCE_OUTPUT) {
            collector.observeResourceOutput(pathOf(fileObject));
        }
        return fileObject;
    }

    private static Path pathOf(FileObject fileObject) {
        URI uri = fileObject.toUri();
        if (uri != null && "file".equalsIgnoreCase(uri.getScheme())) {
            return Path.of(uri);
        }
        return Path.of(fileObject.getName());
    }
}
