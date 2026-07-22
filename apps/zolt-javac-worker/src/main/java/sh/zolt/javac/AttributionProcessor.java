package sh.zolt.javac;

import java.util.Set;
import javax.annotation.processing.Completion;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/**
 * Wraps a real {@link Processor} so that, when {@code recording} is set, its {@link #init} receives a
 * {@link AttributionProcessingEnvironment} whose Filer records originating elements. When
 * {@code recording} is false the processor is given the raw environment untouched (the belt-and-braces
 * file-manager watch still guards its outputs). Every other call delegates unchanged.
 */
final class AttributionProcessor implements Processor {
    private final Processor delegate;
    private final AttributionCollector collector;
    private final boolean recording;

    AttributionProcessor(Processor delegate, AttributionCollector collector, boolean recording) {
        this.delegate = delegate;
        this.collector = collector;
        this.recording = recording;
    }

    @Override
    public Set<String> getSupportedOptions() {
        return delegate.getSupportedOptions();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return delegate.getSupportedAnnotationTypes();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return delegate.getSupportedSourceVersion();
    }

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        delegate.init(recording
                ? new AttributionProcessingEnvironment(processingEnv, collector)
                : processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return delegate.process(annotations, roundEnv);
    }

    @Override
    public Iterable<? extends Completion> getCompletions(
            Element element,
            AnnotationMirror annotation,
            ExecutableElement member,
            String userText) {
        return delegate.getCompletions(element, annotation, member, userText);
    }
}
