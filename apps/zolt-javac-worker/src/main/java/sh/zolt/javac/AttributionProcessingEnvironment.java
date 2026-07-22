package sh.zolt.javac;

import java.util.Locale;
import java.util.Map;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * A {@link ProcessingEnvironment} that is identical to the real one except that {@link #getFiler()}
 * returns the recording {@link AttributionFiler}. It is a plain delegating implementation rather than
 * a JDK proxy so well-behaved isolating processors that only touch the JSR-269 interface see no
 * difference. Processors that reflect into javac internals (e.g. Lombok) are never given this
 * environment; see {@link AttributingCompiler}.
 */
final class AttributionProcessingEnvironment implements ProcessingEnvironment {
    private final ProcessingEnvironment delegate;
    private final Filer filer;

    AttributionProcessingEnvironment(ProcessingEnvironment delegate, AttributionCollector collector) {
        this.delegate = delegate;
        this.filer = new AttributionFiler(delegate.getFiler(), delegate.getElementUtils(), collector);
    }

    @Override
    public Map<String, String> getOptions() {
        return delegate.getOptions();
    }

    @Override
    public Messager getMessager() {
        return delegate.getMessager();
    }

    @Override
    public Filer getFiler() {
        return filer;
    }

    @Override
    public Elements getElementUtils() {
        return delegate.getElementUtils();
    }

    @Override
    public Types getTypeUtils() {
        return delegate.getTypeUtils();
    }

    @Override
    public SourceVersion getSourceVersion() {
        return delegate.getSourceVersion();
    }

    @Override
    public Locale getLocale() {
        return delegate.getLocale();
    }

    @Override
    public boolean isPreviewEnabled() {
        return delegate.isPreviewEnabled();
    }
}
