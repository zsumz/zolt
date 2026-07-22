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
 * Wraps the standard file manager and records the ground-truth set of files the compiler actually
 * created under {@link StandardLocation#SOURCE_OUTPUT}. This is independent of the Filer interception:
 * anything created here that the Filer did not also report is an unexplained generated source, which
 * {@link AttributionCollector} treats as unattributed.
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
            observe(fileObject);
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
        if (location == StandardLocation.SOURCE_OUTPUT) {
            observe(fileObject);
        }
        return fileObject;
    }

    private void observe(FileObject fileObject) {
        URI uri = fileObject.toUri();
        if (uri != null && "file".equalsIgnoreCase(uri.getScheme())) {
            collector.observeSourceOutput(Path.of(uri));
        } else {
            collector.observeSourceOutput(Path.of(fileObject.getName()));
        }
    }
}
