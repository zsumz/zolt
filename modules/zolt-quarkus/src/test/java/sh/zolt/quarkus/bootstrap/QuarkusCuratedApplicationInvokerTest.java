package sh.zolt.quarkus.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.quarkus.QuarkusAugmentationException;
import org.junit.jupiter.api.Test;

final class QuarkusCuratedApplicationInvokerTest {
    private final QuarkusCuratedApplicationInvoker invoker = new QuarkusCuratedApplicationInvoker();

    @Test
    void invokesBootstrapAndValidatesAugmentorBoundary() {
        FakeBootstrap bootstrap = new FakeBootstrap(new FakeCuratedApplication());

        QuarkusCuratedApplicationHandle handle = invoker.invoke(new QuarkusBootstrapHandle(
                bootstrap,
                bootstrap.getClass().getName(),
                "model"));

        assertEquals(FakeCuratedApplication.class.getName(), handle.curatedApplicationClass());
        assertEquals(1, bootstrap.invocations());
    }

    @Test
    void rejectsMissingBootstrapMethod() {
        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> invoker.invoke(new QuarkusBootstrapHandle(
                        new MissingBootstrap(),
                        MissingBootstrap.class.getName(),
                        "model")));

        assertTrue(exception.getMessage().contains("curated application API is incompatible"));
        assertTrue(exception.getMessage().contains("bootstrap"));
    }

    @Test
    void rejectsCuratedApplicationWithoutAugmentorFactory() {
        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> invoker.invoke(new QuarkusBootstrapHandle(
                        new FakeBootstrap(new MissingAugmentorFactory()),
                        FakeBootstrap.class.getName(),
                        "model")));

        assertTrue(exception.getMessage().contains("curated application API is incompatible"));
        assertTrue(exception.getMessage().contains("createAugmentor"));
    }

    @Test
    void reportsBootstrapFailures() {
        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> invoker.invoke(new QuarkusBootstrapHandle(
                        new FailingBootstrap(),
                        FailingBootstrap.class.getName(),
                        "model")));

        assertTrue(exception.getMessage().contains("Quarkus bootstrap failed"));
    }

    public static final class FakeBootstrap {
        private final Object curatedApplication;
        private int invocations;

        FakeBootstrap(Object curatedApplication) {
            this.curatedApplication = curatedApplication;
        }

        public Object bootstrap() {
            invocations++;
            return curatedApplication;
        }

        int invocations() {
            return invocations;
        }
    }

    public static final class FakeCuratedApplication {
        public Object createAugmentor() {
            return new Object();
        }
    }

    public static final class MissingBootstrap {
    }

    public static final class MissingAugmentorFactory {
    }

    public static final class FailingBootstrap {
        public Object bootstrap() {
            throw new IllegalStateException("boom");
        }
    }
}
