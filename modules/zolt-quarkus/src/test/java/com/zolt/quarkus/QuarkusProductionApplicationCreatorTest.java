package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class QuarkusProductionApplicationCreatorTest {
    private final QuarkusProductionApplicationCreator creator = new QuarkusProductionApplicationCreator();

    @Test
    void createsProductionApplicationFromCuratedApplication() {
        FakeCuratedApplication curatedApplication = new FakeCuratedApplication(new FakeAugmentAction());

        QuarkusProductionApplicationHandle handle = creator.create(new QuarkusCuratedApplicationHandle(
                curatedApplication,
                curatedApplication.getClass().getName()));

        assertEquals(FakeAugmentResult.class.getName(), handle.augmentResultClass());
        assertEquals(1, curatedApplication.invocations());
        assertEquals(1, curatedApplication.augmentAction().invocations());
    }

    @Test
    void supportsPublicAugmentActionInterfaceOnHiddenImplementation() {
        HiddenAugmentAction curatedAugmentAction = new HiddenAugmentAction();
        InterfaceCuratedApplication curatedApplication = new InterfaceCuratedApplication(curatedAugmentAction);

        QuarkusProductionApplicationHandle handle = creator.create(new QuarkusCuratedApplicationHandle(
                curatedApplication,
                curatedApplication.getClass().getName()));

        assertEquals(FakeAugmentResult.class.getName(), handle.augmentResultClass());
        assertEquals(1, curatedAugmentAction.invocations());
    }

    @Test
    void rejectsMissingAugmentorFactory() {
        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> creator.create(new QuarkusCuratedApplicationHandle(
                        new MissingAugmentorFactory(),
                        MissingAugmentorFactory.class.getName())));

        assertTrue(exception.getMessage().contains("production application API is incompatible"));
        assertTrue(exception.getMessage().contains("createAugmentor"));
    }

    @Test
    void rejectsMissingProductionApplicationMethod() {
        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> creator.create(new QuarkusCuratedApplicationHandle(
                        new FakeCuratedApplication(new MissingProductionApplicationMethod()),
                        FakeCuratedApplication.class.getName())));

        assertTrue(exception.getMessage().contains("production application API is incompatible"));
        assertTrue(exception.getMessage().contains("createProductionApplication"));
    }

    @Test
    void rejectsNullAugmentor() {
        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> creator.create(new QuarkusCuratedApplicationHandle(
                        new NullAugmentorCuratedApplication(),
                        NullAugmentorCuratedApplication.class.getName())));

        assertTrue(exception.getMessage().contains("returned no augmentor"));
    }

    @Test
    void rejectsNullAugmentResult() {
        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> creator.create(new QuarkusCuratedApplicationHandle(
                        new FakeCuratedApplication(new NullProductionApplication()),
                        FakeCuratedApplication.class.getName())));

        assertTrue(exception.getMessage().contains("returned no augment result"));
    }

    @Test
    void reportsProductionApplicationFailures() {
        RuntimeException cause = new RuntimeException("boom");
        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> creator.create(new QuarkusCuratedApplicationHandle(
                        new FakeCuratedApplication(new FailingAugmentAction(cause)),
                        FakeCuratedApplication.class.getName())));

        assertTrue(exception.getMessage().contains("production application creation failed"));
        assertSame(cause, exception.getCause());
    }

    public interface AugmentActionApi {
        Object createProductionApplication();
    }

    public static final class FakeCuratedApplication {
        private final Object augmentAction;
        private int invocations;

        FakeCuratedApplication(Object augmentAction) {
            this.augmentAction = augmentAction;
        }

        public Object createAugmentor() {
            invocations++;
            return augmentAction;
        }

        FakeAugmentAction augmentAction() {
            return (FakeAugmentAction) augmentAction;
        }

        int invocations() {
            return invocations;
        }
    }

    public static final class InterfaceCuratedApplication {
        private final AugmentActionApi augmentAction;

        InterfaceCuratedApplication(AugmentActionApi augmentAction) {
            this.augmentAction = augmentAction;
        }

        public AugmentActionApi createAugmentor() {
            return augmentAction;
        }
    }

    public static final class FakeAugmentAction {
        private int invocations;

        public Object createProductionApplication() {
            invocations++;
            return new FakeAugmentResult();
        }

        int invocations() {
            return invocations;
        }
    }

    static final class HiddenAugmentAction implements AugmentActionApi {
        private int invocations;

        @Override
        public Object createProductionApplication() {
            invocations++;
            return new FakeAugmentResult();
        }

        int invocations() {
            return invocations;
        }
    }

    public static final class FailingAugmentAction {
        private final RuntimeException cause;

        FailingAugmentAction(RuntimeException cause) {
            this.cause = cause;
        }

        public Object createProductionApplication() {
            throw cause;
        }
    }

    public static final class NullProductionApplication {
        public Object createProductionApplication() {
            return null;
        }
    }

    public static final class MissingAugmentorFactory {
    }

    public static final class MissingProductionApplicationMethod {
    }

    public static final class NullAugmentorCuratedApplication {
        public Object createAugmentor() {
            return null;
        }
    }

    public static final class FakeAugmentResult {
    }
}
