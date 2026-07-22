package sh.zolt.javac;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

/**
 * A {@link Filer} that delegates every call to the real compiler Filer but records the path of each
 * created source/class/resource together with the binary names of the top-level types passed as
 * originating elements. This is the only place originating elements are visible, so it is the source
 * of truth for mapping a generated file back to the handwritten type that caused it.
 */
final class AttributionFiler implements Filer {
    private final Filer delegate;
    private final Elements elements;
    private final AttributionCollector collector;

    AttributionFiler(Filer delegate, Elements elements, AttributionCollector collector) {
        this.delegate = delegate;
        this.elements = elements;
        this.collector = collector;
    }

    @Override
    public JavaFileObject createSourceFile(CharSequence name, Element... originatingElements) throws IOException {
        JavaFileObject fileObject = delegate.createSourceFile(name, originatingElements);
        collector.recordSource(pathOf(fileObject), name.toString(), originatingBinaryNames(originatingElements));
        return fileObject;
    }

    @Override
    public JavaFileObject createClassFile(CharSequence name, Element... originatingElements) throws IOException {
        JavaFileObject fileObject = delegate.createClassFile(name, originatingElements);
        collector.recordClass(pathOf(fileObject), name.toString(), originatingBinaryNames(originatingElements));
        return fileObject;
    }

    @Override
    public FileObject createResource(
            JavaFileManager.Location location,
            CharSequence moduleAndPkg,
            CharSequence relativeName,
            Element... originatingElements) throws IOException {
        FileObject fileObject = delegate.createResource(location, moduleAndPkg, relativeName, originatingElements);
        collector.recordResource(pathOf(fileObject), originatingBinaryNames(originatingElements));
        return fileObject;
    }

    @Override
    public FileObject getResource(
            JavaFileManager.Location location,
            CharSequence moduleAndPkg,
            CharSequence relativeName) throws IOException {
        return delegate.getResource(location, moduleAndPkg, relativeName);
    }

    private List<String> originatingBinaryNames(Element... originatingElements) {
        List<String> names = new ArrayList<>();
        if (originatingElements != null) {
            for (Element element : originatingElements) {
                TypeElement topLevel = topLevelType(element);
                if (topLevel != null) {
                    names.add(elements.getBinaryName(topLevel).toString());
                }
            }
        }
        return names;
    }

    private static TypeElement topLevelType(Element element) {
        Element current = element;
        while (current != null) {
            Element enclosing = current.getEnclosingElement();
            if (enclosing == null || enclosing.getKind() == ElementKind.PACKAGE) {
                return current instanceof TypeElement type ? type : null;
            }
            current = enclosing;
        }
        return null;
    }

    private static Path pathOf(FileObject fileObject) {
        URI uri = fileObject.toUri();
        if (uri != null && "file".equalsIgnoreCase(uri.getScheme())) {
            return Path.of(uri).toAbsolutePath().normalize();
        }
        return Path.of(fileObject.getName()).toAbsolutePath().normalize();
    }
}
